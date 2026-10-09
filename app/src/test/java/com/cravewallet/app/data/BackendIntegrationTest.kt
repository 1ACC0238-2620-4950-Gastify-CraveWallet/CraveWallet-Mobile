package com.cravewallet.app.data

import android.app.Application
import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], application=Application::class)
class BackendIntegrationTest {
    private lateinit var context: Context
    private lateinit var server: MockWebServer
    private lateinit var api: BackendApi
    private fun auth(access:String="access-1",refresh:String="refresh-1") = """{"accessToken":"$access","refreshToken":"$refresh","user":{"id":"demo","email":"demo@example.com","referenceCurrency":"PEN"}}"""
    private fun response(body:String, status:Int=200) = MockResponse().setResponseCode(status).setHeader("Content-Type","application/json").setBody(body)
    @Before fun setup() {
        context=RuntimeEnvironment.getApplication()
        context.getSharedPreferences("backend_session",0).edit().clear().commit()
        context.getSharedPreferences("cravewallet",0).edit().clear().commit()
        server=MockWebServer();server.start()
        api=BackendApi(context);api.configure(server.url("/").toString())
    }
    @After fun close() {server.shutdown()}

    @Test fun registrationPersistsOnlySessionAndLogoutClearsIt() = runBlocking {
        server.enqueue(response(auth(),201));api.authenticate("demo@example.com","Example123",true)
        val registered=server.takeRequest()
        assertEquals("/api/v1/auth/register",registered.path)
        assertNull(registered.getHeader("Authorization"))
        assertEquals("Example123",JSONObject(registered.body.readUtf8()).getString("password"))
        assertTrue(api.hasSession)
        assertEquals("demo@example.com",BackendApi(context).email)
        assertFalse(context.getSharedPreferences("backend_session",0).all.containsValue("Example123"))
        server.enqueue(response("",204));api.logout()
        assertEquals("Bearer access-1",server.takeRequest().getHeader("Authorization"));assertFalse(api.hasSession)
    }

    @Test fun unauthorizedRequestRefreshesAndRetriesWithTheNewToken() = runBlocking {
        server.enqueue(response(auth()));api.authenticate("demo@example.com","Example123",false);server.takeRequest()
        server.enqueue(response("{}",401));server.enqueue(response(auth("access-2","refresh-2")));server.enqueue(response("""{"email":"demo@example.com"}"""))
        assertEquals("demo@example.com",api.request("GET","/api/v1/users/me").getString("email"))
        assertEquals("Bearer access-1",server.takeRequest().getHeader("Authorization"))
        val refresh=server.takeRequest();assertEquals("/api/v1/auth/refresh",refresh.path)
        assertEquals("refresh-1",JSONObject(refresh.body.readUtf8()).getString("refreshToken"))
        assertEquals("Bearer access-2",server.takeRequest().getHeader("Authorization"))
    }

