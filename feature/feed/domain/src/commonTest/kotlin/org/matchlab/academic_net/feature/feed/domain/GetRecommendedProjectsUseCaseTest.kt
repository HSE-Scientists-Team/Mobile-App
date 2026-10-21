package org.matchlab.academic_net.feature.feed.domain

import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.Collaborator
import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.feature.feed.domain.usecase.GetRecommendedProjectsUseCase
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

private class ProjectsOnlyRepository : FeedRepository {
    var calls = 0
    var answer: suspend () -> List<RecommendedProject> = { emptyList() }

    override suspend fun getRecommendedPeople(): List<Collaborator> =
        throw AssertionError("The projects block never asks for the people")

    override suspend fun getRecommendedProjects(): List<RecommendedProject> {
        calls++
        return answer()
    }
}

private fun recommended(id: String) = RecommendedProject(
    project = Project(
        id = ProjectId(id),
        title = "Проект $id",
        type = null,
        sphere = null,
        problem = "",
        goal = "",
        tags = emptyList(),
        initiator = UserSummary(UserId("owner-$id"), "Имя"),
        roles = emptyList(),
        status = null,
        period = null,
        viewerIsInitiator = false,
    ),
    initiatorUniversity = "МГУ",
)

/** "Рекомендации для вас": the projects block of the feed, as a result. */
class GetRecommendedProjectsUseCaseTest {
    private val repository = ProjectsOnlyRepository()
    private val get = GetRecommendedProjectsUseCase(repository)

    @Test
    fun theProjectsOfTheRepositoryAreTheSuccessInTheirOrder() = runTest {
        val projects = listOf(recommended("c"), recommended("a"), recommended("b"))
        repository.answer = { projects }

        val result = get()

        assertSame(projects, result.getOrThrow())
        assertEquals(1, repository.calls)
    }

    @Test
    fun noProjectIsASuccessNotAFailure() = runTest {
        val result = get()

        assertTrue(result.isSuccess)
        assertEquals(emptyList(), result.getOrThrow())
    }

    @Test
    fun aFailureOfTheRepositoryIsAFailedResultWithTheSameCause() = runTest {
        val cause = RemoteFailure.Http(503)
        repository.answer = { throw cause }

        val result = get()

        assertIs<RemoteFailure.Http>(result.exceptionOrNull())
        assertSame(cause, result.exceptionOrNull())
    }

    @Test
    fun aCancellationIsNotAResult() = runTest {
        repository.answer = { throw CancellationException("cancelled") }

        assertFailsWith<CancellationException> { get() }
    }
}
