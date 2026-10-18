package org.matchlab.academic_net.components.interaction

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class ClickDebouncerTest {
    private val time = TestTimeSource()

    private fun debouncer(window: Duration = 500.milliseconds) = ClickDebouncer(window, time)

    @Test
    fun theFirstClickGoesThrough() {
        assertTrue(debouncer().tryAccept())
    }

    @Test
    fun aClickInsideTheWindowIsDropped() {
        val debouncer = debouncer()
        assertTrue(debouncer.tryAccept())

        time += 100.milliseconds

        assertFalse(debouncer.tryAccept())
    }

    @Test
    fun everyClickOfAFastBurstAfterTheFirstIsDropped() {
        val debouncer = debouncer()
        val accepted = (1..5).count {
            val accepted = debouncer.tryAccept()
            time += 100.milliseconds
            accepted
        }

        assertEquals(1, accepted)
    }

    @Test
    fun aClickAfterTheWindowGoesThrough() {
        val debouncer = debouncer()
        debouncer.tryAccept()

        time += 501.milliseconds

        assertTrue(debouncer.tryAccept())
    }

    @Test
    fun aClickExactlyAtTheEndOfTheWindowGoesThrough() {
        val debouncer = debouncer()
        debouncer.tryAccept()

        time += 500.milliseconds

        assertTrue(debouncer.tryAccept())
    }

    @Test
    fun aClickJustBeforeTheEndOfTheWindowIsDropped() {
        val debouncer = debouncer()
        debouncer.tryAccept()

        time += 499.milliseconds

        assertFalse(debouncer.tryAccept())
    }

    @Test
    fun droppedClicksDoNotExtendTheWindow() {
        val debouncer = debouncer()
        assertTrue(debouncer.tryAccept())

        // A finger that keeps tapping every 300 ms still gets a click through every window.
        time += 300.milliseconds
        assertFalse(debouncer.tryAccept())
        time += 300.milliseconds
        assertTrue(debouncer.tryAccept())
    }

    @Test
    fun anAcceptedClickStartsANewWindow() {
        val debouncer = debouncer()
        assertTrue(debouncer.tryAccept())
        time += 1.seconds
        assertTrue(debouncer.tryAccept())

        time += 100.milliseconds

        assertFalse(debouncer.tryAccept())
    }

    @Test
    fun theWindowIsMeasuredOnTheGivenTimeSourceNotOnTheClockOfTheTest() {
        val other = TestTimeSource()
        val debouncer = ClickDebouncer(500.milliseconds, other)
        debouncer.tryAccept()

        // Moving some other clock does not open the window.
        time += 10.seconds

        assertFalse(debouncer.tryAccept())
        other += 500.milliseconds
        assertTrue(debouncer.tryAccept())
    }

    @Test
    fun aCustomWindowIsRespected() {
        val debouncer = debouncer(window = 2.seconds)
        debouncer.tryAccept()

        time += 1999.milliseconds
        assertFalse(debouncer.tryAccept())
        time += 1.milliseconds
        assertTrue(debouncer.tryAccept())
    }

    @Test
    fun aZeroWindowDropsNothing() {
        val debouncer = debouncer(window = Duration.ZERO)

        assertTrue(debouncer.tryAccept())
        assertTrue(debouncer.tryAccept())
        assertTrue(debouncer.tryAccept())
    }

    @Test
    fun aNegativeWindowIsRefused() {
        assertFailsWith<IllegalArgumentException> { debouncer(window = (-1).milliseconds) }
    }

    @Test
    fun debouncersDoNotShareTheirWindows() {
        val first = debouncer()
        val second = debouncer()

        assertTrue(first.tryAccept())
        assertTrue(second.tryAccept())
        assertFalse(first.tryAccept())
        assertFalse(second.tryAccept())
    }

    @Test
    fun theDefaultWindowIsHalfASecond() {
        assertEquals(500.milliseconds, ClickDebouncer().window)
        assertEquals(500.milliseconds, ClickDebouncer.DefaultWindow)
    }

    @Test
    fun theRealClockLetsTheFirstClickThroughAndDropsTheImmediateSecond() {
        // The default time source is the monotonic one: two calls in a row are well inside 500 ms.
        val debouncer = ClickDebouncer()

        assertTrue(debouncer.tryAccept())
        assertFalse(debouncer.tryAccept())
    }
}
