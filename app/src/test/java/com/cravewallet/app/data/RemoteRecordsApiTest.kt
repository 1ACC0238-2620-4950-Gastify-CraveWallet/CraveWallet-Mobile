package com.cravewallet.app.data

import android.app.Application
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RemoteRecordsApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: RemoteRecordsApi

    @Before fun setup() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("backend_session", 0).edit().clear().commit()
        server = MockWebServer()
        server.start()
        val transport = BackendApi(context)
        transport.configure(server.url("/").toString())
        server.enqueue(json("""{"accessToken":"records-token","refreshToken":"refresh","user":{"email":"records@example.com"}}"""))
        transport.authenticate("records@example.com", "Test12345", false)
        server.takeRequest()
        api = RemoteRecordsApi(transport)
    }

    @After fun close() { server.shutdown() }

    @Test fun listCorrectAndDeleteUseTheBackendContract() = runBlocking {
        server.enqueue(json("""[{"id":"expense-1","merchant":"Shop","amount":12.50,"category":"Food","expenseDate":"2019-11-03"}]"""))
        val record = api.deliveryExpenses(2019, 11).single()
        assertEquals(0, BigDecimal("12.50").compareTo(record.amount))
        assertEquals(LocalDate.of(2019, 11, 3), record.date)
        val listing = server.takeRequest()
        assertEquals("GET", listing.method)
        assertEquals("/api/v1/delivery-expenses?year=2019&month=11", listing.path)
        assertEquals("Bearer records-token", listing.getHeader("Authorization"))

        server.enqueue(json("""{"id":"expense-1","merchant":"New shop","amount":20.25,"category":"Food","expenseDate":"2019-12-03"}"""))
        val corrected = api.correctDeliveryExpense(record.copy(merchant = "New shop", amount = BigDecimal("20.25"), date = LocalDate.of(2019, 12, 3)))
        assertEquals(BigDecimal("20.25"), corrected.amount)
        val edit = server.takeRequest()
        assertEquals("PUT", edit.method)
        assertEquals("/api/v1/delivery-expenses/expense-1", edit.path)
        val body = JSONObject(edit.body.readUtf8())
        assertEquals("2019-12-03", body.getString("expenseDate"))
        assertEquals(BigDecimal("20.25"), BigDecimal(body.getString("amount")))

        server.enqueue(MockResponse().setResponseCode(204))
        api.deleteDeliveryExpense(record.id)
        val delete = server.takeRequest()
        assertEquals("DELETE", delete.method)
        assertEquals("/api/v1/delivery-expenses/expense-1", delete.path)
        assertEquals(0L, delete.bodySize)
    }

    @Test fun emptyDeliveryListIsSupported() = runBlocking {
        server.enqueue(json("[]"))
        assertTrue(api.deliveryExpenses(2026, 11).isEmpty())
    }

    @Test fun historyParsesDecimalValuesAndNullableVariation() = runBlocking {
        server.enqueue(json("""{"cycles":[{"billingDate":"2026-10-03","rate":3.71234567,"amountPen":37.12}],"variation":{"percentChange":-2.70,"direction":"DOWN"}}"""))
        val history = api.exchangeRateHistory("sub-1")
        assertEquals("/api/v1/subscriptions/sub-1/exchange-rate-history", server.takeRequest().path)
        assertEquals(RateDirection.DOWN, history.variation!!.direction)
        assertEquals(BigDecimal("3.71234567"), history.cycles.single().rate)
        server.enqueue(json("""{"cycles":[],"variation":null}"""))
        val empty = api.exchangeRateHistory("sub-2")
        assertTrue(empty.cycles.isEmpty())
        assertNull(empty.variation)
    }

    @Test fun forbiddenOwnerRecordDoesNotBecomeAnEmptyHistory() = runBlocking {
        server.enqueue(json("""{"detail":"Not found"}""", 404))
        try {
            api.exchangeRateHistory("other-owner-record")
            fail("Expected API error")
        } catch (error: ApiException) {
            assertEquals(404, error.status)
        }
    }

    private fun json(body: String, status: Int = 200) = MockResponse().setResponseCode(status)
        .setHeader("Content-Type", "application/json").setBody(body)
}
