package org.matchlab.academic_net.feature.auth.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class CountdownTest {
    @Test
    fun waitIsRoundedUpToWholeSeconds() {
        assertEquals(1, 1.seconds.wholeSecondsRoundedUp())
        assertEquals(2, (1.seconds + 100.milliseconds).wholeSecondsRoundedUp())
        assertEquals(60, 60.seconds.wholeSecondsRoundedUp())
        assertEquals(1, 1.milliseconds.wholeSecondsRoundedUp())
        assertEquals(1, 999.milliseconds.wholeSecondsRoundedUp())
        assertEquals(59, (58.seconds + 1.milliseconds).wholeSecondsRoundedUp())
    }

    @Test
    fun aWaitThatIsOverIsZero() {
        assertEquals(0, Duration.ZERO.wholeSecondsRoundedUp())
        assertEquals(0, (-5).seconds.wholeSecondsRoundedUp())
    }

    @Test
    fun aWaitTooLongForAnIntIsTheLargestInt() {
        assertEquals(Int.MAX_VALUE, Duration.INFINITE.wholeSecondsRoundedUp())
        assertEquals(Int.MAX_VALUE, (Int.MAX_VALUE.toLong() * 2).seconds.wholeSecondsRoundedUp())
    }

    @Test
    fun textIsMinutesAndTwoDigitSeconds() {
        assertEquals("1:00", countdownText(60))
        assertEquals("0:45", countdownText(45))
        assertEquals("0:09", countdownText(9))
        assertEquals("0:00", countdownText(0))
        assertEquals("12:30", countdownText(750))
        assertEquals("1:01", countdownText(61))
    }

    @Test
    fun aNegativeTimeIsZero() {
        assertEquals("0:00", countdownText(-1))
        assertEquals("0:00", countdownText(Int.MIN_VALUE))
    }
}
