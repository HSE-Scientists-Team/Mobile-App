package org.matchlab.academic_net.feature.collaborators.domain

import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A blank university means "any" ([CollaboratorFilters.university]) and is not sent, so it must not light the dot on the
 * filters button, which the screen draws from [CollaboratorFilters.isEmpty].
 */
class CollaboratorFiltersBlankTest {
    @Test
    fun aBlankUniversityDoesNotCountAsAnActiveFilter() {
        // This one holds: activeCount treats a blank university as off.
        assertEquals(0, CollaboratorFilters(university = "   ").activeCount)
    }

    // BUG found by the tester (not fixed: the tests of pass 1b do not change production code). isEmpty is
    // `this == CollaboratorFilters()`, so a university of spaces (the text field of "08b" lets one in) is "not empty" while
    // activeCount is 0 and toParams() sends nothing: CollaboratorSearchScreen.kt draws `FiltersButton(active = !filters.isEmpty)`
    // and shows the dot for a filter that restricts nothing. Fix: isEmpty = activeCount == 0 (or compare with the university
    // trimmed), then remove @Ignore.
    @Ignore
    @Test
    fun aBlankUniversityIsNoRestrictionForTheDotOfTheFiltersButtonToo() {
        assertTrue(CollaboratorFilters(university = "   ").isEmpty)
    }
}
