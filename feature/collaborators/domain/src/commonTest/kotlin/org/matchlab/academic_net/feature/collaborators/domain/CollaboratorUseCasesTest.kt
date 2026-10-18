package org.matchlab.academic_net.feature.collaborators.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.feature.collaborators.domain.usecase.GetRoleRequirementUseCase

class GetRoleRequirementUseCaseTest {
    private val provider = FakeRoleRequirementProvider()
    private val get = GetRoleRequirementUseCase(provider)

    @Test
    fun returnsTheRequirement() = runTest {
        val requirement = RoleRequirement("AI-навигация", "ML Engineer", listOf("Python"))
        provider.requirement = requirement

        assertEquals(requirement, get(ProjectId("p1"), RoleId("r1")).getOrThrow())
    }

    @Test
    fun missingRoleIsAFailure() = runTest {
        assertIs<RoleNotFoundException>(get(ProjectId("p1"), RoleId("r1")).exceptionOrNull())
    }
}

class CollaboratorFiltersTest {
    @Test
    fun noFiltersMeansEmpty() {
        assertTrue(CollaboratorFilters().isEmpty)
        assertEquals(0, CollaboratorFilters().activeCount)
    }

    @Test
    fun activeFiltersAreCounted() {
        val filters = CollaboratorFilters(
            verifiedProfile = true,
            workTypes = setOf(ProjectType.Practical, ProjectType.Scientific),
            keywords = listOf("python"),
        )

        assertFalse(filters.isEmpty)
        assertEquals(3, filters.activeCount)
    }

    @Test
    fun blankUniversityDoesNotCount() {
        assertEquals(0, CollaboratorFilters(university = "  ").activeCount)
        assertEquals(1, CollaboratorFilters(university = "МГУ").activeCount)
    }
}
