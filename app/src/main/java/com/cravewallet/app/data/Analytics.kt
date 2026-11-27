package com.cravewallet.app.data

import android.content.Context
import androidx.annotation.StringRes
import com.cravewallet.app.R
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

/** A bar chart point: a month (or a week in "Month" mode). */
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

enum class Range(@param:StringRes val labelRes: Int, val months: Int) {
    MES(R.string.range_month, 1),
    SEIS_MESES(R.string.range_six_months, 6),
    ANIO(R.string.range_year, 12);

    fun label(context: Context): String = context.getString(labelRes)
}

/** Calculations for Home and Analysis. Past months come from recorded payments; current month from projection. */
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

    fun bars(state: AppState, range: Range, today: LocalDate = LocalDate.now(), context: Context? = null): List<Bar> {
        val current = YearMonth.from(today)
        if (range == Range.MES) return weekBars(state, current, context)
        return (range.months - 1 downTo 0).map { back ->
            val m = current.minusMonths(back.toLong())
            val (total, count) = monthTotal(state, m, today)
            Bar(
                label = Fmt.monthShortCap(m.monthValue),
                total = total,
                count = count,
                month = m,
                tooltip = "${Fmt.monthShortCap(m.monthValue)} · ${Fmt.pen(total, context)}",
            )
        }
    }

    /** "Month" mode: current month charges grouped by week (by billing date). */
    private fun weekBars(state: AppState, month: YearMonth, context: Context? = null): List<Bar> {
        val ranges = listOf(1..7, 8..14, 15..21, 22..28, 29..month.lengthOfMonth())
        return ranges.mapIndexed { i, days ->
            val subs = state.active.filter { s ->
                val chargeDay = chargeDayIn(s, month)
                chargeDay != null && chargeDay in days
            }
            val total = subs.sumOf { state.pen(it) }
            val weekLabel = context?.getString(R.string.fmt_week_short, i + 1) ?: "Wk ${i + 1}"
            Bar(
                label = weekLabel,
                total = total,
                count = subs.size,
                tooltip = "${days.first}–${days.last} ${Fmt.monthShort(month.monthValue)} · ${Fmt.pen(total, context)}",
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

    /** Finds the subscription that increased spending the most within the displayed range. */
    fun insight(state: AppState, context: Context, today: LocalDate = LocalDate.now()): Insight? {
        val windowStart = YearMonth.from(today).minusMonths(5)
        val candidate = state.active
            .filter { !YearMonth.from(it.startDate).isBefore(windowStart.plusMonths(1)) && !it.startDate.isAfter(today) && it.payments.isNotEmpty() }
            .maxByOrNull { state.monthlyPen(it) } ?: return null
        val start = YearMonth.from(candidate.startDate)
        val monthly = state.monthlyPen(candidate)
        val currencyPlural = currencyName(candidate.currency, context)
        return Insight(
            title = context.getString(R.string.analysis_insight_title, Fmt.monthLong(start.monthValue), Math.round(roundTo(monthly))),
            body = context.getString(R.string.analysis_insight_body, candidate.name, currencyPlural),
            subscriptionId = candidate.id,
        )
    }

    private fun roundTo(v: Double): Double = if (v >= 20) Math.round(v / 10.0) * 10.0 else v

    fun currencyName(c: Currency, context: Context): String = context.getString(c.pluralRes)

    /** Trend text for a series of totals: stable, rose, or dropped. */
    fun trend(values: List<Double>, categoryLabel: String, context: Context): String {
        val nonZero = values.filter { it > 0 }
        if (nonZero.isEmpty()) return context.getString(R.string.analysis_trend_empty, categoryLabel)
        val min = nonZero.min()
        val max = nonZero.max()
        val first = nonZero.first()
        val last = values.last()
        return when {
            max - min <= max * 0.05 ->
                context.getString(
                    R.string.analysis_trend_stable,
                    categoryLabel.lowercase(),
                    Fmt.pen(min, context).substringBefore('.'),
                    Fmt.pen(max, context).substringBefore('.'),
                )
            last > first ->
                context.getString(
                    R.string.analysis_trend_up,
                    categoryLabel.lowercase(),
                    Fmt.pen(last - first, context),
                    values.size - 1,
                )
            else ->
                context.getString(
                    R.string.analysis_trend_down,
                    categoryLabel.lowercase(),
                    Fmt.pen(abs(first - last), context),
                    values.size - 1,
                )
        }
    }

    /** Variation of next charge vs. last payment (only relevant for foreign currency). */
    fun variation(state: AppState, sub: Subscription): Pair<Double, Payment>? {
        val last = sub.payments.maxByOrNull { it.date } ?: return null
        return (state.pen(sub) - last.amountPen) to last
    }
}
