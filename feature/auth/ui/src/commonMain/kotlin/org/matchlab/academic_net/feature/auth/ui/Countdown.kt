package org.matchlab.academic_net.feature.auth.ui

import kotlin.time.Duration

/**
 * A wait as whole seconds, rounded up: "через 1 с" is better than "через 0 с" with a refusal. A wait that is over
 * (zero or less) is `0`, and one that does not fit an `Int` is the largest `Int`.
 */
internal fun Duration.wholeSecondsRoundedUp(): Int {
    val millis = inWholeMilliseconds
    if (millis <= 0) return 0
    // Not (millis + 999) / 1000: an infinite wait is Long.MAX_VALUE milliseconds, and that would overflow.
    val seconds = millis / 1000 + if (millis % 1000 > 0) 1 else 0
    return seconds.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

/** "1:05": the time to wait as minutes and two-digit seconds; a negative time is "0:00". */
internal fun countdownText(seconds: Int): String {
    val total = seconds.coerceAtLeast(0)
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}
