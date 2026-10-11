package org.matchlab.academic_net.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

class ChatTimeTest {
    private val zone = TimeZone.UTC
    private val now = Instant.parse("2026-10-10T12:30:00Z")

    @Test
    fun messageTimeIsPaddedHoursAndMinutes() {
        assertEquals("09:05", formatMessageTime(Instant.parse("2026-10-10T09:05:00Z"), zone))
        assertEquals("23:59", formatMessageTime(Instant.parse("2026-10-10T23:59:59Z"), zone))
    }

    @Test
    fun listShowsTimeForToday() {
        assertEquals("10:22", formatChatListTime(Instant.parse("2026-10-10T10:22:00Z"), now, zone))
    }

    @Test
    fun listShowsYesterdayEvenJustAfterMidnight() {
        val justAfterMidnight = Instant.parse("2026-10-10T00:10:00Z")
        val lateYesterday = Instant.parse("2026-10-09T23:50:00Z")

        assertEquals("Вчера", formatChatListTime(lateYesterday, justAfterMidnight, zone))
    }

    @Test
    fun listShowsDayAndShortMonthForOlderMessages() {
        assertEquals("9 мар", formatChatListTime(Instant.parse("2026-03-09T10:00:00Z"), now, zone))
        assertEquals("1 янв", formatChatListTime(Instant.parse("2026-01-01T10:00:00Z"), now, zone))
        assertEquals("31 дек", formatChatListTime(Instant.parse("2025-12-31T10:00:00Z"), now, zone))
    }

    @Test
    fun timeZoneDecidesTheDay() {
        val moscow = TimeZone.of("Europe/Moscow")
        // 22:30 UTC on the 9th is already 01:30 on the 10th in Moscow.
        val sent = Instant.parse("2026-10-09T22:30:00Z")

        assertEquals("01:30", formatChatListTime(sent, now, moscow))
        assertEquals("Вчера", formatChatListTime(sent, now, zone))
    }

    @Test
    fun chatListTimeIsTodayWithTimeOfDay() {
        assertEquals(
            ChatListTime.Today("10:22"),
            chatListTime(Instant.parse("2026-10-10T10:22:00Z"), now, zone),
        )
        assertEquals(
            ChatListTime.Today("09:05"),
            chatListTime(Instant.parse("2026-10-10T09:05:00Z"), now, zone),
        )
    }

    @Test
    fun chatListTimeIsYesterdayForThePreviousCalendarDay() {
        assertEquals(
            ChatListTime.Yesterday,
            chatListTime(Instant.parse("2026-10-09T08:00:00Z"), now, zone),
        )
    }

    @Test
    fun chatListTimeIsEarlierWithDateForOlderMessages() {
        assertEquals(
            ChatListTime.Earlier(LocalDate(2026, 3, 9)),
            chatListTime(Instant.parse("2026-03-09T10:00:00Z"), now, zone),
        )
        assertEquals(
            ChatListTime.Earlier(LocalDate(2025, 12, 31)),
            chatListTime(Instant.parse("2025-12-31T10:00:00Z"), now, zone),
        )
        // Two days back is already "earlier", not "yesterday".
        assertEquals(
            ChatListTime.Earlier(LocalDate(2026, 10, 8)),
            chatListTime(Instant.parse("2026-10-08T23:59:00Z"), now, zone),
        )
    }

    @Test
    fun chatListTimeMidnightBoundaryIsByCalendarDayNotBy24Hours() {
        val justAfterMidnight = Instant.parse("2026-10-10T00:10:00Z")

        // 20 minutes earlier, but on the previous calendar day.
        assertEquals(
            ChatListTime.Yesterday,
            chatListTime(Instant.parse("2026-10-09T23:50:00Z"), justAfterMidnight, zone),
        )
        // Exactly midnight already belongs to the new day.
        assertEquals(
            ChatListTime.Today("00:00"),
            chatListTime(Instant.parse("2026-10-10T00:00:00Z"), justAfterMidnight, zone),
        )
        // The very last moment before the next midnight is still today.
        assertEquals(
            ChatListTime.Today("23:59"),
            chatListTime(
                Instant.parse("2026-10-10T23:59:59Z"),
                Instant.parse("2026-10-10T23:59:59Z"),
                zone,
            ),
        )
    }

    @Test
    fun chatListTimeTimeZoneDecidesTheDay() {
        val moscow = TimeZone.of("Europe/Moscow")
        val newYork = TimeZone.of("America/New_York")
        // 22:30 UTC on the 9th is 01:30 on the 10th in Moscow and 18:30 on the 9th in New York.
        val sent = Instant.parse("2026-10-09T22:30:00Z")

        assertEquals(ChatListTime.Today("01:30"), chatListTime(sent, now, moscow))
        assertEquals(ChatListTime.Yesterday, chatListTime(sent, now, zone))
        assertEquals(ChatListTime.Yesterday, chatListTime(sent, now, newYork))
    }

    @Test
    fun chatListTimeEarlierDateIsTakenInTheRequestedZone() {
        val moscow = TimeZone.of("Europe/Moscow")
        // 22:30 UTC on 2026-03-08 is already 2026-03-09 in Moscow.
        val sent = Instant.parse("2026-03-08T22:30:00Z")

        assertEquals(ChatListTime.Earlier(LocalDate(2026, 3, 8)), chatListTime(sent, now, zone))
        assertEquals(ChatListTime.Earlier(LocalDate(2026, 3, 9)), chatListTime(sent, now, moscow))
    }

    @Test
    fun chatListTimeMessageFromTheFutureIsTodayOnTheSameDayOtherwiseEarlier() {
        // Later the same day: still shown by its time of day.
        assertEquals(
            ChatListTime.Today("18:00"),
            chatListTime(Instant.parse("2026-10-10T18:00:00Z"), now, zone),
        )
        // On a later day it matches neither today nor yesterday, so it is reported by its date.
        assertEquals(
            ChatListTime.Earlier(LocalDate(2026, 10, 11)),
            chatListTime(Instant.parse("2026-10-11T09:00:00Z"), now, zone),
        )
    }
}
