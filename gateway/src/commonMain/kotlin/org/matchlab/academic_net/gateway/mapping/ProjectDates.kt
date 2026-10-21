package org.matchlab.academic_net.gateway.mapping

import kotlinx.datetime.YearMonth
import org.matchlab.academic_net.common.model.ProjectPeriod

// The dates of a project, `planned_start` and `planned_end`. The API says only `type: string`; the app writes an ISO date
// (`YYYY-MM-DD`) and reads the year and the month from the start of whatever came (docs/api-values.md).

/** The start of a date or a date with a time: spaces, four digits of the year, a dash, two digits of the month. */
private val YearMonthPrefix = Regex("^\\s*([0-9]{4})-([0-9]{2})")

/**
 * `planned_start` of a project that runs over this period: the first day of the month it starts in, as an ISO date
 * (`2026-10-01`).
 */
fun ProjectPeriod.plannedStart(): String = start.firstDay.toString()

/**
 * `planned_end` of a project that runs over this period: the last day of the month it ends in, as an ISO date
 * (`2026-11-30`; the 29th in February of a leap year).
 */
fun ProjectPeriod.plannedEnd(): String = end.lastDay.toString()

/**
 * The period the server's [start] and [end] stand for, or `null` ("unknown") when there is none: a date is missing, one
 * of them does not begin with `YYYY-MM` of a real month, or the end month is before the start month. Only the year and
 * the month are read, from the start of the text, so `2026-10-01`, `2026-10-01T00:00:00Z` and ` 2026-10` all give
 * October 2026; the day and the time are ignored.
 */
fun periodOfPlanned(start: String?, end: String?): ProjectPeriod? {
    val first = yearMonthOf(start) ?: return null
    val last = yearMonthOf(end) ?: return null
    return if (last < first) null else ProjectPeriod(first, last)
}

/** The month a date text begins with, `null` when it does not begin with `YYYY-MM` or the month is not 01..12. */
private fun yearMonthOf(text: String?): YearMonth? {
    val match = YearMonthPrefix.find(text.orEmpty()) ?: return null
    val (year, month) = match.destructured
    return YearMonth.orNull(year.toInt(), month.toInt())
}
