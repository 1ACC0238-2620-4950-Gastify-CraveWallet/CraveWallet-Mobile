package com.cravewallet.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/** La demostración y la sesión conectada mantienen fuentes de datos distintas. */
enum class AccessMode { SIGNED_OUT, CONNECTED, DEMO }

/** El backend conserva suscripciones y totales; el teléfono conserva notas y preferencias por cuenta. */
class Repository(context: Context) {
    val api = BackendApi(context)
    private val _access = MutableStateFlow(if (api.hasSession) AccessMode.CONNECTED else AccessMode.SIGNED_OUT)
    val access = _access.asStateFlow()
    val connected get() = _access.value == AccessMode.CONNECTED
    fun demo() { _access.value = AccessMode.DEMO; _state.value = load() }
    fun signedOut() { _access.value = AccessMode.SIGNED_OUT; _state.value = emptyRemote() }
    fun checkSession() { if (connected && !api.hasSession) signedOut() }
    private fun emptyRemote() = SeedData.emptyState().copy(
        profile = Profile(api.email.substringBefore('@').ifBlank { "Mi cuenta" }, api.email, false),
        reminders = ReminderSettings(calendarEnabled = false, pushEnabled = false),
        subscriptions = emptyList(), backendMode = true, conversionAvailable = false,
        rates = ExchangeRates(usd = Double.NaN, eur = Double.NaN),
    )

    suspend fun authenticate(email: String, password: String, register: Boolean) {
        api.authenticate(email, password, register)
        _access.value = AccessMode.CONNECTED
        _state.value = load()
        refresh()
    }

    suspend fun refresh() {
        if (!connected) return
        val profile = api.request("GET", "/api/v1/users/me")
        val active = api.request("GET", "/api/v1/subscriptions?status=ACTIVE")
        val cancelled = api.request("GET", "/api/v1/subscriptions?status=CANCELLED")
        val quote = if (!active.isNull("exchangeRate")) active.optJSONObject("exchangeRate") else {
            try { api.request("GET", "/api/v1/exchange-rate?from=USD&to=PEN") }
            catch(e: ApiException) { if (e.status == 503) null else throw e }
        }
        val local = current.subscriptions.associateBy { it.id }
        val items = listOf(active, cancelled).flatMap { body ->
            val a = body.getJSONArray("items")
            (0 until a.length()).map { fromRemote(a.getJSONObject(it), local) }
        }
        val rates = if (quote == null) ExchangeRates(usd=Double.NaN, eur=Double.NaN) else ExchangeRates(
            usd=quote.getDouble("rate"), eur=Double.NaN,
            updatedAt=java.time.Instant.parse(quote.getString("updatedAt")).atZone(java.time.ZoneId.of("America/Lima")).toLocalDateTime())
        commit { it.copy(profile=Profile(it.profile.name, profile.getString("email"), false),
            subscriptions=items, rates=rates, backendMode=true,
            conversionAvailable=active.getBoolean("conversionAvailable"),
            serverMonthlyTotal=if(active.isNull("monthlyTotalPen")) null else active.getDouble("monthlyTotalPen"),
            rateAttribution=quote?.optString("attributionUrl"),rateStale=quote?.optBoolean("stale",false) ?: false) }
    }

    private fun fromRemote(o: JSONObject, local: Map<String, Subscription> = emptyMap()): Subscription {
        val id=o.getString("id")
        val previous=local[id]
        return Subscription(id=id, name=o.getString("name"), amount=o.getDouble("amount"),
            currency=Currency.valueOf(o.getString("currency")),
            frequency=if(o.getString("billingCycle")=="ANNUAL") Frequency.ANUAL else Frequency.MENSUAL,
            nextCharge=LocalDate.parse(o.getString("nextBillingDate")),
            category=Category.entries.firstOrNull { it.name.equals(o.getString("category"),true) || it.label.equals(o.getString("category"),true) } ?: Category.OTROS,
            cancelled=o.getString("status")=="CANCELLED", notes=previous?.notes.orEmpty(),
            unusedSince=previous?.unusedSince, startDate=previous?.startDate ?: LocalDate.parse(o.getString("nextBillingDate")),
            payments=emptyList(), calendarEventId=previous?.calendarEventId, leadTime=previous?.leadTime,
            createdAt=previous?.createdAt ?: LocalDateTime.now())
    }

