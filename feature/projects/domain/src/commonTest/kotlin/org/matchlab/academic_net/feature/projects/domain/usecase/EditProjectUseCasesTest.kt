package org.matchlab.academic_net.feature.projects.domain.usecase

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.YearMonth
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.Role
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.result.SubmitResult
import org.matchlab.academic_net.feature.projects.domain.FakeProjectsRepository
import org.matchlab.academic_net.feature.projects.domain.ProjectConflictException
import org.matchlab.academic_net.feature.projects.domain.ProjectDraftError
import org.matchlab.academic_net.feature.projects.domain.ProjectInfoDraft
import org.matchlab.academic_net.feature.projects.domain.ProjectNotFoundException
import org.matchlab.academic_net.feature.projects.domain.ProjectsRepository
import org.matchlab.academic_net.feature.projects.domain.RoleDraft
import org.matchlab.academic_net.feature.projects.domain.RoleDraftError
import org.matchlab.academic_net.feature.projects.domain.RoleNotRemovableException
import org.matchlab.academic_net.feature.projects.domain.testProject

class UpdateProjectUseCaseTest {
    private val repository = FakeProjectsRepository(listOf(testProject("p1")))
    private val update = UpdateProjectUseCase(repository)
    private val draft get() = ProjectInfoDraft.of(testProject("p1"))

    @Test
    fun aValidDraftIsSentNormalizedAndTheProjectComesBack() = runTest {
        val result = update(ProjectId("p1"), draft.copy(title = "  New   title ", tags = listOf("A", "a")))

        val project = assertIs<SubmitResult.Success<*>>(result).value
        assertEquals("New title", repository.updatedInfos.single().second.title)
        assertEquals(listOf("A"), repository.updatedInfos.single().second.tags)
        assertEquals(project, repository.projects.value.single())
    }

    @Test
    fun anInvalidDraftSendsNothing() = runTest {
        val result = update(
            ProjectId("p1"),
            draft.copy(problem = "  ", start = YearMonth(2027, 5), end = YearMonth(2027, 1)),
        )

        val invalid = assertIs<SubmitResult.Invalid<ProjectDraftError>>(result)
        assertEquals(setOf(ProjectDraftError.ProblemEmpty, ProjectDraftError.EndBeforeStart), invalid.errors)
        assertEquals(emptyList(), repository.updatedInfos)
    }

    @Test
    fun failuresOfTheRepositoryAreFailuresTheScreenCanTellApart() = runTest {
        repository.failure = ProjectConflictException(ProjectId("p1"))
        assertIs<ProjectConflictException>(assertIs<SubmitResult.Failure>(update(ProjectId("p1"), draft)).cause)

        repository.failure = null
        assertIs<ProjectNotFoundException>(assertIs<SubmitResult.Failure>(update(ProjectId("gone"), draft)).cause)
    }

    @Test
    fun cancellationIsNotAFailure() = runTest {
        repository.failure = CancellationException("cancelled")

        assertFailsWith<CancellationException> { update(ProjectId("p1"), draft) }
    }
}

class UpdateRoleUseCaseTest {
    private val repository = FakeProjectsRepository(
        listOf(testProject("p1", roles = listOf(Role(RoleId("r1"), "Dev", listOf("Kotlin"))))),
    )
    private val update = UpdateRoleUseCase(repository)

    @Test
    fun aValidRoleIsSentNormalized() = runTest {
        val result = update(ProjectId("p1"), RoleId("r1"), RoleDraft(" Mobile  Dev ", listOf("Swift", "swift")))

        val role = assertIs<SubmitResult.Success<*>>(result).value as Role
        assertEquals("Mobile Dev", role.title)
        assertEquals(listOf("Swift"), role.skills)
        assertEquals(RoleDraft("Mobile Dev", listOf("Swift")), repository.updatedRoles.single().third)
    }

