package org.matchlab.academic_net.feature.projectdetails.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.RoleMember
import org.matchlab.academic_net.common.model.Sphere
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.feature.projectdetails.domain.InitiatorInfo
import org.matchlab.academic_net.feature.projectdetails.domain.LeaveNotAllowedException
import org.matchlab.academic_net.feature.projectdetails.domain.ProjectDetails
import org.matchlab.academic_net.feature.projectdetails.domain.TeamMember

class ProjectDetailsRepositoryImplTest {
    private val details = ProjectDetails(
        project = Project(
            id = ProjectId("p1"),
            title = "T",
            type = null,
            sphere = Sphere("Biology", "Biology"),
            problem = "P",
            goal = "G",
            tags = emptyList(),
            initiator = UserSummary(UserId("u"), "U"),
            roles = emptyList(),
            status = null,
            period = null,
            viewerIsInitiator = false,
        ),
        initiator = InitiatorInfo("МГУ", rating = null, completedProjects = 0),
    )

    private val teamAsked = mutableListOf<Project>()
    private val team = listOf(TeamMember(RoleMember(UserSummary(UserId("m"), "M")), "Dev", isViewer = false))

    @Test
    fun theTeamIsAskedFromTheSourceAndComesBackAsItIs() = runTest {
        val repository = ProjectDetailsRepositoryImpl(
            object : ProjectDetailsDataSource {
                override suspend fun getProjectDetails(id: ProjectId): ProjectDetails? = null

                override suspend fun getTeam(project: Project): List<TeamMember> {
                    teamAsked += project
                    return team
                }

                override suspend fun leaveProject(id: ProjectId) = Unit
            },
        )

        assertSame(team, repository.getTeam(details.project))
        assertEquals(listOf(details.project), teamAsked)
    }

    @Test
    fun theRequestGoesToTheSourceAndTheAnswerComesBackAsItIs() = runTest {
        val asked = mutableListOf<ProjectId>()
        val repository = ProjectDetailsRepositoryImpl(
            object : ProjectDetailsDataSource {
                override suspend fun getProjectDetails(id: ProjectId): ProjectDetails? {
                    asked += id
                    return if (id.value == "p1") details else null
                }

                override suspend fun getTeam(project: Project): List<TeamMember> {
                    teamAsked += project
                    return team
                }

                override suspend fun leaveProject(id: ProjectId) = Unit
            },
        )

        assertSame(details, repository.getProjectDetails(ProjectId("p1")))
        assertNull(repository.getProjectDetails(ProjectId("other")))
        assertEquals(listOf(ProjectId("p1"), ProjectId("other")), asked)
    }

    @Test
    fun leavingGoesToTheSourceAndItsFailureComesBack() = runTest {
        val left = mutableListOf<ProjectId>()
        val repository = ProjectDetailsRepositoryImpl(
            object : ProjectDetailsDataSource {
                override suspend fun getProjectDetails(id: ProjectId): ProjectDetails? = null

                override suspend fun getTeam(project: Project): List<TeamMember> = emptyList()

                override suspend fun leaveProject(id: ProjectId) {
                    left += id
                    if (id.value == "denied") throw LeaveNotAllowedException(id)
                }
            },
        )

        repository.leaveProject(ProjectId("p1"))
        assertFailsWith<LeaveNotAllowedException> { repository.leaveProject(ProjectId("denied")) }

        assertEquals(listOf(ProjectId("p1"), ProjectId("denied")), left)
    }
}
