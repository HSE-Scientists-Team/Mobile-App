package org.matchlab.academic_net.feature.projectdetails.domain

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.YearMonth
import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectMember
import org.matchlab.academic_net.common.model.ProjectPeriod
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.RoleMember
import org.matchlab.academic_net.common.model.Sphere
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary

private fun projectWith(members: List<ProjectMember>) = Project(
    id = ProjectId("p-1"),
    title = "Title",
    type = ProjectType.Scientific,
    sphere = Sphere("biology", "Биология"),
    problem = "Problem",
    goal = "Goal",
    tags = emptyList(),
    initiator = UserSummary(UserId("owner"), "Owner"),
    roles = emptyList(),
    status = ProjectStatus.Recruiting,
    period = ProjectPeriod(YearMonth(2026, 10), YearMonth(2027, 3)),
    viewerIsInitiator = false,
    members = members,
)

private fun member(user: String, role: String?, title: String = "Role", viewer: Boolean = false) =
    ProjectMember(UserId(user), role?.let(::RoleId), title, viewer)

private class TeamRepository : ProjectDetailsRepository {
    val asked = mutableListOf<Project>()
    var failure: Throwable? = null

    override suspend fun getProjectDetails(id: ProjectId): ProjectDetails? = null

    override suspend fun getTeam(project: Project): List<TeamMember> {
        asked += project
        failure?.let { throw it }
        return project.members.map {
            TeamMember(RoleMember(UserSummary(it.userId, "Name ${it.userId.value}")), it.roleTitle, it.isViewer)
        }
    }

    override suspend fun leaveProject(id: ProjectId) = Unit
}

class GetProjectTeamUseCaseTest {
    private val repository = TeamRepository()
    private val getTeam = GetProjectTeamUseCase(repository)

    @Test
    fun theInitiatorIsLeftOutAndTheRestGoToTheRepository() = runTest {
        val project = projectWith(listOf(member("owner", "r0"), member("u1", "r1"), member("u2", "r2")))

        val team = getTeam(project)

        assertEquals(listOf("u1", "u2"), team.map { it.person.user.id.value })
        assertEquals(listOf(UserId("u1"), UserId("u2")), repository.asked.single().members.map { it.userId })
    }

    @Test
    fun aProjectWithoutOtherMembersAsksForNothing() = runTest {
        assertEquals(emptyList(), getTeam(projectWith(emptyList())))
        assertEquals(emptyList(), getTeam(projectWith(listOf(member("owner", "r0"), member("owner", "r1")))))
        assertEquals(emptyList(), repository.asked)
    }

    @Test
    fun aPersonOnTwoRolesIsThereOncePerRoleAndRepeatedPairsAreDropped() = runTest {
        val project = projectWith(
            listOf(member("u1", "r1"), member("u1", "r1"), member("u1", "r2"), member("u1", null), member("u1", null)),
        )

        getTeam(project)

        assertEquals(
            listOf(UserId("u1") to RoleId("r1"), UserId("u1") to RoleId("r2"), UserId("u1") to null),
            repository.asked.single().members.map { it.userId to it.roleId },
        )
    }

    @Test
    fun aFailureOfTheRepositoryGivesMembersWithoutProfiles() = runTest {
        repository.failure = IllegalStateException("offline")
        val project = projectWith(listOf(member("owner", "r0"), member("u1", "r1", "Designer", viewer = true)))

        val team = getTeam(project)

        val only = team.single()
        assertFalse(only.person.hasProfile)
        assertEquals(UserId("u1"), only.person.user.id)
        assertEquals("", only.person.user.name)
        assertEquals("Designer", only.roleTitle)
        assertTrue(only.isViewer)
        assertEquals(null, only.person.rating)
        assertEquals(0, only.person.completedProjects)
    }

    @Test
    fun cancellationIsNotSwallowed() = runTest {
        repository.failure = CancellationException("cancelled")

        assertFailsWith<CancellationException> { getTeam(projectWith(listOf(member("u1", "r1")))) }
    }

    @Test
    fun theAnswerOfTheRepositoryComesAsIs() = runTest {
        val project = projectWith(listOf(member("u1", "r1", "Dev")))

        val team = getTeam(project)

        assertEquals("Dev", team.single().roleTitle)
        assertSame(true, team.single().person.hasProfile)
    }
}