    @Test
    fun aRoleWithoutTitleSendsNothing() = runTest {
        val result = update(ProjectId("p1"), RoleId("r1"), RoleDraft("  "))

        assertEquals(setOf(RoleDraftError.TitleEmpty), assertIs<SubmitResult.Invalid<RoleDraftError>>(result).errors)
        assertEquals(emptyList(), repository.updatedRoles)
    }

    @Test
    fun aMissingRoleIsAConflictFailure() = runTest {
        val result = update(ProjectId("p1"), RoleId("nope"), RoleDraft("Dev"))

        assertIs<ProjectConflictException>(assertIs<SubmitResult.Failure>(result).cause)
    }

    @Test
    fun cancellationIsNotAFailure() = runTest {
        repository.failure = CancellationException("cancelled")

        assertFailsWith<CancellationException> { update(ProjectId("p1"), RoleId("r1"), RoleDraft("Dev")) }
    }
}

class RemoveRoleUseCaseTest {
    private val free = Role(RoleId("free"), "Free", occupied = 0)
    private val taken = Role(RoleId("taken"), "Taken", occupied = 1)
    private val repository = FakeProjectsRepository(listOf(testProject("p1", roles = listOf(free, taken))))
    private val remove = RemoveRoleUseCase(repository)

    @Test
    fun aFreeRoleIsRemoved() = runTest {
        val result = remove(ProjectId("p1"), RoleId("free"))

        assertEquals(listOf(taken), assertIs<RemoveRoleResult.Removed>(result).project.roles)
        assertEquals(listOf(ProjectId("p1") to RoleId("free")), repository.removedRoles)
    }

    @Test
    fun aRoleWithTakenPlacesIsNotRemovableAndNothingIsSent() = runTest {
        assertEquals(RemoveRoleResult.NotRemovable, remove(ProjectId("p1"), RoleId("taken")))
        assertEquals(emptyList(), repository.removedRoles)
    }

    @Test
    fun aRoleThatIsNotInTheProjectCountsAsRemovedWithoutARequest() = runTest {
        val result = remove(ProjectId("p1"), RoleId("ghost"))

        assertEquals(2, assertIs<RemoveRoleResult.Removed>(result).project.roles.size)
        assertEquals(emptyList(), repository.removedRoles)
    }

    @Test
    fun anUnknownProjectIsNotFoundWithoutARequest() = runTest {
        assertEquals(RemoveRoleResult.NotFound, remove(ProjectId("gone"), RoleId("free")))
        assertEquals(emptyList(), repository.removedRoles)
    }

    @Test
    fun aFailureOfReadingTheProjectIsAFailure() = runTest {
        val error = IllegalStateException("offline")
        repository.failure = error

        assertSame(error, assertIs<RemoveRoleResult.Failure>(remove(ProjectId("p1"), RoleId("free"))).cause)
    }

    @Test
    fun repositoryOutcomesMapToResults() = runTest {
        fun failingWith(error: (ProjectId, RoleId) -> Throwable): ProjectsRepository =
            object : ProjectsRepository by repository {
                override suspend fun removeRole(projectId: ProjectId, roleId: RoleId) =
                    throw error(projectId, roleId)
            }

        assertEquals(
            RemoveRoleResult.NotRemovable,
            RemoveRoleUseCase(failingWith { p, r -> RoleNotRemovableException(p, r) })(ProjectId("p1"), RoleId("free")),
        )
        assertEquals(
            RemoveRoleResult.NotFound,
            RemoveRoleUseCase(failingWith { p, _ -> ProjectNotFoundException(p) })(ProjectId("p1"), RoleId("free")),
        )
        val other = IllegalStateException("500")
        assertSame(
            other,
            assertIs<RemoveRoleResult.Failure>(
                RemoveRoleUseCase(failingWith { _, _ -> other })(ProjectId("p1"), RoleId("free")),
            ).cause,
        )
    }

    @Test
    fun cancellationIsNotAFailure() = runTest {
        repository.failure = CancellationException("cancelled")

        assertFailsWith<CancellationException> { remove(ProjectId("p1"), RoleId("free")) }
    }
}
