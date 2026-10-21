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
 * Which day the last message of a chat belongs to, relative to "now".
 *
 * It carries no localized words: the UI maps each case to text with its own resources, so the
 * label follows the app language instead of being baked into ViewModel state.
 */
sealed interface ChatListTime {
    /**
     * Sent on the current day.
     *
     * @property time time of day in 24-hour format, locale-neutral ("10:22").
     */
    data class Today(val time: String) : ChatListTime

    /** Sent on the calendar day before the current one. */
    data object Yesterday : ChatListTime

    /**
     * Sent two or more days ago. A message dated after the current day ("from the future", e.g.
     * because of clock skew) is also reported as [Earlier], as its date is the most honest label.
     *
     * @property date calendar date of the message in the requested time zone.
     */
    data class Earlier(val date: LocalDate) : ChatListTime
}

/**
 * Classifies the last message of a chat for the chat list.
 *
 * Days are compared as calendar dates in [zone], not as 24-hour distances: 23:50 yesterday is
 * [ChatListTime.Yesterday] even at 00:10 today.
 *
 * @param sentAt when the message was sent.
 * @param now the current moment.
 * @param zone time zone that defines where the day boundaries lie.
 */
fun chatListTime(sentAt: Instant, now: Instant, zone: TimeZone): ChatListTime {
    val sentDate: LocalDate = sentAt.toLocalDateTime(zone).date
    val today: LocalDate = now.toLocalDateTime(zone).date
    return when (sentDate) {
        today -> ChatListTime.Today(formatMessageTime(sentAt, zone))
        today.minus(DatePeriod(days = 1)) -> ChatListTime.Yesterday
        else -> ChatListTime.Earlier(sentDate)
    }
}

private fun Int.twoDigits(): String = toString().padStart(2, '0')
