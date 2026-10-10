package org.matchlab.academic_net.feature.projects.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.feature.projects.data.mock.MockProjectsData
import org.matchlab.academic_net.feature.projects.data.mock.MockProjectsDataSource
import org.matchlab.academic_net.feature.projects.domain.NewProject
import org.matchlab.academic_net.feature.projects.domain.ProjectNotFoundException
import org.matchlab.academic_net.feature.projects.domain.RoleDraft
import org.matchlab.academic_net.utils.IdGenerator

class MockProjectsDataSourceTest {
    private var counter = 0
    private val ids = IdGenerator { "id-${counter++}" }

    private fun dataSource(latency: Long = 0) = MockProjectsDataSource(idGenerator = ids, latencyMillis = latency)

    private val newProject = NewProject(
        title = "Новый проект",
        type = ProjectType.Practical,
        sphere = MockProjectsData.spheres.first(),
        problem = "problem",
        goal = "goal",
        tags = listOf("IoT"),
        roles = listOf(RoleDraft("Backend", listOf("Kotlin"))),
    )

    @Test
    fun seedContainsOnlyProjectsOfCurrentUser() = runTest {
        val projects = dataSource().observeMyProjects().first()

        assertTrue(projects.isNotEmpty())
        assertTrue(projects.all { it.initiator == MockProjectsData.currentUser })
    }

    @Test
    fun projectsOfOtherUsersAreNotListed() = runTest {
        val stranger = UserSummary(UserId("someone-else"), "Кто-то")
        val foreign = MockProjectsData.myProjects().first().copy(initiator = stranger)
        val source = MockProjectsDataSource(initialProjects = listOf(foreign), latencyMillis = 0)

        assertEquals(emptyList(), source.observeMyProjects().first())
        assertEquals(foreign, source.observeProject(foreign.id).first())
    }

    @Test
    fun createdProjectIsRecruitingAndListedFirst() = runTest {
        val source = dataSource()

        val created = source.createProject(newProject)

        assertEquals(ProjectStatus.Recruiting, created.status)
        assertEquals(MockProjectsData.currentUser, created.initiator)
        assertEquals(created, source.observeMyProjects().first().first())
        assertEquals(listOf("Backend"), created.roles.map { it.title })
        assertTrue(created.roles.single().isVacant)
    }

    @Test
    fun createdProjectsGetDistinctIds() = runTest {
        val source = dataSource()

        val first = source.createProject(newProject)
        val second = source.createProject(newProject)

        assertNotEquals(first.id, second.id)
    }

    @Test
    fun unpublishChangesStatusAndNotifiesObservers() = runTest {
        val source = dataSource()
        val id = MockProjectsData.myProjects().first().id

        val updated = source.unpublishProject(id)

        assertEquals(ProjectStatus.Unpublished, updated.status)
        assertEquals(ProjectStatus.Unpublished, source.observeProject(id).first()?.status)
        assertEquals(ProjectStatus.Unpublished, source.getProject(id)?.status)
    }

    @Test
    fun unpublishOfUnknownProjectFails() = runTest {
        assertFailsWith<ProjectNotFoundException> { dataSource().unpublishProject(ProjectId("missing")) }
    }

    @Test
    fun addedRoleAppearsInProject() = runTest {
        val source = dataSource()
        val project = MockProjectsData.myProjects().first()

        val role = source.addRole(project.id, RoleDraft("DevOps", listOf("Docker")))

        val roles = source.getProject(project.id)?.roles.orEmpty()
        assertEquals(project.roles.size + 1, roles.size)
        assertEquals(role, roles.last())
        assertTrue(role.isVacant)
    }

    @Test
    fun addRoleToUnknownProjectFails() = runTest {
        assertFailsWith<ProjectNotFoundException> {
            dataSource().addRole(ProjectId("missing"), RoleDraft("DevOps"))
        }
    }

    @Test
    fun unknownProjectIsNull() = runTest {
        assertNull(dataSource().getProject(ProjectId("missing")))
        assertNull(dataSource().observeProject(ProjectId("missing")).first())
    }

    @Test
    fun spheresAreOfferedForSelection() = runTest {
        assertTrue(dataSource().getSpheres().any { it.name == "Computer Science" })
    }
}
