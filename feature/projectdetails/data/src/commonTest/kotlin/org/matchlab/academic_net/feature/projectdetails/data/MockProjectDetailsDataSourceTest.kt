package org.matchlab.academic_net.feature.projectdetails.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.matchlab.academic_net.feature.projectdetails.domain.InitiatorInfo
import org.matchlab.academic_net.feature.projectdetails.domain.ProjectDetails
import org.matchlab.academic_net.mockserver.MockProjectCatalog
import org.matchlab.academic_net.mockserver.MockServerProject

private fun record(
    id: String,
    status: ProjectStatus = ProjectStatus.Recruiting,
    own: Boolean = false,
    university: String = "University",
    rating: Double? = null,
    completed: Int = 0,
) = MockServerProject(
    project = Project(
        id = ProjectId(id),
        title = "Title $id",
        type = ProjectType.Practical,
        sphere = Sphere("biology", "Биология"),
        problem = "Problem",
        goal = "Goal",
        tags = listOf("Tag"),
        initiator = UserSummary(UserId("u-$id"), "Initiator"),
        roles = emptyList(),
        status = status,
        period = ProjectPeriod(YearMonth(2026, 10), YearMonth(2026, 12)),
        viewerIsInitiator = own,
    ),
    initiatorUniversity = university,
    initiatorRating = rating,
    initiatorCompletedProjects = completed,
)

private fun sourceOf(vararg projects: MockServerProject) =
    MockProjectDetailsDataSource(projects.toList(), latencyMillis = 0)

@OptIn(ExperimentalCoroutinesApi::class)
class MockProjectDetailsDataSourceTest {
    @Test
    fun findsTheProjectByIdWithTheInitiatorInfo() = runTest {
        val source = sourceOf(
            record("a"),
            record("b", university = "Stanford", rating = 4.9, completed = 12),
        )

        val details = source.getProjectDetails(ProjectId("b"))

        assertNotNull(details)
        assertEquals(ProjectId("b"), details.project.id)
        assertEquals(InitiatorInfo(university = "Stanford", rating = 4.9, completedProjects = 12), details.initiator)
    }

    @Test
    fun initiatorWithoutReviewsHasNoRating() = runTest {
        val details = sourceOf(record("a")).getProjectDetails(ProjectId("a"))

        assertNull(details?.initiator?.rating)
        assertEquals(0, details?.initiator?.completedProjects)
    }

    @Test
    fun unknownIdIsNull() = runTest {
        assertNull(sourceOf(record("a")).getProjectDetails(ProjectId("nope")))
    }

    @Test
    fun emptyCatalogKnowsNoProject() = runTest {
        assertNull(sourceOf().getProjectDetails(ProjectId("a")))
    }

    @Test
    fun unpublishedProjectIsNull() = runTest {
        val source = sourceOf(record("hidden", status = ProjectStatus.Unpublished), record("shown"))

        assertNull(source.getProjectDetails(ProjectId("hidden")))
        assertNotNull(source.getProjectDetails(ProjectId("shown")))
    }

    @Test
    fun viewersOwnProjectIsReturnedForTheServerToMark() = runTest {
        val details = sourceOf(record("mine", own = true)).getProjectDetails(ProjectId("mine"))

        assertEquals(true, details?.project?.viewerIsInitiator)
    }

    @Test
    fun answersAfterTheLatency() = runTest {
        val slow = MockProjectDetailsDataSource(listOf(record("a")), latencyMillis = 250)

        slow.getProjectDetails(ProjectId("a"))

        assertEquals(250, testScheduler.currentTime)
    }

    @Test
    fun zeroLatencyDoesNotWait() = runTest {
        sourceOf(record("a")).getProjectDetails(ProjectId("a"))

        assertEquals(0, testScheduler.currentTime)
    }

    @Test
    fun unknownIdWaitsForTheLatencyToo() = runTest {
        MockProjectDetailsDataSource(emptyList(), latencyMillis = 100).getProjectDetails(ProjectId("a"))

        assertEquals(100, testScheduler.currentTime)
    }
}

/** The page over the real seeded catalog of `:mockserver`. */
class MockProjectDetailsCatalogTest {
    private val source = MockProjectDetailsDataSource(latencyMillis = 0)

    @Test
    fun everyProjectOfTheCatalogIsFoundByItsId() = runTest {
        for (found in MockProjectCatalog.all) {
            val details = source.getProjectDetails(found.project.id)

            assertNotNull(details, "not found: ${found.project.id}")
            assertSame(found.project, details.project)
        }
    }

    @Test
    fun campusNavigationShowsTwoOfFourRolesTakenAndAReputableInitiator() = runTest {
        val details = source.getProjectDetails(ProjectId("project-found-campus-navigation"))

        assertNotNull(details)
        assertEquals(2, details.project.occupiedRolesCount)
        assertEquals(4, details.project.roles.size)
        assertEquals(InitiatorInfo("Stanford", rating = 4.9, completedProjects = 12), details.initiator)
        assertEquals(UserId("user-elena"), details.project.initiator.id)
    }

    @Test
    fun theDefaultSourceHasTheWholeCatalog() = runTest {
        assertTrue(MockProjectCatalog.all.isNotEmpty())
        assertNull(source.getProjectDetails(ProjectId("project-unknown")))
    }
}

class ProjectDetailsRepositoryImplTest {
    @Test
    fun delegatesTheIdAndReturnsTheAnswer() = runTest {
        val answer = sourceOf(record("a")).getProjectDetails(ProjectId("a"))
        val source = RecordingDataSource(answer)

        val result = ProjectDetailsRepositoryImpl(source).getProjectDetails(ProjectId("a"))

        assertEquals(listOf(ProjectId("a")), source.requested)
        assertSame(answer, result)
    }

    @Test
    fun nullAnswerStaysNull() = runTest {
        val repository = ProjectDetailsRepositoryImpl(RecordingDataSource(null))

        assertNull(repository.getProjectDetails(ProjectId("a")))
    }

    @Test
    fun errorsOfTheDataSourceGoThrough() = runTest {
        val repository = ProjectDetailsRepositoryImpl(object : ProjectDetailsDataSource {
            override suspend fun getProjectDetails(id: ProjectId): ProjectDetails =
                throw IllegalStateException("offline")
        })

        val failure = runCatching { repository.getProjectDetails(ProjectId("a")) }.exceptionOrNull()

        assertEquals("offline", failure?.message)
    }

    private class RecordingDataSource(private val answer: ProjectDetails?) : ProjectDetailsDataSource {
        val requested = mutableListOf<ProjectId>()

        override suspend fun getProjectDetails(id: ProjectId): ProjectDetails? {
            requested += id
            return answer
        }
    }
}
