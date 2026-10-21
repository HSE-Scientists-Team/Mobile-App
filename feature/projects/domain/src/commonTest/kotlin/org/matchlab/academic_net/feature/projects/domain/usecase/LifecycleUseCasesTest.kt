package org.matchlab.academic_net.feature.projects.domain.usecase

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.feature.projects.domain.FakeProjectsRepository
import org.matchlab.academic_net.feature.projects.domain.ProjectConflictException
import org.matchlab.academic_net.feature.projects.domain.ProjectLifecycleAction
import org.matchlab.academic_net.feature.projects.domain.ProjectNotFoundException
import org.matchlab.academic_net.feature.projects.domain.testProject

class ChangeProjectLifecycleUseCaseTest {
    private val repository = FakeProjectsRepository(
        listOf(
            testProject("recruiting", ProjectStatus.Recruiting),
            testProject("started", ProjectStatus.Started),
            testProject("finished", ProjectStatus.Finished),
            testProject("cancelled", ProjectStatus.Cancelled),
            testProject("unknown", status = null),
        ),
    )
    private val change = ChangeProjectLifecycleUseCase(repository)

    @Test
    fun anOfferedActionMovesTheProject() = runTest {
        val result = change(ProjectId("recruiting"), ProjectLifecycleAction.Start)

        assertEquals(ProjectStatus.Started, assertIs<ChangeLifecycleResult.Changed>(result).project.status)
        assertEquals(listOf(ProjectId("recruiting") to ProjectLifecycleAction.Start), repository.lifecycleCalls)
    }

    @Test
    fun anActionNotOfferedForTheStatusSendsNothing() = runTest {
        assertEquals(ChangeLifecycleResult.NotAllowed, change(ProjectId("recruiting"), ProjectLifecycleAction.Complete))
        assertEquals(ChangeLifecycleResult.NotAllowed, change(ProjectId("started"), ProjectLifecycleAction.Start))
        assertEquals(ChangeLifecycleResult.NotAllowed, change(ProjectId("finished"), ProjectLifecycleAction.Cancel))
        assertEquals(ChangeLifecycleResult.NotAllowed, change(ProjectId("cancelled"), ProjectLifecycleAction.Start))

        assertEquals(emptyList(), repository.lifecycleCalls)
    }

    @Test
    fun anUnknownStatusOffersEverything() = runTest {
        assertIs<ChangeLifecycleResult.Changed>(change(ProjectId("unknown"), ProjectLifecycleAction.Complete))
    }

    @Test
    fun noProjectInTheCacheIsNotFoundWithoutARequest() = runTest {
        assertEquals(ChangeLifecycleResult.NotFound, change(ProjectId("missing"), ProjectLifecycleAction.Start))
        assertEquals(emptyList(), repository.lifecycleCalls)
    }

    @Test
    fun aFailureOfReadingTheProjectIsAFailureWithoutARequest() = runTest {
        val error = IllegalStateException("offline")
        repository.failure = error

        assertSame(error, assertIs<ChangeLifecycleResult.Failure>(change(ProjectId("recruiting"), ProjectLifecycleAction.Start)).cause)
        assertEquals(emptyList(), repository.lifecycleCalls)
    }

    @Test
    fun theRepositoryOutcomesMapToResults() = runTest {
        // The fake reads the project first (getProject) and then moves it; both are told to fail by the same field, so
        // the exception is raised by a repository whose cache answers: use the next two to hit the second step.
        val gone = FakeProjectsRepository(listOf(testProject("p", ProjectStatus.Recruiting)))
        val throwing = object : FailingMove(gone) {
            override fun fail(id: ProjectId): Throwable = ProjectNotFoundException(id)
        }
        assertEquals(ChangeLifecycleResult.NotFound, ChangeProjectLifecycleUseCase(throwing)(ProjectId("p"), ProjectLifecycleAction.Start))

        val conflict = object : FailingMove(gone) {
            override fun fail(id: ProjectId): Throwable = ProjectConflictException(id)
        }
        assertEquals(ChangeLifecycleResult.NotAllowed, ChangeProjectLifecycleUseCase(conflict)(ProjectId("p"), ProjectLifecycleAction.Start))

        val other = IllegalStateException("500")
        val generic = object : FailingMove(gone) {
            override fun fail(id: ProjectId): Throwable = other
        }
        val result = ChangeProjectLifecycleUseCase(generic)(ProjectId("p"), ProjectLifecycleAction.Start)
        assertSame(other, assertIs<ChangeLifecycleResult.Failure>(result).cause)
    }

    @Test
    fun cancellationIsNotAFailure() = runTest {
        repository.failure = CancellationException("cancelled")

        assertFailsWith<CancellationException> { change(ProjectId("recruiting"), ProjectLifecycleAction.Start) }
    }
}

/** A repository that reads from [base] and fails the moves with [fail]. */
private abstract class FailingMove(private val base: FakeProjectsRepository) :
    org.matchlab.academic_net.feature.projects.domain.ProjectsRepository by base {
    abstract fun fail(id: ProjectId): Throwable

    override suspend fun changeLifecycle(id: ProjectId, action: ProjectLifecycleAction) =
        throw fail(id)
}

class UnpublishAfterLifecycleTest {
    @Test
    fun startedFinishedAndCancelledProjectsAreNotPublished() = runTest {
        val repository = FakeProjectsRepository(
            listOf(
                testProject("started", ProjectStatus.Started),
                testProject("finished", ProjectStatus.Finished),
                testProject("cancelled", ProjectStatus.Cancelled),
            ),
        )
        val unpublish = UnpublishProjectUseCase(repository)

        for (id in listOf("started", "finished", "cancelled")) {
            assertEquals(UnpublishProjectResult.NotPublished, unpublish(ProjectId(id)), id)
        }
        assertEquals(
            listOf(ProjectStatus.Started, ProjectStatus.Finished, ProjectStatus.Cancelled),
            repository.projects.value.map { it.status },
        )
    }
}
