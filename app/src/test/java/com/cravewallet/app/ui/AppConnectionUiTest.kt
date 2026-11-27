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
            compose.onNodeWithText("Email address").performTextInput("ui@example.com")
            compose.onNodeWithText("Password").performTextInput("Example2026")
            compose.onNodeWithText("Test server").performTextReplacement(server.url("/").toString())
            compose.onAllNodesWithText("Sign in").onLast().performClick()
            compose.waitUntil(20000) {compose.onAllNodesWithText("Profile").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("Profile").performClick()
            compose.onAllNodesWithText("ui@example.com").onFirst().assertExists()
            compose.onNodeWithText("Sign out").performScrollTo().performClick()
            compose.waitUntil(20000) {compose.onAllNodesWithText("Sign in").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("Email address").assertExists()
            Assert.assertTrue(server.requestCount>=6)
        } finally {server.shutdown()}
    }

    @Test fun registrationRequiresAcceptingTermsAndConditions() {
        compose.onNodeWithText("Create an account").performScrollTo().performClick()
        compose.onNodeWithText("Email address").performTextInput("new@example.com")
        compose.onNodeWithText("Password").performTextInput("Example2026")
        compose.onNodeWithText("Create account").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Terms and Conditions").performScrollTo().performClick()
        compose.onNodeWithText("Terms and Conditions of Use").assertExists()
        compose.onNodeWithText("Accept Terms").performClick()
        compose.onNodeWithText("Create account").performScrollTo().assertIsEnabled()
    }
}
