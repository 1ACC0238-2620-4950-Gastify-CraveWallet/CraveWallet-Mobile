package com.cravewallet.app.data

import android.content.Context
import com.cravewallet.app.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Locale-aware formatting for currency (PEN S/), dates, times, and relative periods. */
object Fmt {

    private fun currentLocale(): Locale = Locale.getDefault()

    fun number(value: Double): String = String.format(Locale.US, "%,.2f", value)

    /** S/ 433.22 */
    fun pen(value: Double, context: Context? = null): String =
        if (value.isFinite()) "S/ ${number(value)}"
        else context?.getString(R.string.fmt_conversion_unavailable) ?: "Conversion unavailable"

    /** USD 39.99 */
    fun money(value: Double, currency: Currency, context: Context? = null): String =
        if (currency == Currency.PEN) pen(value, context) else "${currency.code} ${number(value)}"

    fun rate(value: Double, context: Context? = null): String =
        if (value.isFinite()) String.format(Locale.US, "%.2f", value)
        else context?.getString(R.string.fmt_not_available) ?: "Unavailable"

    fun monthShort(month: Int, locale: Locale = currentLocale()): String =
        Month.of(month).getDisplayName(TextStyle.SHORT, locale).trimEnd('.')

    fun monthShortCap(month: Int, locale: Locale = currentLocale()): String =
        monthShort(month, locale).replaceFirstChar { it.uppercase(locale) }

    fun monthLong(month: Int, locale: Locale = currentLocale()): String =
        Month.of(month).getDisplayName(TextStyle.FULL, locale)

    private fun dayLong(dayOfWeek: DayOfWeek, locale: Locale = currentLocale()): String =
        dayOfWeek.getDisplayName(TextStyle.FULL, locale)

    private fun dayShort(dayOfWeek: DayOfWeek, locale: Locale = currentLocale()): String =
        dayOfWeek.getDisplayName(TextStyle.SHORT, locale).trimEnd('.')

    /** 6 Oct / 6 oct */
    fun dayMonth(date: LocalDate, locale: Locale = currentLocale()): String =
        "${date.dayOfMonth} ${monthShort(date.monthValue, locale)}"

    /** 9 Sep 2026 */
    fun dayMonthYear(date: LocalDate, locale: Locale = currentLocale()): String =
        "${dayMonth(date, locale)} ${date.year}"

    /** Monday, October 5 / Lunes 5 de octubre */
    fun longDate(date: LocalDate, context: Context? = null, locale: Locale = currentLocale()): String {
        val dayName = dayLong(date.dayOfWeek, locale)
        val monthName = monthLong(date.monthValue, locale)
        val formatted = context?.getString(R.string.fmt_long_date, dayName, date.dayOfMonth, monthName)
            ?: if (locale.language == "es") "$dayName ${date.dayOfMonth} de $monthName"
            else "$dayName, $monthName ${date.dayOfMonth}"
        return formatted.replaceFirstChar { it.uppercase(locale) }
    }

    /** Tuesday 3 Nov 2026 */
    fun weekdayDate(date: LocalDate, locale: Locale = currentLocale()): String =
        "${dayLong(date.dayOfWeek, locale)} ${date.dayOfMonth} ${monthShort(date.monthValue, locale)} ${date.year}"

    /** Thu 8 Oct */
    fun shortWeekday(date: LocalDate, locale: Locale = currentLocale()): String =
        "${dayShort(date.dayOfWeek, locale)} ${date.dayOfMonth} ${monthShort(date.monthValue, locale)}"

    /** October 2026 */
    fun monthYear(ym: YearMonth, locale: Locale = currentLocale()): String =
        "${monthLong(ym.monthValue, locale).replaceFirstChar { it.uppercase(locale) }} ${ym.year}"

    /** 9:00 AM / 9:00 a. m. */
    fun time(hour: Int, minute: Int, context: Context? = null, locale: Locale = currentLocale()): String {
        val h12 = if (hour % 12 == 0) 12 else hour % 12
        val suffix = if (hour < 12) {
            context?.getString(R.string.fmt_am) ?: if (locale.language == "es") "a. m." else "AM"
        } else {
            context?.getString(R.string.fmt_pm) ?: if (locale.language == "es") "p. m." else "PM"
        }
        return String.format(Locale.US, "%d:%02d %s", h12, minute, suffix)
    }

    fun time(t: LocalTime, context: Context? = null): String = time(t.hour, t.minute, context)

    /** 08:00 */
    fun clock(dt: LocalDateTime): String = String.format(Locale.US, "%02d:%02d", dt.hour, dt.minute)

    /** today, 08:00 · yesterday, 08:00 · 3 Oct, 08:00 */
    fun relativeDateTime(dt: LocalDateTime, today: LocalDate = LocalDate.now(), context: Context? = null): String {
        val d = dt.toLocalDate()
        val day = when (d) {
            today -> context?.getString(R.string.fmt_today_lower) ?: "today"
            today.minusDays(1) -> context?.getString(R.string.fmt_yesterday_lower) ?: "yesterday"
            else -> dayMonth(d)
        }
        return "$day, ${clock(dt)}"
    }

    /** Today · Tomorrow · In 4 days · 2 days ago */
    fun relativeDays(date: LocalDate, today: LocalDate = LocalDate.now(), context: Context? = null): String {
        val days = ChronoUnit.DAYS.between(today, date)
        return when {
            days == 0L -> context?.getString(R.string.fmt_today) ?: "Today"
            days == 1L -> context?.getString(R.string.fmt_tomorrow) ?: "Tomorrow"
            days > 1 -> context?.getString(R.string.fmt_in_days, days) ?: "In $days days"
            days == -1L -> context?.getString(R.string.fmt_yesterday) ?: "Yesterday"
            else -> context?.getString(R.string.fmt_days_ago, -days) ?: "${-days} days ago"
        }
    }

    /** dd/mm/yyyy */
    fun input(date: LocalDate): String = String.format(Locale.US, "%02d/%02d/%04d", date.dayOfMonth, date.monthValue, date.year)

    fun percent(value: Double): String = "${Math.round(value * 100)} %"

    /** Initials for a service avatar. */
    fun serviceInitials(name: String): String {
        val known = mapOf(
            "linkedin" to "in", "youtube" to "YT", "amazon" to "a", "disney" to "D+",
            "smart fit" to "SF", "chatgpt" to "AI", "google" to "G",
        )
        val lower = name.lowercase()
        known.entries.firstOrNull { lower.startsWith(it.key) }?.let { return it.value }
        return name.trim().firstOrNull()?.uppercase() ?: "?"
    }
}