    suspend fun saveRemote(sub: Subscription): Subscription {
        val previous=subscription(sub.id)
        require(sub.currency!=Currency.EUR && sub.frequency!=Frequency.TRIMESTRAL) { "El servidor admite PEN/USD y frecuencia mensual/anual." }
        val data=JSONObject().put("amount", java.math.BigDecimal.valueOf(sub.amount))
            .put("category",sub.category.name).put("nextBillingDate",sub.nextCharge.toString())
        if(previous!=null) {
            require(previous.name==sub.name && previous.currency==sub.currency && previous.frequency==sub.frequency) {
                "Solo puedes editar monto, categoría y próxima fecha de cobro."
            }
        } else data.put("name",sub.name).put("currency",sub.currency.name)
            .put("billingCycle",if(sub.frequency==Frequency.ANUAL) "ANNUAL" else "MONTHLY")
        val body=api.request(if(previous==null) "POST" else "PATCH",
            if(previous==null) "/api/v1/subscriptions" else "/api/v1/subscriptions/${sub.id}",data)
        return fromRemote(body,current.subscriptions.associateBy {it.id}).copy(notes=sub.notes,unusedSince=sub.unusedSince,leadTime=sub.leadTime)
    }

    suspend fun cancelRemote(id: String) {
        val result=api.request("POST", "/api/v1/subscriptions/$id/cancel")
        upsert(fromRemote(result,current.subscriptions.associateBy { it.id }).copy(calendarEventId=null))
        commit { it.copy(serverMonthlyTotal=null) }
    }

    suspend fun logout() { if(connected) api.logout(); signedOut() }


