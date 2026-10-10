package org.matchlab.academic_net.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
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
}
