package org.matchlab.academic_net.feature.collaborators.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.feature.collaborators.domain.usecase.GetRoleRequirementUseCase
import org.matchlab.academic_net.feature.collaborators.domain.usecase.SearchCollaboratorsUseCase

class SearchCollaboratorsUseCaseTest {
    private val answer = listOf(
        ScoredCollaborator(collaborator("Вера"), 100),
        ScoredCollaborator(collaborator("Борис"), 50),
    )
    private val repository = FakeCollaboratorsRepository(answer)
    private val search = SearchCollaboratorsUseCase(repository)

    @Test
    fun sendsTheQueryAsIsAndKeepsTheServersOrder() = runTest {
        val query = CollaboratorQuery(
            text = "Python, Kotlin",
            filters = CollaboratorFilters(verifiedProfile = true),
            role = RoleReference(ProjectId("p1"), RoleId("r1")),
        )

        val result = search(query).getOrThrow()

        assertEquals(query, repository.lastQuery)
        assertEquals(answer, result)
    }

    @Test
    fun repositoryErrorBecomesFailure() = runTest {
        val error = IllegalStateException("offline")
        repository.failure = error

        assertSame(error, search(CollaboratorQuery()).exceptionOrNull())
    }
}

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
