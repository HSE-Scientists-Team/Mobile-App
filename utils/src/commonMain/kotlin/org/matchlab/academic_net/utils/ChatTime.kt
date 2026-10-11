package org.matchlab.academic_net.utils

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus
import kotlin.time.Instant

/** Time under a message bubble: "10:22". */
fun formatMessageTime(sentAt: Instant, zone: TimeZone): String {
    val time = sentAt.toLocalDateTime(zone).time
    return "${time.hour.twoDigits()}:${time.minute.twoDigits()}"
}

/**
 * Time of the last message in the chat list: "10:22" for today, "Вчера", or "9 мар" for earlier
 * days (the app is Russian-only, like [russianPluralForm]).
 */
fun formatChatListTime(sentAt: Instant, now: Instant, zone: TimeZone): String {
    val sentDate: LocalDate = sentAt.toLocalDateTime(zone).date
    val today: LocalDate = now.toLocalDateTime(zone).date
    return when (sentDate) {
        today -> formatMessageTime(sentAt, zone)
        today.minus(DatePeriod(days = 1)) -> "Вчера"
        else -> "${sentDate.day} ${RussianShortMonths[sentDate.month.ordinal]}"
    }
}

private fun Int.twoDigits(): String = toString().padStart(2, '0')
