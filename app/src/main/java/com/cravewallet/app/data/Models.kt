package com.cravewallet.app.data

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.cravewallet.app.R
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

enum class Currency(val code: String, val label: String) {
    PEN("PEN", "Sol peruano"),
    USD("USD", "Dólar estadounidense"),
    EUR("EUR", "Euro"),
}

enum class Frequency(val label: String, val months: Long) {
    MENSUAL("Mensual", 1),
    TRIMESTRAL("Trimestral", 3),
    ANUAL("Anual", 12);

    fun next(date: LocalDate): LocalDate = date.plusMonths(months)
}

enum class Category(val label: String, @param:DrawableRes val icon: Int, val color: Color) {
    STREAMING("Streaming", R.drawable.ic_play_circle, Color(0xFFF97316)),
    PRODUCTIVIDAD("Productividad", R.drawable.ic_work, Color(0xFF3B4FD8)),
    FITNESS("Fitness", R.drawable.ic_fitness_center, Color(0xFF22C55E)),
    MUSICA("Música", R.drawable.ic_headphones, Color(0xFF38BDF8)),
    DELIVERY("Delivery", R.drawable.ic_moped, Color(0xFFFBBF24)),
    OTROS("Otros", R.drawable.ic_category, Color(0xFF64748B)),
}

enum class SubStatus(val label: String, @param:DrawableRes val icon: Int, val container: Color, val content: Color) {
    ACTIVA("Activa", R.drawable.ic_check_circle_fill, Color(0xFF22C55E), Color(0xFF0F172A)),
    COBRO_HOY("Cobro hoy", R.drawable.ic_notifications_active_fill, Color(0xFFF97316), Color(0xFF0F172A)),
    PRONTO("Pronto", R.drawable.ic_schedule_fill, Color(0xFFFBBF24), Color(0xFF0F172A)),
    SIN_USAR("Sin usar", R.drawable.ic_visibility_off_fill, Color(0xFF64748B), Color.White),
    CANCELADA("Cancelada", R.drawable.ic_cancel_fill, Color(0xFFEF4444), Color(0xFF0F172A)),
    PENDIENTE("Pendiente", R.drawable.ic_hourglass_empty_fill, Color(0xFF38BDF8), Color(0xFF0F172A)),
}

enum class LeadTime(val label: String, val chip: String, val short: String, val days: Long) {
    SAME_DAY("El mismo día del cobro", "El mismo día", "el mismo día del cobro", 0),
    ONE_DAY("1 día antes (24 h)", "24 h antes", "1 día antes (24 h)", 1),
    THREE_DAYS("3 días antes", "3 días antes", "3 días antes del cobro", 3),
    SEVEN_DAYS("7 días antes", "7 días antes", "7 días antes del cobro", 7),
}

/** Un cobro ya realizado. El monto está en la moneda original. */
data class Payment(
    val date: LocalDate,
    val amount: Double,
    val currency: Currency,
    val rate: Double,
) {
    val amountPen: Double get() = amount * rate
}

data class Subscription(
    val id: String,
    val name: String,
    val amount: Double,
    val currency: Currency,
    val frequency: Frequency,
    val nextCharge: LocalDate,
    val category: Category,
    val notes: String = "",
    val unusedSince: LocalDate? = null,
    val cancelled: Boolean = false,
    val startDate: LocalDate = nextCharge,
    val payments: List<Payment> = emptyList(),
    val calendarEventId: Long? = null,
    val leadTime: LeadTime? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
) {
    val isActive: Boolean get() = !cancelled
    val isUnused: Boolean get() = unusedSince != null
}

data class ReminderSettings(
    val calendarEnabled: Boolean = true,
    val pushEnabled: Boolean = false,
    val leadTime: LeadTime = LeadTime.ONE_DAY,
    val hour: Int = 9,
    val minute: Int = 0,
)

data class Profile(
    val name: String,
    val email: String,
    val premium: Boolean,
) {
    val firstName: String get() = name.trim().substringBefore(' ').ifBlank { "Hola" }
    val initials: String
        get() = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            .take(2).joinToString("") { it.first().uppercase() }.ifBlank { "CW" }
}

data class ExchangeRates(
    val usd: Double = 3.76,
    val eur: Double = 4.10,
    val updatedAt: LocalDateTime = LocalDate.now().atTime(8, 0),
) {
    fun rate(currency: Currency): Double = when (currency) {
        Currency.PEN -> 1.0
        Currency.USD -> usd
        Currency.EUR -> eur
    }
}

data class AppState(
    val profile: Profile,
    val subscriptions: List<Subscription>,
    val reminders: ReminderSettings,
    val rates: ExchangeRates,
) {
    val active: List<Subscription> get() = subscriptions.filter { it.isActive }

    fun pen(sub: Subscription): Double = sub.amount * rates.rate(sub.currency)

    /** Costo mensual equivalente en soles (anual / 12, trimestral / 3). */
    fun monthlyPen(sub: Subscription): Double = pen(sub) / sub.frequency.months

    val monthlyTotal: Double get() = active.sumOf { monthlyPen(it) }

    fun status(sub: Subscription, today: LocalDate = LocalDate.now()): SubStatus {
        if (sub.cancelled) return SubStatus.CANCELADA
        if (sub.isUnused) return SubStatus.SIN_USAR
        val days = ChronoUnit.DAYS.between(today, sub.nextCharge)
        return when {
            days <= 1 -> SubStatus.COBRO_HOY
            days <= 7 -> SubStatus.PRONTO
            else -> SubStatus.ACTIVA
        }
    }

    companion object {
        const val FREE_PLAN_LIMIT = 5
        const val PREMIUM_PRICE = 9.90
    }
}
