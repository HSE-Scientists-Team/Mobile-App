package org.matchlab.academic_net.feature.projectsearch.domain

import kotlin.test.Test
import kotlin.test.assertEquals

/** The codes of `duration` of `docs/api-values.md`. */
class ProjectDurationTest {
    @Test
    fun theCodesAreTheOnesOfTheContract() {
        assertEquals("up_to_3_months", ProjectDuration.UpTo3Months.code)
        assertEquals("from_3_to_6_months", ProjectDuration.From3To6Months.code)
        assertEquals("over_6_months", ProjectDuration.Over6Months.code)
    }

    @Test
    fun theCodesAreUnique() {
        assertEquals(ProjectDuration.entries.size, ProjectDuration.entries.map { it.code }.toSet().size)
    }
}
