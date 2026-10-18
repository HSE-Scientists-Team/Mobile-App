package org.matchlab.academic_net.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class SetsTest {
    @Test
    fun toggledAddsItemWhenOn() {
        assertEquals(setOf(1, 2), setOf(1).toggled(2, on = true))
    }

    @Test
    fun toggledRemovesItemWhenOff() {
        assertEquals(setOf(1), setOf(1, 2).toggled(2, on = false))
    }

    @Test
    fun toggledIsIdempotent() {
        assertEquals(setOf(1, 2), setOf(1, 2).toggled(2, on = true))
        assertEquals(setOf(1), setOf(1).toggled(2, on = false))
    }

    @Test
    fun toggledDoesNotChangeTheOriginalSet() {
        val original = setOf("a")
        original.toggled("b", on = true)
        assertEquals(setOf("a"), original)
    }
}
