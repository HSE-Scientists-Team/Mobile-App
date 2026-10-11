package org.matchlab.academic_net.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchTextTest {
    @Test
    fun searchTermsSplitsByCommasAndTrims() {
        assertEquals(listOf("Python", "machine learning"), searchTerms(" Python ,machine learning"))
    }

    @Test
    fun searchTermsDropsBlankTerms() {
        assertEquals(listOf("a", "b"), searchTerms(",, a , ,b,"))
    }

    @Test
    fun searchTermsOfBlankTextIsEmpty() {
        assertEquals(emptyList(), searchTerms(""))
        assertEquals(emptyList(), searchTerms("  , ,  "))
    }

    @Test
    fun searchTermsKeepsSpacesInsideATerm() {
        assertEquals(listOf("computer vision"), searchTerms("computer vision"))
    }

    @Test
    fun occursInIgnoresCaseOfTheTerm() {
        assertTrue("PYTHON".occursIn("python, pytorch"))
        assertTrue("Torch".occursIn("python, pytorch"))
    }

    @Test
    fun occursInTrimsTheTerm() {
        assertTrue("  python ".occursIn("python"))
    }

    @Test
    fun occursInIsFalseForMissingTerm() {
        assertFalse("kotlin".occursIn("python, pytorch"))
    }

    @Test
    fun occursInIsFalseForBlankTerm() {
        assertFalse("".occursIn("python"))
        assertFalse("   ".occursIn("python"))
    }

    @Test
    fun occursInDoesNotLowercaseTheHaystack() {
        // The caller lowercases the haystack once; an uppercase haystack is not matched.
        assertFalse("python".occursIn("PYTHON"))
    }

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
