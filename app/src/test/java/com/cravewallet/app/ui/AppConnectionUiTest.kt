package com.cravewallet.app.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.cravewallet.app.CraveApplication
import com.cravewallet.app.MainActivity
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=CraveApplication::class)
@LooperMode(LooperMode.Mode.PAUSED)
class AppConnectionUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()

    @Test fun loginAndLogoutUseTheServerRatherThanTheDemoProfile() {
        val server=MockWebServer()
        server.dispatcher=object:Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                val body=when {
                    request.path=="/api/v1/auth/login" -> """{"accessToken":"ui-access","refreshToken":"ui-refresh","user":{"email":"ui@example.com"}}"""
                    request.path=="/api/v1/users/me" -> """{"email":"ui@example.com"}"""
                    request.path!!.contains("status=ACTIVE") -> """{"items":[],"monthlyTotalPen":0,"conversionAvailable":true,"exchangeRate":null}"""
                    request.path!!.contains("status=CANCELLED") -> """{"items":[]}"""
                    request.path!!.contains("exchange-rate") -> """{"rate":3.5,"updatedAt":"2026-10-08T12:00:00Z","attributionUrl":"https://www.exchangerate-api.com"}"""
                    request.path=="/api/v1/auth/logout" -> return MockResponse().setResponseCode(204)
                    else -> return MockResponse().setResponseCode(404)
                }
                return MockResponse().setResponseCode(200).setHeader("Content-Type","application/json").setBody(body)
            }
        }
        server.start()
        try {
            compose.onNodeWithText("Correo electrónico").performTextInput("ui@example.com")
            compose.onNodeWithText("Contraseña").performTextInput("Example2026")
            compose.onNodeWithText("Servidor de pruebas").performTextReplacement(server.url("/").toString())
            compose.onNodeWithText("Ingresar").performClick()
            compose.waitUntil(20000) {compose.onAllNodesWithText("Perfil").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("Perfil").performClick()
            compose.onAllNodesWithText("ui@example.com").onFirst().assertExists()
            compose.onNodeWithText("Cerrar sesión").performScrollTo().performClick()
            compose.waitUntil(20000) {compose.onAllNodesWithText("Ingresar").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("Correo electrónico").assertExists()
            Assert.assertTrue(server.requestCount>=6)
        } finally {server.shutdown()}
    }
}
