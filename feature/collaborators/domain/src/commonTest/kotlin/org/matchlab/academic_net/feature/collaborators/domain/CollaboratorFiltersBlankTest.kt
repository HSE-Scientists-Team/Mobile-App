package org.matchlab.academic_net.feature.collaborators.domain

import org.matchlab.academic_net.common.model.ProjectType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A blank university means "any" ([CollaboratorFilters.university]) and is not sent, so it must not light the dot on the
 * filters button, which the screen draws from [CollaboratorFilters.isEmpty]: the dot and [CollaboratorFilters.activeCount]
 * tell the same thing.
 */
class CollaboratorFiltersBlankTest {
    @Test
    fun aBlankUniversityDoesNotCountAsAnActiveFilter() {
        assertEquals(0, CollaboratorFilters(university = "   ").activeCount)
    }

    @Test
    fun aBlankUniversityIsNoRestrictionForTheDotOfTheFiltersButtonToo() {
        assertTrue(CollaboratorFilters(university = "   ").isEmpty)
        assertTrue(CollaboratorFilters(university = "\t \n").isEmpty)
        assertTrue(CollaboratorFilters(university = "").isEmpty)
    }

    @Test
    fun aRealUniversityLightsTheDotAndSoDoesAnyOtherFilter() {
        assertFalse(CollaboratorFilters(university = " НИУ ВШЭ ").isEmpty)
        assertFalse(CollaboratorFilters(verifiedProfile = true).isEmpty)
        assertFalse(CollaboratorFilters(hasVerifiedProjects = true).isEmpty)
        assertFalse(CollaboratorFilters(hasPublications = true).isEmpty)
        assertFalse(CollaboratorFilters(workTypes = setOf(ProjectType.Scientific)).isEmpty)
        assertFalse(CollaboratorFilters(keywords = listOf("Kotlin")).isEmpty)
    }

    @Test
    fun theDotIsOnAsSoonAsOneRealFilterIsSetEvenNextToABlankUniversity() {
        val filters = CollaboratorFilters(university = "  ", hasPublications = true)

        assertFalse(filters.isEmpty)
        assertEquals(1, filters.activeCount, "the blank university adds nothing to the count")
    }

    @Test
    fun openToCollaborationCountsAsAnActiveFilterAndLightsTheDot() {
        val filters = CollaboratorFilters(openOnly = true)

        assertEquals(1, filters.activeCount)
        assertFalse(filters.isEmpty)
        assertEquals(2, filters.copy(verifiedProfile = true).activeCount)
    }

    @Test
    fun resettingTheFiltersTurnsOpenOnlyOff() {
        val reset = CollaboratorFilters(openOnly = true, verifiedProfile = true).copy(openOnly = false, verifiedProfile = false)

        assertTrue(reset.isEmpty)
        assertEquals(CollaboratorFilters(), reset)
    }

    @Test
    fun theDotAndTheCountAlwaysAgree() {
        val samples = listOf(
            CollaboratorFilters(),
            CollaboratorFilters(university = " "),
            CollaboratorFilters(university = "МГУ"),
            CollaboratorFilters(openOnly = true),
            CollaboratorFilters(verifiedProfile = true, university = "  "),
            CollaboratorFilters(keywords = listOf("a", "b"), workTypes = setOf(ProjectType.Practical)),
        )

        for (filters in samples) {
            assertEquals(filters.activeCount == 0, filters.isEmpty, "$filters")
        }
    }
}
