package org.matchlab.academic_net.feature.projects.domain.usecase

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.result.LoadResult
import org.matchlab.academic_net.feature.projects.domain.FakeProjectsRepository
import org.matchlab.academic_net.feature.projects.domain.testProject

class LoadProjectUseCaseTest {
    private val project = testProject("p1")
    private val repository = FakeProjectsRepository(listOf(project))
    private val load = LoadProjectUseCase(repository)

    @Test
    fun aProjectTheServerKnowsIsFound() = runTest {
        assertEquals(LoadResult.Found(project), load(ProjectId("p1")))
        assertEquals(listOf(ProjectId("p1")), repository.loaded)
    }

    @Test
    fun aProjectTheViewerMayNotSeeIsNotFound() = runTest {
        // loadProject answers null for 400, 403 and 404 alike; asking again does not help.
        assertEquals(LoadResult.NotFound, load(ProjectId("missing")))
    }

    @Test
    fun aFailureOfTheRepositoryIsAFailureAndKeepsItsCause() = runTest {
        val error = IllegalStateException("offline")
        repository.failure = error

        assertSame(error, assertIs<LoadResult.Failure>(load(ProjectId("p1"))).cause)
    }

    @Test
    fun cancellationPassesThrough() = runTest {
        repository.failure = CancellationException("cancelled")

        assertFailsWith<CancellationException> { load(ProjectId("p1")) }
    }
}
