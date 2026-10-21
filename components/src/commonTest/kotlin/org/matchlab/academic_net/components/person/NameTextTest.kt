package org.matchlab.academic_net.components.person

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What counts as a name that [nameOrPlaceholder] shows as it is: anything that is not blank. */
class NameTextTest {
    @Test
    fun aNameIsAName() {
        assertTrue(hasName("Арджун Мехта"))
        assertTrue(hasName("Elena"))
        assertTrue(hasName("Й"))
    }

    @Test
    fun anEmptyNameHasNoName() {
        assertFalse(hasName(""))
    }

    @Test
    fun aNameOfSpacesOnlyHasNoName() {
        assertFalse(hasName(" "))
        assertFalse(hasName("     "))
        assertFalse(hasName("\t"))
        assertFalse(hasName("\n"))
        assertFalse(hasName(" \t\r\n "))
    }

    @Test
    fun aNonBreakingSpaceIsNotAName() {
        assertFalse(hasName(" "))
        assertFalse(hasName("   "))
    }

    @Test
    fun spacesAroundAWordDoNotTakeTheNameAway() {
        assertTrue(hasName("  Арджун "))
    }

    @Test
    fun aDotOrADashIsAName() {
        // The server's text is shown as it is: only blank is replaced.
        assertTrue(hasName("."))
        assertTrue(hasName("-"))
    }
}
