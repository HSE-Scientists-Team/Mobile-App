package org.matchlab.academic_net.utils

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

class ElapsedDaysTest {
    private val utc = TimeZone.UTC
    private val plus3 = TimeZone.of("Etc/GMT-3")

    private fun at(zone: TimeZone, y: Int, m: Int, d: Int, h: Int, min: Int) =
        LocalDateTime(y, m, d, h, min).toInstant(zone)

    @Test
    fun theSameDayIsZeroWhateverTheTimeOfDay() {
        assertEquals(0, elapsedDays(at(utc, 2026, 10, 19, 0, 0), at(utc, 2026, 10, 19, 23, 59), utc))
        assertEquals(0, elapsedDays(at(utc, 2026, 10, 19, 10, 0), at(utc, 2026, 10, 19, 10, 0), utc))
    }

    @Test
    fun theDayAfterIsOneEvenAFewMinutesLater() {
        assertEquals(1, elapsedDays(at(utc, 2026, 10, 18, 23, 50), at(utc, 2026, 10, 19, 0, 10), utc))
    }

    @Test
    fun almostTwentyFourHoursOnTheSameDayIsStillZero() {
        assertEquals(0, elapsedDays(at(utc, 2026, 10, 19, 0, 1), at(utc, 2026, 10, 19, 23, 59), utc))
    }

    @Test
    fun theMidnightIsTakenInTheGivenZone() {
        val since = at(utc, 2026, 10, 18, 22, 0) // 01:00 on the 19th in +3
        val now = at(utc, 2026, 10, 19, 1, 0) // 04:00 on the 19th in +3

        assertEquals(1, elapsedDays(since, now, utc))
        assertEquals(0, elapsedDays(since, now, plus3))
    }

    @Test
    fun severalDaysCrossTheMonthAndYearBorders() {
        assertEquals(5, elapsedDays(at(utc, 2026, 10, 29, 12, 0), at(utc, 2026, 11, 3, 8, 0), utc))
        assertEquals(1, elapsedDays(at(utc, 2026, 12, 31, 23, 0), at(utc, 2027, 1, 1, 1, 0), utc))
        assertEquals(365, elapsedDays(LocalDate(2026, 1, 1).atStartOfDayIn(utc), LocalDate(2027, 1, 1).atStartOfDayIn(utc), utc))
    }

    @Test
    fun aMomentInTheFutureIsZero() {
        assertEquals(0, elapsedDays(at(utc, 2026, 10, 25, 12, 0), at(utc, 2026, 10, 19, 12, 0), utc))
        assertEquals(0, elapsedDays(at(utc, 2026, 10, 19, 12, 5), at(utc, 2026, 10, 19, 12, 0), utc))
    }
}
