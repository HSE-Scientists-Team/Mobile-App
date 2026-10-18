package org.matchlab.academic_net.components.interaction

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Lets the first click of a burst through and drops the ones that follow it within [window].
 *
 * A tap that starts something slow (a screen sliding in, a request on its way) leaves the element
 * that was tapped on the screen and tappable. A second tap, or the second half of a double tap,
 * would start the same thing again: two screens on the back stack, two projects created. A
 * debouncer in front of the click handler makes the second one a no-op.
 *
 * The window runs from the last click that was *accepted*. Dropped clicks do not extend it, so a
 * finger that keeps tapping still gets one click through every [window].
 *
 * Time comes from a monotonic [TimeSource]: setting the clock of the device cannot open the window
 * early or keep it shut. Tests pass a `TestTimeSource` and move it by hand.
 *
 * Not thread-safe: clicks are delivered on the main thread, and every clickable element has a
 * debouncer of its own (see [rememberOnceClick]).
 *
 * @param window how long after an accepted click the next ones are dropped
 * @param timeSource where the time comes from
 */
class ClickDebouncer(
    val window: Duration = DefaultWindow,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    init {
        require(!window.isNegative()) { "The window must not be negative: $window" }
    }

    private var lastAccepted: TimeMark? = null

    /**
     * Asks whether a click that happens now goes through. `true` also starts the window; `false`
     * leaves it as it was. Call it once per click.
     */
    fun tryAccept(): Boolean {
        val last = lastAccepted
        if (last != null && last.elapsedNow() < window) return false
        lastAccepted = timeSource.markNow()
        return true
    }

    companion object {
        /**
         * Long enough to cover a screen transition (300 ms) and the second tap of a double tap,
         * short enough not to be felt by someone who really means to tap twice.
         */
        val DefaultWindow: Duration = 500.milliseconds
    }
}
