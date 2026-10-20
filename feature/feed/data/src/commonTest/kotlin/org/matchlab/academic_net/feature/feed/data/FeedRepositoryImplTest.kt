package org.matchlab.academic_net.feature.feed.data

import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.Collaborator
import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.Study
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.feature.feed.domain.RecommendedProject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

private class RecordingFeedDataSource : FeedDataSource {
    var calls = 0
    var projectCalls = 0
    var people: List<Collaborator> = emptyList()
    var projects: List<RecommendedProject> = emptyList()
    var failure: Throwable? = null

    override suspend fun getRecommendedPeople(): List<Collaborator> {
        calls++
        failure?.let { throw it }
        return people
    }

    override suspend fun getRecommendedProjects(): List<RecommendedProject> {
        projectCalls++
        failure?.let { throw it }
        return projects
    }
}

/** The repository adds nothing of its own: the people and the projects are the data source's. */
class FeedRepositoryImplTest {
    private val source = RecordingFeedDataSource()
    private val repository = FeedRepositoryImpl(source)

    @Test
    fun thePeopleAreTheSourcesPeople() = runTest {
        val people = listOf(Collaborator(UserSummary(UserId("a"), "Анна"), "МГУ", Study("магистратура", 2), "physics"))
        source.people = people

        assertSame(people, repository.getRecommendedPeople())
        assertEquals(1, source.calls)
    }

    @Test
    fun theProjectsAreTheSourcesProjects() = runTest {
        val project = Project(
            id = ProjectId("p"),
            title = "Проект",
            type = null,
            sphere = null,
            problem = "",
            goal = "",
            tags = emptyList(),
            initiator = UserSummary(UserId("o"), "Имя"),
            roles = emptyList(),
            status = null,
            period = null,
            viewerIsInitiator = false,
        )
        val projects = listOf(RecommendedProject(project, "МГУ"))
        source.projects = projects

        assertSame(projects, repository.getRecommendedProjects())
        assertEquals(1, source.projectCalls)
        assertEquals(0, source.calls, "the people are not asked for")
    }

    @Test
    fun everyCallAsksTheSourceAgain() = runTest {
        repository.getRecommendedPeople()
        repository.getRecommendedPeople()

        assertEquals(2, source.calls)
    }

    @Test
    fun theFailuresOfTheSourceAreThrownUnchanged() = runTest {
        val cause = RemoteFailure.Timeout()
        source.failure = cause

        assertSame(cause, assertFailsWith<RemoteFailure.Timeout> { repository.getRecommendedPeople() })
        assertSame(cause, assertFailsWith<RemoteFailure.Timeout> { repository.getRecommendedProjects() })
    }
}
