package com.cravewallet.app.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Formatos en español (Perú). Se arman a mano para no depender de los datos CLDR del dispositivo. */
object Fmt {
    private val monthsShort = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
    private val monthsLong = listOf(
        "enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
    )
    private val daysLong = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")
    private val daysShort = listOf("lun", "mar", "mié", "jue", "vie", "sáb", "dom")

    fun number(value: Double): String = String.format(Locale.US, "%,.2f", value)

    /** S/ 433.22 */
    fun pen(value: Double): String = if(value.isFinite()) "S/ ${number(value)}" else "Conversión no disponible"

    /** USD 39.99 */
    fun money(value: Double, currency: Currency): String =
        if (currency == Currency.PEN) pen(value) else "${currency.code} ${number(value)}"

    fun rate(value: Double): String = if(value.isFinite()) String.format(Locale.US, "%.2f", value) else "No disponible"

    fun monthShort(month: Int): String = monthsShort[month - 1]
    fun monthShortCap(month: Int): String = monthShort(month).replaceFirstChar { it.uppercase() }
    fun monthLong(month: Int): String = monthsLong[month - 1]

    /** 6 oct */
    fun dayMonth(date: LocalDate): String = "${date.dayOfMonth} ${monthShort(date.monthValue)}"

    /** 9 sep 2026 */
    fun dayMonthYear(date: LocalDate): String = "${dayMonth(date)} ${date.year}"

    /** Lunes 5 de octubre */
    fun longDate(date: LocalDate): String =
        "${daysLong[date.dayOfWeek.value - 1]} ${date.dayOfMonth} de ${monthsLong[date.monthValue - 1]}"
            .replaceFirstChar { it.uppercase() }

    /** martes 3 nov 2026 */
    fun weekdayDate(date: LocalDate): String =
        "${daysLong[date.dayOfWeek.value - 1]} ${date.dayOfMonth} ${monthShort(date.monthValue)} ${date.year}"

    /** jue 8 oct */
    fun shortWeekday(date: LocalDate): String =
        "${daysShort[date.dayOfWeek.value - 1]} ${date.dayOfMonth} ${monthShort(date.monthValue)}"

    /** Octubre 2026 */
    fun monthYear(ym: YearMonth): String = "${monthsLong[ym.monthValue - 1].replaceFirstChar { it.uppercase() }} ${ym.year}"

    /** 9:00 a. m. */
    fun time(hour: Int, minute: Int): String {
        val h12 = if (hour % 12 == 0) 12 else hour % 12
        val suffix = if (hour < 12) "a. m." else "p. m."
        return String.format(Locale.US, "%d:%02d %s", h12, minute, suffix)
    }

    fun time(t: LocalTime): String = time(t.hour, t.minute)

    /** 08:00 */
    fun clock(dt: LocalDateTime): String = String.format(Locale.US, "%02d:%02d", dt.hour, dt.minute)

    /** hoy, 08:00 · ayer, 08:00 · 3 oct, 08:00 */
    fun relativeDateTime(dt: LocalDateTime, today: LocalDate = LocalDate.now()): String {
        val d = dt.toLocalDate()
        val day = when (d) {
            today -> "hoy"
            today.minusDays(1) -> "ayer"
            else -> dayMonth(d)
        }
        return "$day, ${clock(dt)}"
    }

    /** Hoy · Mañana · En 4 días · Hace 2 días */
    fun relativeDays(date: LocalDate, today: LocalDate = LocalDate.now()): String {
        val days = ChronoUnit.DAYS.between(today, date)
        return when {
            days == 0L -> "Hoy"
            days == 1L -> "Mañana"
            days > 1 -> "En $days días"
            days == -1L -> "Ayer"
            else -> "Hace ${-days} días"
        }
    }

    /** dd/mm/aaaa */
    fun input(date: LocalDate): String = String.format(Locale.US, "%02d/%02d/%04d", date.dayOfMonth, date.monthValue, date.year)

    fun percent(value: Double): String = "${Math.round(value * 100)} %"

    /** Iniciales para el avatar de un servicio. */
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