    private val prefs = context.getSharedPreferences("cravewallet", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppState> = _state.asStateFlow()

    val current: AppState get() = _state.value

    private fun load(): AppState {
        val raw = prefs.getString(KEY_STATE, null)
        if (connected) {
            val cache=prefs.getString("account_${api.email}",null)?.let { runCatching {decode(JSONObject(it))}.getOrNull() }
            return emptyRemote().copy(reminders=cache?.reminders ?: ReminderSettings(calendarEnabled=false,pushEnabled=false), subscriptions=cache?.subscriptions.orEmpty())
        }
        val loaded = raw?.let { runCatching { decode(JSONObject(it)) }.getOrNull() } ?: SeedData.state()
        return rollForward(loaded)
    }

    /** Si una fecha de cobro ya pasó, registra el pago y avanza al siguiente periodo. */
    private fun rollForward(state: AppState, today: LocalDate = LocalDate.now()): AppState {
        val subs = state.subscriptions.map { s ->
            if (s.cancelled || !s.nextCharge.isBefore(today)) return@map s
            var next = s.nextCharge
            val newPayments = mutableListOf<Payment>()
            while (next.isBefore(today)) {
                newPayments += Payment(next, s.amount, s.currency, state.rates.rate(s.currency))
                next = s.frequency.next(next)
            }
            s.copy(nextCharge = next, payments = (newPayments + s.payments).sortedByDescending { it.date }, calendarEventId = null)
        }
        return state.copy(subscriptions = subs)
    }

    private fun commit(transform: (AppState) -> AppState) {
        _state.update(transform)
        prefs.edit().putString(if(connected) "account_${api.email}" else KEY_STATE, encode(_state.value).toString()).apply()
    }

    fun markTotalsPending() = commit { it.copy(serverMonthlyTotal=null) }

    fun subscription(id: String): Subscription? = current.subscriptions.firstOrNull { it.id == id }

    fun newId(): String = UUID.randomUUID().toString()

    fun upsert(sub: Subscription) = commit { s ->
        val exists = s.subscriptions.any { it.id == sub.id }
        s.copy(subscriptions = if (exists) s.subscriptions.map { if (it.id == sub.id) sub else it } else s.subscriptions + sub)
    }

    fun remove(id: String) = commit { s -> s.copy(subscriptions = s.subscriptions.filterNot { it.id == id }) }

    fun update(id: String, transform: (Subscription) -> Subscription) = commit { s ->
        s.copy(subscriptions = s.subscriptions.map { if (it.id == id) transform(it) else it })
    }

    fun setUnused(id: String, unused: Boolean) = update(id) { it.copy(unusedSince = if (unused) LocalDate.now() else null) }

    fun setCancelled(id: String, cancelled: Boolean) = update(id) { it.copy(cancelled = cancelled) }

    fun setReminders(settings: ReminderSettings) = commit { it.copy(reminders = settings) }

    fun setProfile(profile: Profile) = commit { it.copy(profile = profile) }

    fun setPremium(premium: Boolean) = commit { it.copy(profile = it.profile.copy(premium = premium)) }

    fun setRates(rates: ExchangeRates) = commit { it.copy(rates = rates) }

    fun resetDemo() = commit { SeedData.state() }

    fun startEmpty() = commit { SeedData.emptyState() }

    // region JSON

    private fun encode(s: AppState): JSONObject = JSONObject().apply {
        put("profile", JSONObject().apply {
            put("name", s.profile.name); put("email", s.profile.email); put("premium", s.profile.premium)
        })
        put("reminders", JSONObject().apply {
            put("calendar", s.reminders.calendarEnabled); put("push", s.reminders.pushEnabled)
            put("lead", s.reminders.leadTime.name); put("hour", s.reminders.hour); put("minute", s.reminders.minute)
        })
        put("rates", JSONObject().apply {
            put("usd", if(s.rates.usd.isFinite()) s.rates.usd else JSONObject.NULL); put("eur", if(s.rates.eur.isFinite()) s.rates.eur else JSONObject.NULL); put("updatedAt", s.rates.updatedAt.toString())
        })
        put("subs", JSONArray().apply { s.subscriptions.forEach { put(encodeSub(it)) } })
    }

    private fun encodeSub(s: Subscription) = JSONObject().apply {
        put("id", s.id); put("name", s.name); put("amount", s.amount); put("currency", s.currency.name)
        put("frequency", s.frequency.name); put("next", s.nextCharge.toString()); put("category", s.category.name)
        put("notes", s.notes); put("unusedSince", s.unusedSince?.toString() ?: JSONObject.NULL)
        put("cancelled", s.cancelled); put("start", s.startDate.toString())
        put("eventId", s.calendarEventId ?: JSONObject.NULL); put("lead", s.leadTime?.name ?: JSONObject.NULL)
        put("createdAt", s.createdAt.toString())
        put("payments", JSONArray().apply {
            s.payments.forEach { p ->
                put(JSONObject().apply {
                    put("date", p.date.toString()); put("amount", p.amount)
                    put("currency", p.currency.name); put("rate", p.rate)
                })
            }
        })
    }

    private fun decode(o: JSONObject): AppState {
        val p = o.getJSONObject("profile")
        val r = o.getJSONObject("reminders")
        val x = o.getJSONObject("rates")
        val subs = o.getJSONArray("subs")
        return AppState(
            profile = Profile(p.getString("name"), p.getString("email"), p.getBoolean("premium")),
            reminders = ReminderSettings(
                calendarEnabled = r.getBoolean("calendar"),
                pushEnabled = r.getBoolean("push"),
                leadTime = LeadTime.valueOf(r.getString("lead")),
                hour = r.getInt("hour"),
                minute = r.getInt("minute"),
            ),
            rates = ExchangeRates(if(x.isNull("usd")) Double.NaN else x.getDouble("usd"), if(x.isNull("eur")) Double.NaN else x.getDouble("eur"), LocalDateTime.parse(x.getString("updatedAt"))),
            subscriptions = (0 until subs.length()).map { decodeSub(subs.getJSONObject(it)) },
        )
    }

    private fun decodeSub(o: JSONObject): Subscription {
        val pays = o.getJSONArray("payments")
        return Subscription(
            id = o.getString("id"),
            name = o.getString("name"),
            amount = o.getDouble("amount"),
            currency = Currency.valueOf(o.getString("currency")),
            frequency = Frequency.valueOf(o.getString("frequency")),
            nextCharge = LocalDate.parse(o.getString("next")),
            category = Category.valueOf(o.getString("category")),
            notes = o.optString("notes"),
            unusedSince = o.optStringOrNull("unusedSince")?.let(LocalDate::parse),
            cancelled = o.getBoolean("cancelled"),
            startDate = LocalDate.parse(o.getString("start")),
            calendarEventId = if (o.isNull("eventId")) null else o.getLong("eventId"),
            leadTime = o.optStringOrNull("lead")?.let(LeadTime::valueOf),
            createdAt = o.optStringOrNull("createdAt")?.let(LocalDateTime::parse) ?: LocalDateTime.now(),
            payments = (0 until pays.length()).map {
                val p = pays.getJSONObject(it)
                Payment(LocalDate.parse(p.getString("date")), p.getDouble("amount"), Currency.valueOf(p.getString("currency")), p.getDouble("rate"))
            },
        )
    }

    private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key) || !has(key)) null else getString(key)

    // endregion

    companion object {
        private const val KEY_STATE = "state_v1"
    }
}
