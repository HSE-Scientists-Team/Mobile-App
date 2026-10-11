package org.matchlab.academic_net.components.person

import kotlin.test.Test
import kotlin.test.assertEquals

class PersonStatsTest {
    @Test
    fun roundsToOneDigit() {
        assertEquals("4.9", formatRating(4.85))
        assertEquals("4.8", formatRating(4.84))
        assertEquals("3.0", formatRating(3.0))
    }

    @Test
    fun keepsTheTrailingZeroOfTheBounds() {
        assertEquals("5.0", formatRating(5.0))
        assertEquals("0.0", formatRating(0.0))
    }

    @Test
    fun cutsValuesOutsideTheScale() {
        assertEquals("5.0", formatRating(7.3))
        assertEquals("0.0", formatRating(-1.0))
    }

    @Test
    fun usesAPointNotAComma() {
        assertEquals("4.1", formatRating(4.1))
    }
}
