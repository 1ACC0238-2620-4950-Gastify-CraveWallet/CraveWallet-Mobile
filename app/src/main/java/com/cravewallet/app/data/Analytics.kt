package com.cravewallet.app.data

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

/** Un punto del gráfico de barras: un mes (o una semana en el modo "Mes"). */
data class Bar(
    val label: String,
    val total: Double,
    val count: Int,
    val month: YearMonth? = null,
    val tooltip: String,
)

data class CategoryShare(
    val category: Category,
    val total: Double,
    val share: Double,
    val subscriptions: List<Subscription>,
)

data class Insight(
    val title: String,
    val body: String,
    val subscriptionId: String,
)

enum class Range(val label: String, val months: Int) { MES("Mes", 1), SEIS_MESES("6 meses", 6), ANIO("Año", 12) }

/** Cálculos para Inicio y Análisis. Los meses pasados salen de los cobros registrados; el mes actual, de la proyección. */
object Analytics {

    fun monthTotal(state: AppState, month: YearMonth, today: LocalDate = LocalDate.now()): Pair<Double, Int> {
        if (month == YearMonth.from(today)) return state.monthlyTotal to state.active.size
        val payments = state.subscriptions.flatMap { s -> s.payments.filter { YearMonth.from(it.date) == month }.map { s.id to it } }
        return payments.sumOf { it.second.amountPen } to payments.map { it.first }.distinct().size
    }

    fun categoryMonthTotal(state: AppState, category: Category, month: YearMonth, today: LocalDate = LocalDate.now()): Double {
        if (month == YearMonth.from(today)) return state.active.filter { it.category == category }.sumOf { state.monthlyPen(it) }
        return state.subscriptions.filter { it.category == category }
            .flatMap { it.payments }.filter { YearMonth.from(it.date) == month }.sumOf { it.amountPen }
    }

    fun bars(state: AppState, range: Range, today: LocalDate = LocalDate.now()): List<Bar> {
        val current = YearMonth.from(today)
        if (range == Range.MES) return weekBars(state, current)
        return (range.months - 1 downTo 0).map { back ->
            val m = current.minusMonths(back.toLong())
            val (total, count) = monthTotal(state, m, today)
            Bar(
                label = Fmt.monthShortCap(m.monthValue),
                total = total,
                count = count,
                month = m,
                tooltip = "${Fmt.monthShortCap(m.monthValue)} · ${Fmt.pen(total)}",
            )
        }
    }

    /** Modo "Mes": cobros del mes actual agrupados por semana (según la fecha de cobro). */
    private fun weekBars(state: AppState, month: YearMonth): List<Bar> {
        val ranges = listOf(1..7, 8..14, 15..21, 22..28, 29..month.lengthOfMonth())
        return ranges.mapIndexed { i, days ->
            val subs = state.active.filter { s ->
                val chargeDay = chargeDayIn(s, month)
                chargeDay != null && chargeDay in days
            }
            val total = subs.sumOf { state.pen(it) }
            Bar(
                label = "Sem ${i + 1}",
                total = total,
                count = subs.size,
                tooltip = "${days.first}–${days.last} ${Fmt.monthShort(month.monthValue)} · ${Fmt.pen(total)}",
            )
        }
    }

    private fun chargeDayIn(s: Subscription, month: YearMonth): Int? {
        if (YearMonth.from(s.nextCharge) == month) return s.nextCharge.dayOfMonth
        return s.payments.firstOrNull { YearMonth.from(it.date) == month }?.date?.dayOfMonth
    }

    fun categories(state: AppState): List<CategoryShare> {
        val total = state.monthlyTotal
        if (total <= 0) return emptyList()
        return state.active.groupBy { it.category }
            .map { (cat, subs) ->
                val t = subs.sumOf { state.monthlyPen(it) }
                CategoryShare(cat, t, t / total, subs.sortedByDescending { state.monthlyPen(it) })
            }
            .sortedByDescending { it.total }
    }

    /** Busca la suscripción que más subió el gasto dentro del rango mostrado. */
    fun insight(state: AppState, today: LocalDate = LocalDate.now()): Insight? {
        val windowStart = YearMonth.from(today).minusMonths(5)
        val candidate = state.active
            .filter { !YearMonth.from(it.startDate).isBefore(windowStart.plusMonths(1)) && !it.startDate.isAfter(today) && it.payments.isNotEmpty() }
            .maxByOrNull { state.monthlyPen(it) } ?: return null
        val start = YearMonth.from(candidate.startDate)
        val monthly = state.monthlyPen(candidate)
        val currencyNote = if (candidate.currency == Currency.PEN) "que se cobra en soles" else "que se cobra en ${currencyName(candidate.currency)}"
        return Insight(
            title = "Desde ${Fmt.monthLong(start.monthValue)} gastas S/ ${Math.round(roundTo(monthly))} más al mes",
            body = "Es ${candidate.name}, $currencyNote.",
            subscriptionId = candidate.id,
        )
    }

    private fun roundTo(v: Double): Double = if (v >= 20) Math.round(v / 10.0) * 10.0 else v

    fun currencyName(c: Currency): String = when (c) {
        Currency.PEN -> "soles"
        Currency.USD -> "dólares"
        Currency.EUR -> "euros"
    }

    /** Texto de tendencia para una serie de totales: "se mantiene estable", "subió" o "bajó". */
    fun trend(values: List<Double>, categoryLabel: String): String {
        val nonZero = values.filter { it > 0 }
        if (nonZero.isEmpty()) return "Aún no hay cobros de $categoryLabel en estos meses."
        val min = nonZero.min()
        val max = nonZero.max()
        val first = nonZero.first()
        val last = values.last()
        return when {
            max - min <= max * 0.05 ->
                "Tu gasto en ${categoryLabel.lowercase()} se mantiene estable: entre ${Fmt.pen(min).substringBefore('.')} y ${Fmt.pen(max).substringBefore('.')} al mes."
            last > first -> "Tu gasto en ${categoryLabel.lowercase()} subió ${Fmt.pen(last - first)} desde hace ${values.size - 1} meses."
            else -> "Tu gasto en ${categoryLabel.lowercase()} bajó ${Fmt.pen(abs(first - last))} desde hace ${values.size - 1} meses."
        }
    }

    /** Variación del próximo cobro frente al último pago (solo tiene sentido en moneda extranjera). */
    fun variation(state: AppState, sub: Subscription): Pair<Double, Payment>? {
        val last = sub.payments.maxByOrNull { it.date } ?: return null
        return (state.pen(sub) - last.amountPen) to last
    }
}
