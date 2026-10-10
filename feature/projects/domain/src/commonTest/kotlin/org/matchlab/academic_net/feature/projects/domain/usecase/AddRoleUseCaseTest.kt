package org.matchlab.academic_net.feature.projects.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.result.SubmitResult
import org.matchlab.academic_net.feature.projects.domain.FakeProjectsRepository
import org.matchlab.academic_net.feature.projects.domain.ProjectNotFoundException
import org.matchlab.academic_net.feature.projects.domain.RoleDraft
import org.matchlab.academic_net.feature.projects.domain.RoleDraftError
import org.matchlab.academic_net.feature.projects.domain.testProject

class AddRoleUseCaseTest {
    private val repository = FakeProjectsRepository(listOf(testProject("p1")))
    private val addRole = AddRoleUseCase(repository)

    @Test
    fun roleIsAddedAsVacant() = runTest {
        val result = addRole(ProjectId("p1"), RoleDraft(" Mobile  Developer ", listOf("Flutter", "flutter")))

        val role = assertIs<SubmitResult.Success<*>>(result).value
        assertEquals(listOf(role), repository.projects.value.single().roles)
        assertEquals(true, repository.projects.value.single().roles.single().isVacant)
        assertEquals("Mobile Developer", repository.projects.value.single().roles.single().title)
        assertEquals(listOf("Flutter"), repository.projects.value.single().roles.single().skills)
    }

    @Test
    fun roleWithoutTitleIsRejected() = runTest {
        val result = addRole(ProjectId("p1"), RoleDraft("  "))

        val invalid = assertIs<SubmitResult.Invalid<RoleDraftError>>(result)
        assertEquals(setOf(RoleDraftError.TitleEmpty), invalid.errors)
        assertEquals(emptyList(), repository.projects.value.single().roles)
    }

    @Test
    fun unknownProjectIsFailure() = runTest {
        val result = addRole(ProjectId("missing"), RoleDraft("Designer"))

        assertIs<ProjectNotFoundException>(assertIs<SubmitResult.Failure>(result).cause)
    }
}
