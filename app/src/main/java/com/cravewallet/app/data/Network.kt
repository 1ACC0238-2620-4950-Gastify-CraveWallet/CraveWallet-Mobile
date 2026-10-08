package com.cravewallet.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime

object Connectivity {

    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Descarga el tipo de cambio del día (USD→PEN y EUR→PEN) desde un servicio público sin clave.
     * Devuelve null si no hay conexión o el servicio no responde: la app sigue con el último valor guardado.
     */
    suspend fun fetchRates(): ExchangeRates? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL("https://open.er-api.com/v6/latest/USD").openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
            }
            try {
                if (conn.responseCode != 200) return@runCatching null
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val rates = json.getJSONObject("rates")
                val pen = rates.getDouble("PEN")
                val eur = rates.getDouble("EUR")
                ExchangeRates(usd = pen, eur = pen / eur, updatedAt = LocalDateTime.now())
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }
}

/** Estado de conexión observable desde Compose. */
@Composable
fun rememberIsOnline(): State<Boolean> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(Connectivity.isOnline(context)) }
    DisposableEffect(context) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { state.value = Connectivity.isOnline(context) }
            override fun onLost(network: Network) { state.value = Connectivity.isOnline(context) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                state.value = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }
        }
        runCatching { cm?.registerDefaultNetworkCallback(callback) }
        onDispose { runCatching { cm?.unregisterNetworkCallback(callback) } }
    }
    return state
}
