package org.matchlab.academic_net.feature.projects.domain.usecase

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.result.SubmitResult
import org.matchlab.academic_net.feature.projects.domain.FakeProjectsRepository
import org.matchlab.academic_net.feature.projects.domain.ProjectConflictException
import org.matchlab.academic_net.feature.projects.domain.ProjectLimits
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
    fun aChangedProjectIsAFailureTheScreenCanTellApart() = runTest {
        // The repository has read the project again by now; the use case only reports what happened.
        repository.failure = ProjectConflictException(ProjectId("p1"))

        val result = addRole(ProjectId("p1"), RoleDraft("Designer"))

        assertEquals(ProjectId("p1"), assertIs<ProjectConflictException>(assertIs<SubmitResult.Failure>(result).cause).projectId)
        assertEquals(emptyList(), repository.projects.value.single().roles)
    }

    @Test
    fun aTooLongTitleIsRejectedBeforeAnythingIsSent() = runTest {
        repository.failure = IllegalStateException("must not be called")

        val result = addRole(ProjectId("p1"), RoleDraft("а".repeat(ProjectLimits.MaxRoleTitleLength + 1)))

        assertEquals(setOf(RoleDraftError.TitleTooLong), assertIs<SubmitResult.Invalid<RoleDraftError>>(result).errors)
    }

    @Test
    fun cancellationIsNotAFailure() = runTest {
        repository.failure = CancellationException("cancelled")

        assertFailsWith<CancellationException> { addRole(ProjectId("p1"), RoleDraft("Designer")) }
    }

    @Test
    fun unknownProjectIsFailure() = runTest {
        val result = addRole(ProjectId("missing"), RoleDraft("Designer"))

        assertIs<ProjectNotFoundException>(assertIs<SubmitResult.Failure>(result).cause)
    }
}
