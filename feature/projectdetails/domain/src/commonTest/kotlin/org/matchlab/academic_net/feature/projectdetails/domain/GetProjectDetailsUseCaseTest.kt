package org.matchlab.academic_net.feature.projectdetails.domain

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.YearMonth
import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectPeriod
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.Sphere
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary

private val Details = ProjectDetails(
    project = Project(
        id = ProjectId("p-1"),
        title = "Title",
        type = ProjectType.Scientific,
        sphere = Sphere("biology", "Биология"),
        problem = "Problem",
        goal = "Goal",
        tags = listOf("Tag"),
        initiator = UserSummary(UserId("u-1"), "Initiator"),
        roles = emptyList(),
        status = ProjectStatus.Recruiting,
        period = ProjectPeriod(YearMonth(2026, 10), YearMonth(2027, 3)),
        viewerIsInitiator = false,
    ),
    initiator = InitiatorInfo(university = "МГУ", rating = 4.5, completedProjects = 3),
)

/** Answers with [answer], or fails with [failure] when set; remembers what it was asked. */
private class FakeProjectDetailsRepository(var answer: ProjectDetails? = Details) : ProjectDetailsRepository {
    var failure: Throwable? = null
    val requested = mutableListOf<ProjectId>()

    override suspend fun getProjectDetails(id: ProjectId): ProjectDetails? {
        requested += id
        failure?.let { throw it }
        return answer
    }

    override suspend fun getTeam(project: Project): List<TeamMember> = emptyList()
}

class GetProjectDetailsUseCaseTest {
    private val repository = FakeProjectDetailsRepository()
    private val getProjectDetails = GetProjectDetailsUseCase(repository)

    @Test
    fun foundProjectComesAsIs() = runTest {
        val result = getProjectDetails(ProjectId("p-1"))

        val found = assertIs<ProjectDetailsResult.Found>(result)
        assertSame(Details, found.details)
        assertEquals(listOf(ProjectId("p-1")), repository.requested)
    }

    @Test
    fun noProjectIsNotFound() = runTest {
        repository.answer = null

        assertEquals(ProjectDetailsResult.NotFound, getProjectDetails(ProjectId("p-404")))
    }

    @Test
    fun failureOfTheRepositoryIsAResultWithTheCause() = runTest {
        val cause = IllegalStateException("offline")
        repository.failure = cause

        val result = getProjectDetails(ProjectId("p-1"))

        val failure = assertIs<ProjectDetailsResult.Failure>(result)
        assertSame(cause, failure.cause)
    }

    @Test
    fun cancellationIsNotAFailure() = runTest {
        repository.failure = CancellationException("cancelled")

        assertFailsWith<CancellationException> { getProjectDetails(ProjectId("p-1")) }
    }

    @Test
    fun failureDoesNotTurnIntoNotFound() = runTest {
        repository.failure = RuntimeException("boom")
        repository.answer = null

        assertIs<ProjectDetailsResult.Failure>(getProjectDetails(ProjectId("p-1")))
    }
}
