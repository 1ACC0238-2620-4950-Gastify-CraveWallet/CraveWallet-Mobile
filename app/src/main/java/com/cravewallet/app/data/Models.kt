package com.cravewallet.app.data

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.cravewallet.app.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

enum class Currency(val code: String, @param:StringRes val labelRes: Int, @param:StringRes val pluralRes: Int) {
    PEN("PEN", R.string.currency_pen_label, R.string.currency_pen_plural),
    USD("USD", R.string.currency_usd_label, R.string.currency_usd_plural),
    EUR("EUR", R.string.currency_eur_label, R.string.currency_eur_plural);

    fun label(context: Context): String = context.getString(labelRes)
}

enum class Frequency(@param:StringRes val labelRes: Int, val months: Long) {
    MENSUAL(R.string.freq_monthly, 1),
    TRIMESTRAL(R.string.freq_quarterly, 3),
    ANUAL(R.string.freq_annual, 12);

    fun next(date: LocalDate): LocalDate = date.plusMonths(months)
    fun label(context: Context): String = context.getString(labelRes)
}

enum class Category(val legacyLabel: String, @param:StringRes val labelRes: Int, @param:DrawableRes val icon: Int, val color: Color) {
    STREAMING("Streaming", R.string.cat_streaming, R.drawable.ic_play_circle, Color(0xFFF97316)),
    PRODUCTIVIDAD("Productividad", R.string.cat_productivity, R.drawable.ic_work, Color(0xFF3B4FD8)),
    FITNESS("Fitness", R.string.cat_fitness, R.drawable.ic_fitness_center, Color(0xFF22C55E)),
    MUSICA("Música", R.string.cat_music, R.drawable.ic_headphones, Color(0xFF38BDF8)),
    DELIVERY("Delivery", R.string.cat_delivery, R.drawable.ic_moped, Color(0xFFFBBF24)),
    OTROS("Otros", R.string.cat_other, R.drawable.ic_category, Color(0xFF64748B));

    fun label(context: Context): String = context.getString(labelRes)
}

enum class SubStatus(@param:StringRes val labelRes: Int, @param:DrawableRes val icon: Int, val container: Color, val content: Color) {
    ACTIVA(R.string.status_active, R.drawable.ic_check_circle_fill, Color(0xFF22C55E), Color(0xFF0F172A)),
    COBRO_HOY(R.string.status_charge_today, R.drawable.ic_notifications_active_fill, Color(0xFFF97316), Color(0xFF0F172A)),
    PRONTO(R.string.status_soon, R.drawable.ic_schedule_fill, Color(0xFFFBBF24), Color(0xFF0F172A)),
    SIN_USAR(R.string.status_unused, R.drawable.ic_visibility_off_fill, Color(0xFF64748B), Color.White),
    CANCELADA(R.string.status_cancelled, R.drawable.ic_cancel_fill, Color(0xFFEF4444), Color(0xFF0F172A)),
    PENDIENTE(R.string.status_pending, R.drawable.ic_hourglass_empty_fill, Color(0xFF38BDF8), Color(0xFF0F172A));

    fun label(context: Context): String = context.getString(labelRes)
}

enum class LeadTime(
    @param:StringRes val labelRes: Int,
    @param:StringRes val chipRes: Int,
    @param:StringRes val shortRes: Int,
    val days: Long,
) {
    SAME_DAY(R.string.lead_same_day_label, R.string.lead_same_day_chip, R.string.lead_same_day_short, 0),
    ONE_DAY(R.string.lead_one_day_label, R.string.lead_one_day_chip, R.string.lead_one_day_short, 1),
    THREE_DAYS(R.string.lead_three_days_label, R.string.lead_three_days_chip, R.string.lead_three_days_short, 3),
    SEVEN_DAYS(R.string.lead_seven_days_label, R.string.lead_seven_days_chip, R.string.lead_seven_days_short, 7);

    fun label(context: Context): String = context.getString(labelRes)
    fun chip(context: Context): String = context.getString(chipRes)
    fun short(context: Context): String = context.getString(shortRes)
}

/** A recorded past payment. The amount is in the original currency. */
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
    fun firstName(defaultGreeting: String): String = name.trim().substringBefore(' ').ifBlank { defaultGreeting }
    val initials: String
        get() = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            .take(2).joinToString("") { it.first().uppercase() }.ifBlank { "CW" }
}

data class PremiumStatus(
    val pending: Boolean,
    val periodEndsAt: Instant?,
    val cancelAtPeriodEnd: Boolean,
    val monthlyPrice: Double,
)

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
    val backendMode: Boolean = false,
    val serverMonthlyTotal: Double? = null,
    val conversionAvailable: Boolean = true,
    val rateAttribution: String? = null,
    val rateStale: Boolean = false,
    val premiumStatus: PremiumStatus? = null,
) {
    val active: List<Subscription> get() = subscriptions.filter { it.isActive }
    val premiumPrice: Double get() = premiumStatus?.monthlyPrice ?: PREMIUM_PRICE

    fun pen(sub: Subscription): Double = sub.amount * rates.rate(sub.currency)

    /** Equivalent monthly cost in soles (annual / 12, quarterly / 3). */
    fun monthlyPen(sub: Subscription): Double = pen(sub) / sub.frequency.months

    val monthlyTotal: Double get() = if (backendMode) serverMonthlyTotal ?: Double.NaN else active.sumOf { monthlyPen(it) }

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
        const val PREMIUM_PRICE = 9.99
    }
}
