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

/**
 * Fuente única de verdad. Guarda el estado como JSON en SharedPreferences,
 * así los datos sobreviven al cerrar la app sin depender de una base de datos.
 */
class Repository(context: Context) {

    private val prefs = context.getSharedPreferences("cravewallet", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppState> = _state.asStateFlow()

    val current: AppState get() = _state.value

    private fun load(): AppState {
        val raw = prefs.getString(KEY_STATE, null)
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
        prefs.edit().putString(KEY_STATE, encode(_state.value).toString()).apply()
    }

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
            put("usd", s.rates.usd); put("eur", s.rates.eur); put("updatedAt", s.rates.updatedAt.toString())
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
            rates = ExchangeRates(x.getDouble("usd"), x.getDouble("eur"), LocalDateTime.parse(x.getString("updatedAt"))),
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
