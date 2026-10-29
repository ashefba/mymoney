package com.mymoney.app.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Dates are stored as epoch days ([LocalDate.toEpochDay]) to avoid time zone drift. */
object Dates {
    fun today(): LocalDate = LocalDate.now()
    fun todayEpoch(): Long = today().toEpochDay()

    fun of(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)

    fun monthStart(ym: YearMonth): Long = ym.atDay(1).toEpochDay()
    fun monthEnd(ym: YearMonth): Long = ym.atEndOfMonth().toEpochDay()

    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Material3 DatePicker works in UTC millis. */
    fun toPickerMillis(epochDay: Long): Long =
        LocalDate.ofEpochDay(epochDay).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun fromPickerMillis(millis: Long): Long =
        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()

    fun daysBetween(from: LocalDate, to: LocalDate): Long = ChronoUnit.DAYS.between(from, to)

    /** Whole months from [from] to [to], counting a partial month as a full one; at least 1. */
    fun monthsUntil(from: LocalDate, to: LocalDate): Long {
        if (!to.isAfter(from)) return 1
        val full = ChronoUnit.MONTHS.between(from, to)
        val remainder = from.plusMonths(full)
        return (if (remainder.isBefore(to)) full + 1 else full).coerceAtLeast(1)
    }

    private val shortFmt = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    private val mediumFmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    private val monthFmt = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault())
    private val shortMonthFmt = DateTimeFormatter.ofPattern("LLL", Locale.getDefault())
    private val shortMonthYearFmt = DateTimeFormatter.ofPattern("LLL yy", Locale.getDefault())
    private val dayNameFmt = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
    private val fileFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun short(epochDay: Long): String = of(epochDay).format(shortFmt)
    fun medium(epochDay: Long): String = of(epochDay).format(mediumFmt)
    fun medium(date: LocalDate): String = date.format(mediumFmt)
    fun month(ym: YearMonth): String = ym.format(monthFmt).replaceFirstChar { it.titlecase(Locale.getDefault()) }
    fun shortMonth(ym: YearMonth): String = ym.format(shortMonthFmt).replaceFirstChar { it.titlecase(Locale.getDefault()) }
    fun shortMonthYear(ym: YearMonth): String = ym.format(shortMonthYearFmt)
    fun dayName(date: LocalDate): String = date.format(dayNameFmt)
    fun iso(date: LocalDate): String = date.format(fileFmt)

    fun relative(epochDay: Long): String {
        val d = of(epochDay)
        val t = today()
        return when (d) {
            t -> "Today"
            t.minusDays(1) -> "Yesterday"
            t.plusDays(1) -> "Tomorrow"
            else -> if (d.year == t.year) short(epochDay) else medium(epochDay)
        }
    }

    fun dueText(epochDay: Long): String {
        val days = daysBetween(today(), of(epochDay))
        return when {
            days < -1 -> "Overdue by ${-days} days"
            days == -1L -> "Overdue since yesterday"
            days == 0L -> "Due today"
            days == 1L -> "Due tomorrow"
            days < 14 -> "Due in $days days"
            else -> "Due ${medium(epochDay)}"
        }
    }

    fun nowMillis(): Long = System.currentTimeMillis()
    fun zone(): ZoneId = ZoneId.systemDefault()
}
