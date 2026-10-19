package org.matchlab.academic_net.feature.projectsearch.domain

import kotlin.test.Test
import kotlin.test.assertEquals

/** The codes of `duration` of `docs/api-values.md`. */
class ProjectDurationTest {
    @Test
    fun theCodesAreTheOnesOfTheContract() {
        assertEquals("1", ProjectDuration.UpTo3Months.code)
        assertEquals("2", ProjectDuration.From3To6Months.code)
        assertEquals("3", ProjectDuration.Over6Months.code)
    }

    @Test
    fun theCodesAreUnique() {
        assertEquals(ProjectDuration.entries.size, ProjectDuration.entries.map { it.code }.toSet().size)
    }
}
