package com.cravewallet.app.data

import android.content.Context
import com.cravewallet.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.util.concurrent.TimeUnit
import java.net.URL

class ApiException(val status: Int, message: String) : Exception(message)

/** REST transport. Refresh is serialized because each refresh token is single-use. */
class BackendApi(context: Context) {
    private val prefs = context.getSharedPreferences("backend_session", Context.MODE_PRIVATE)
    private val refreshLock = Mutex()
    private val client = OkHttpClient.Builder().connectTimeout(5,TimeUnit.SECONDS)
        .readTimeout(10,TimeUnit.SECONDS).callTimeout(20,TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()
    val hasSession get() = prefs.getString("access", null) != null
    val baseUrl get() = prefs.getString("url", null) ?: BuildConfig.API_BASE_URL
    val email get() = prefs.getString("email", "").orEmpty()

    fun configure(url: String) {
        val parsed = URL(url.trim())
        require(parsed.protocol == "https" || (BuildConfig.DEBUG && parsed.protocol == "http")) {
            "Utiliza HTTPS para conectarte al servidor."
        }
        require(parsed.userInfo == null && parsed.query == null && parsed.ref == null) { "La URL no debe contener credenciales ni parámetros." }
        require(parsed.path.isEmpty() || parsed.path == "/") { "Escribe solo la dirección y el puerto del servidor." }
        prefs.edit().putString("url", url.trim().trimEnd('/')).commit()
    }

    private fun saveSession(body: JSONObject) {
        prefs.edit().putString("access", body.getString("accessToken"))
            .putString("refresh", body.getString("refreshToken"))
            .putString("email", body.getJSONObject("user").getString("email")).commit()
    }

    fun clearSession() { prefs.edit().remove("access").remove("refresh").remove("email").commit() }

    suspend fun authenticate(email: String, password: String, register: Boolean): JSONObject = withContext(Dispatchers.IO) {
        val result = raw("POST", if (register) "/api/v1/auth/register" else "/api/v1/auth/login",
            JSONObject().put("email", email).put("password", password), null)
        saveSession(result)
        result.getJSONObject("user")
    }

    suspend fun request(method: String, path: String, body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        val token = prefs.getString("access", null) ?: throw ApiException(401, "Inicia sesión para continuar.")
        try { raw(method, path, body, token) }
        catch (e: ApiException) {
            if (e.status != 401) throw e
            refreshLock.withLock {
                if (prefs.getString("access", null) == token) {
                    try {
                        val refresh = prefs.getString("refresh", null) ?: throw e
                        saveSession(raw("POST", "/api/v1/auth/refresh", JSONObject().put("refreshToken", refresh), null))
                    } catch (failure: ApiException) {
                        if (failure.status == 401) clearSession()
                        throw failure
                    }
                }
            }
            try {raw(method, path, body, prefs.getString("access", null) ?: throw e)} catch(retry:ApiException) {if(retry.status==401) clearSession();throw retry}
        }
    }

    suspend fun logout() { request("POST", "/api/v1/auth/logout"); clearSession() }

    private fun raw(method: String, path: String, body: JSONObject?, token: String?): JSONObject {
        val requestBody = body?.toString()?.toRequestBody("application/json; charset=utf-8".toMediaType())
            ?: if(method in setOf("POST","PUT","PATCH")) "".toRequestBody(null) else null
        val builder = Request.Builder().url(baseUrl + path).header("Accept","application/json").method(method,requestBody)
        token?.let { builder.header("Authorization","Bearer $it") }
        client.newCall(builder.build()).execute().use { response ->
            val status = response.code
            val text = response.body?.string().orEmpty()
            if (status !in 200..299) {
                val message = when (status) {
                    401 -> "La sesión o las credenciales no son válidas."
                    404 -> "No se encontró el registro en tu cuenta."
                    503 -> "El tipo de cambio no está disponible. Intenta actualizarlo más tarde."
                    else -> runCatching { JSONObject(text).optString("detail") }.getOrNull()
                        ?.takeIf { it.isNotBlank() } ?: "No se pudo completar la operación ($status)."
                }
                throw ApiException(status, message)
            }
            return if (text.isBlank()) JSONObject() else JSONObject(text)
        }
    }
}
