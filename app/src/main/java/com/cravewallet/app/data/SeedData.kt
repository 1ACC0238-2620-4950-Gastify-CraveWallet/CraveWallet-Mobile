package com.cravewallet.app.data

import java.time.LocalDate
import java.time.YearMonth

/**
 * Datos de ejemplo del prototipo (persona: Renzo). Las fechas se calculan a partir de hoy
 * para que los estados (Cobro hoy, Pronto, etc.) siempre se vean como en los wireframes.
 */
object SeedData {

    /** Tipo de cambio USD histórico de ejemplo, por meses hacia atrás (1 = mes pasado). */
    private fun historicUsd(monthsAgo: Long): Double = when (monthsAgo) {
        0L -> 3.76
        1L -> 3.73
        2L -> 3.77
        3L -> 3.74
        4L -> 3.72
        5L -> 3.70
        else -> 3.71
    }

    private fun historicRate(currency: Currency, date: LocalDate, today: LocalDate): Double {
        val monthsAgo = java.time.temporal.ChronoUnit.MONTHS.between(YearMonth.from(date), YearMonth.from(today))
        return when (currency) {
            Currency.PEN -> 1.0
            Currency.USD -> historicUsd(monthsAgo)
            Currency.EUR -> historicUsd(monthsAgo) + 0.34
        }
    }

    /** Genera los cobros pasados desde [start] hasta antes de [next]. */
    fun pastPayments(
        amount: Double,
        currency: Currency,
        frequency: Frequency,
        start: LocalDate,
        next: LocalDate,
        today: LocalDate = LocalDate.now(),
    ): List<Payment> {
        val result = mutableListOf<Payment>()
        var d = next.minusMonths(frequency.months)
        while (!d.isBefore(start)) {
            if (!d.isAfter(today)) result += Payment(d, amount, currency, historicRate(currency, d, today))
            d = d.minusMonths(frequency.months)
        }
        return result.sortedByDescending { it.date }
    }

    private fun sub(
        id: String,
        name: String,
        amount: Double,
        currency: Currency,
        category: Category,
        inDays: Long,
        startMonthsAgo: Long,
        notes: String = "",
        unusedDaysAgo: Long? = null,
        today: LocalDate,
    ): Subscription {
        val next = today.plusDays(inDays)
        val start = next.minusMonths(startMonthsAgo)
        return Subscription(
            id = id,
            name = name,
            amount = amount,
            currency = currency,
            frequency = Frequency.MENSUAL,
            nextCharge = next,
            category = category,
            notes = notes,
            unusedSince = unusedDaysAgo?.let { today.minusDays(it) },
            startDate = start,
            payments = pastPayments(amount, currency, Frequency.MENSUAL, start, next, today),
        )
    }

    fun subscriptions(today: LocalDate = LocalDate.now()): List<Subscription> = listOf(
        sub("spotify", "Spotify Premium", 20.90, Currency.PEN, Category.MUSICA, 1, 14, today = today),
        sub(
            "linkedin", "LinkedIn Premium", 39.99, Currency.USD, Category.PRODUCTIVIDAD, 4, 2,
            notes = "Lo saqué para buscar trabajo. ¿Lo sigo necesitando?", today = today,
        ),
        sub("max", "Max", 34.90, Currency.PEN, Category.STREAMING, 7, 13, today = today),
        sub("smartfit", "Smart Fit Black", 129.90, Currency.PEN, Category.FITNESS, 10, 12, unusedDaysAgo = 34, today = today),
        sub("youtube", "YouTube Premium", 25.90, Currency.PEN, Category.STREAMING, 13, 13, today = today),
        sub("amazon", "Amazon Prime", 14.99, Currency.USD, Category.STREAMING, 17, 13, today = today),
        sub("pedidosya", "PedidosYa Plus", 14.90, Currency.PEN, Category.DELIVERY, 22, 13, today = today),
    )

    fun profile() = Profile(name = "Renzo Salazar", email = "renzo.salazar@gmail.com", premium = true)

    fun state(today: LocalDate = LocalDate.now()) = AppState(
        profile = profile(),
        subscriptions = subscriptions(today),
        reminders = ReminderSettings(calendarEnabled = true, pushEnabled = false),
        rates = ExchangeRates(updatedAt = today.atTime(8, 0)),
    )

    /** Estado vacío (persona: Camila, plan gratuito) para mostrar I2 y A9. */
    fun emptyState(today: LocalDate = LocalDate.now()) = AppState(
        profile = Profile(name = "Camila Torres", email = "camila.torres@gmail.com", premium = false),
        subscriptions = emptyList(),
        reminders = ReminderSettings(calendarEnabled = true, pushEnabled = false),
        rates = ExchangeRates(updatedAt = today.atTime(8, 0)),
    )
}