    @Test fun concurrentUnauthorizedRequestsConsumeOneRefreshToken() = runBlocking {
        server.enqueue(response(auth()));api.authenticate("demo@example.com","Example123",false);server.takeRequest()
        val refreshed=AtomicInteger()
        server.dispatcher=object:Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse = when {
                request.path=="/api/v1/auth/refresh" -> {refreshed.incrementAndGet();response(auth("access-2","refresh-2"))}
                request.getHeader("Authorization")=="Bearer access-1" -> response("{}",401)
                else -> response("""{"ok":true}""")
            }
        }
        val results=(1..4).map {async {api.request("GET","/api/v1/users/me")}}.awaitAll()
        assertTrue(results.all {it.getBoolean("ok")});assertEquals(1,refreshed.get())
    }

    @Test fun invalidRefreshClearsTheSession() = runBlocking {
        server.enqueue(response(auth()));api.authenticate("demo@example.com","Example123",false)
        server.enqueue(response("{}",401));server.enqueue(response("{}",401))
        try {api.request("GET","/api/v1/users/me");fail("Expected 401")} catch(e:ApiException) {assertEquals(401,e.status)}
        assertFalse(api.hasSession)
    }

    @Test fun patchUsesJsonAndConflictsDoNotLookLikeSuccessfulWrites() = runBlocking {
        server.enqueue(response(auth()));api.authenticate("demo@example.com","Example123",false);server.takeRequest()
        server.enqueue(response("""{"amount":19.90}"""))
        assertEquals(19.9,api.request("PATCH","/api/v1/subscriptions/123",JSONObject().put("amount",19.9)).getDouble("amount"),0.001)
        val patch=server.takeRequest();assertEquals("PATCH",patch.method);assertEquals(19.9,JSONObject(patch.body.readUtf8()).getDouble("amount"),0.001)
        server.enqueue(response("""{"detail":"Límite de suscripciones alcanzado"}""",409))
        try {api.request("POST","/api/v1/subscriptions",JSONObject());fail("Expected conflict")} catch(e:ApiException) {assertEquals(409,e.status)}
    }

    @Test fun remotePortfolioNeverSeedsDemoOrInventsPastPayments() = runBlocking {
        server.dispatcher=object:Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse = when {
                request.path=="/api/v1/auth/login" -> response(auth())
                request.path=="/api/v1/users/me" -> response("""{"email":"demo@example.com"}""")
                request.path!!.contains("status=ACTIVE") -> response("""{"items":[{"id":"remote-id","name":"Cuenta real","amount":12,"currency":"PEN","category":"MUSICA","billingCycle":"MONTHLY","nextBillingDate":"2026-01-01","status":"ACTIVE"}],"monthlyTotalPen":12,"conversionAvailable":true,"exchangeRate":null}""")
                request.path!!.contains("status=CANCELLED") -> response("""{"items":[]}""")
                else -> response("""{"rate":3.5,"updatedAt":"2026-10-08T12:00:00Z","attributionUrl":"https://www.exchangerate-api.com"}""")
            }
        }
        val repo=Repository(context);repo.authenticate("demo@example.com","Example123",false)
        assertTrue(repo.current.backendMode);assertFalse(repo.current.profile.premium)
        assertEquals(listOf("remote-id"),repo.current.subscriptions.map {it.id})
        assertEquals(LocalDate.of(2026,1,1),repo.current.subscriptions.single().nextCharge)
        assertTrue(repo.current.subscriptions.single().payments.isEmpty())
        assertEquals(12.0,repo.current.monthlyTotal,0.001)
    }

    @Test fun unavailableConversionPreservesOriginalAmountsAndCachesNoInventedRate() = runBlocking {
        server.dispatcher=object:Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse = when {
                request.path=="/api/v1/auth/login" -> response(auth())
                request.path=="/api/v1/users/me" -> response("""{"email":"demo@example.com"}""")
                request.path!!.contains("status=ACTIVE") -> response("""{"items":[{"id":"usd-id","name":"Servicio USD","amount":9.99,"currency":"USD","category":"MUSICA","billingCycle":"MONTHLY","nextBillingDate":"2026-11-01","status":"ACTIVE"}],"monthlyTotalPen":null,"conversionAvailable":false,"exchangeRate":null}""")
                request.path!!.contains("status=CANCELLED") -> response("""{"items":[]}""")
                else -> response("{}",503)
            }
        }
        val repo=Repository(context);repo.authenticate("demo@example.com","Example123",false)
        assertFalse(repo.current.conversionAvailable)
        assertTrue(repo.current.monthlyTotal.isNaN())
        assertEquals(9.99,repo.current.subscriptions.single().amount,0.001)
        val restarted=Repository(context)
        assertTrue(restarted.current.rates.usd.isNaN())
        assertEquals("usd-id",restarted.current.subscriptions.single().id)
    }

    /** Optional real-server test: run with CRAVE_LIVE_BACKEND=http://127.0.0.1:8080. */
    @Test fun realBackendRoundTrip() = runBlocking {
        val url=System.getenv("CRAVE_LIVE_BACKEND")
        Assume.assumeTrue(!url.isNullOrBlank())
        val repo=Repository(context);repo.api.configure(url!!)
        val email="mobile-${UUID.randomUUID()}@example.com"
        repo.authenticate(email,"Integration2026",true)
        val date=LocalDate.now(ZoneId.of("America/Lima")).plusMonths(1)
        val created=repo.saveRemote(Subscription(UUID.randomUUID().toString(),"Música móvil",9.99,Currency.USD,Frequency.MENSUAL,date,Category.MUSICA))
        repo.upsert(created);repo.refresh()
        assertEquals(created.id,repo.current.subscriptions.single().id)
        val updated=repo.saveRemote(created.copy(amount=11.99));repo.upsert(updated);repo.refresh()
        assertEquals(11.99,repo.current.subscriptions.single().amount,0.001)
        val reminder=repo.api.request("GET","/api/v1/subscriptions/${created.id}/reminder")
        assertEquals(86400L,java.time.Duration.between(java.time.Instant.parse(reminder.getString("reminderAt")),java.time.Instant.parse(reminder.getString("billingAt"))).seconds)
        val today=LocalDate.now(ZoneId.of("America/Lima"))
        repo.api.request("PUT","/api/v1/delivery-expenses/budget",JSONObject().put("year",today.year).put("month",today.monthValue).put("spendingLimit",100))
        val expense=JSONObject().put("requestId",UUID.randomUUID().toString()).put("merchant","Comercio móvil").put("amount",35.5).put("category","Comida").put("expenseDate",today.toString())
        repo.api.request("POST","/api/v1/delivery-expenses",expense);repo.api.request("POST","/api/v1/delivery-expenses",expense)
        val summary=repo.api.request("GET","/api/v1/delivery-expenses/summary?year=${today.year}&month=${today.monthValue}")
        assertEquals(35.5,summary.getDouble("total"),0.001);assertEquals(64.5,summary.getDouble("remaining"),0.001)
        context.getSharedPreferences("backend_session",0).edit().putString("access","expired").commit()
        assertEquals(email,repo.api.request("GET","/api/v1/users/me").getString("email"))
        repo.cancelRemote(created.id);repo.refresh();assertTrue(repo.current.subscriptions.single().cancelled)
        repo.logout();assertFalse(repo.api.hasSession);assertEquals(AccessMode.SIGNED_OUT,repo.access.value)
    }
}
