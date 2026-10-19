package org.matchlab.academic_net.utils

import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * How many calendar days in [zone] lie between the day of [since] and the day of [now]: 0 on the same day, 1 the day
 * after. A [since] later than [now] (clock skew) is 0. The UI words it ("сегодня", "2 дня назад").
 *
 * Days are compared as calendar dates, not as 24-hour distances: 23:50 yesterday is 1 even at 00:10 today.
 */
fun elapsedDays(since: Instant, now: Instant, zone: TimeZone): Int {
    val sinceDate = since.toLocalDateTime(zone).date
    val today = now.toLocalDateTime(zone).date
    return sinceDate.daysUntil(today).coerceAtLeast(0)
}
