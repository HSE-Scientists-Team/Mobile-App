package org.matchlab.academic_net.feature.projects.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.YearMonth
import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectPeriod
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.Role
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.Sphere
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.feature.projects.domain.NewProject
import org.matchlab.academic_net.feature.projects.domain.ProjectInfo
import org.matchlab.academic_net.feature.projects.domain.ProjectLifecycleAction
import org.matchlab.academic_net.feature.projects.domain.ProjectsCapabilities
import org.matchlab.academic_net.feature.projects.domain.RoleDraft

/** The repository only passes everything to the source; `publishProject` is the source's `createProject`. */
class ProjectsRepositoryImplTest {
    private val sphere = Sphere("Biology", "Biology")

    private val project = Project(
        id = ProjectId("p1"),
        title = "T",
        type = null,
        sphere = sphere,
        problem = "P",
        goal = "G",
        tags = emptyList(),
        initiator = UserSummary(UserId("u"), "U"),
        roles = emptyList(),
        status = null,
        period = null,
        viewerIsInitiator = true,
    )

    private class RecordingSource(private val project: Project) : ProjectsDataSource {
        val calls = mutableListOf<String>()
        override var capabilities = ProjectsCapabilities(canCreate = false, canListMine = false, canUnpublish = false)
        val cache = MutableStateFlow<Project?>(project)
        val mine = MutableStateFlow(listOf(project))
        val role = Role(RoleId("r1"), "Роль")

        override fun observeMyProjects(): Flow<List<Project>> = mine
        override fun observeProject(id: ProjectId): Flow<Project?> = cache
        override suspend fun loadProject(id: ProjectId): Project? = project.also { calls += "load ${id.value}" }
        override suspend fun getProject(id: ProjectId): Project? = project.also { calls += "get ${id.value}" }
        override suspend fun createProject(project: NewProject): Project = this.project.also { calls += "create ${project.title}" }
        override suspend fun unpublishProject(id: ProjectId): Project = project.also { calls += "unpublish ${id.value}" }
        override suspend fun addRole(projectId: ProjectId, role: RoleDraft): Role = this.role.also { calls += "role ${projectId.value} ${role.title}" }
        override suspend fun changeLifecycle(id: ProjectId, action: ProjectLifecycleAction): Project =
            project.also { calls += "lifecycle ${id.value} $action" }
        override suspend fun updateProject(id: ProjectId, info: ProjectInfo): Project =
            project.also { calls += "update ${id.value} ${info.title}" }
        override suspend fun updateRole(projectId: ProjectId, roleId: RoleId, role: RoleDraft): Role =
            this.role.also { calls += "update-role ${projectId.value} ${roleId.value} ${role.title}" }
        override suspend fun removeRole(projectId: ProjectId, roleId: RoleId): Project =
            project.also { calls += "remove-role ${projectId.value} ${roleId.value}" }
        override suspend fun removeMember(projectId: ProjectId, userId: UserId): Project =
            project.also { calls += "remove-member ${projectId.value} ${userId.value}" }
        override suspend fun getParticipatingProjects(): List<Project> = listOf(project).also { calls += "participating" }
        override suspend fun getSpheres(): List<Sphere> = listOfNotNull(project.sphere).also { calls += "spheres" }
    }

    @Test
    fun everyMemberIsThePassedOnToTheSource() = runTest {
        val source = RecordingSource(project)
        val repository = ProjectsRepositoryImpl(source)
        val id = ProjectId("p1")

        assertSame(project, repository.loadProject(id))
        assertSame(project, repository.getProject(id))
        assertSame(project, repository.publishProject(NewProject("Новый", ProjectType.Practical, sphere, "P", "G", emptyList(), emptyList(), ProjectPeriod(YearMonth(2026, 10), YearMonth(2026, 11)))))
        assertSame(project, repository.unpublishProject(id))
        assertSame(source.role, repository.addRole(id, RoleDraft("Дизайнер")))
        assertSame(project, repository.changeLifecycle(id, ProjectLifecycleAction.Complete))
        assertSame(
            project,
            repository.updateProject(id, ProjectInfo("Новое", ProjectType.Practical, sphere, "P", "G", emptyList(), ProjectPeriod(YearMonth(2026, 10), YearMonth(2026, 11)))),
        )
        assertSame(source.role, repository.updateRole(id, RoleId("r1"), RoleDraft("Дев")))
        assertSame(project, repository.removeRole(id, RoleId("r1")))
        assertSame(project, repository.removeMember(id, UserId("u7")))
        assertEquals(listOf(project), repository.getParticipatingProjects())
        assertEquals(listOf(sphere), repository.getSpheres())
        assertEquals(listOf(project), repository.observeMyProjects().first())
        assertEquals(project, repository.observeProject(id).first())
        assertEquals(
            listOf(
                "load p1", "get p1", "create Новый", "unpublish p1", "role p1 Дизайнер",
                "lifecycle p1 Complete", "update p1 Новое", "update-role p1 r1 Дев", "remove-role p1 r1", "remove-member p1 u7", "participating", "spheres",
            ),
            source.calls,
        )
    }

    @Test
    fun theParticipatingProjectsAreTheSourcesListAsItIsAndFreshEachTime() = runTest {
        val first = project.copy(id = ProjectId("j1"))
        val second = project.copy(id = ProjectId("j2"))
        val lists = ArrayDeque(listOf(listOf(first, second), emptyList()))
        val source = object : ProjectsDataSource by RecordingSource(project) {
            override suspend fun getParticipatingProjects(): List<Project> = lists.removeFirst()
        }
        val repository = ProjectsRepositoryImpl(source)

        assertEquals(listOf(first, second), repository.getParticipatingProjects())
        assertEquals(emptyList(), repository.getParticipatingProjects())
    }

    @Test
    fun aFailureOfTheParticipatingReadReachesTheCallerUnchanged() = runTest {
        val failure = org.matchlab.academic_net.common.result.RemoteFailure.Timeout()
        val source = object : ProjectsDataSource by RecordingSource(project) {
            override suspend fun getParticipatingProjects(): List<Project> = throw failure
        }

        assertSame(failure, kotlin.test.assertFailsWith<org.matchlab.academic_net.common.result.RemoteFailure> {
            ProjectsRepositoryImpl(source).getParticipatingProjects()
        })
    }

    @Test
    fun aCancellationOfTheParticipatingReadPassesThrough() = runTest {
        val source = object : ProjectsDataSource by RecordingSource(project) {
            override suspend fun getParticipatingProjects(): List<Project> = throw kotlin.coroutines.cancellation.CancellationException("c")
        }

        kotlin.test.assertFailsWith<kotlin.coroutines.cancellation.CancellationException> {
            ProjectsRepositoryImpl(source).getParticipatingProjects()
        }
    }

    @Test
    fun theCapabilitiesAreTheSourcesAtTheMomentOfAsking() {
        val source = RecordingSource(project)
        val repository = ProjectsRepositoryImpl(source)
        assertEquals(ProjectsCapabilities(false, false, false, false), repository.capabilities)

        source.capabilities = ProjectsCapabilities(canCreate = true, canListMine = true, canUnpublish = true, canChangeLifecycle = true)

        assertEquals(ProjectsCapabilities(true, true, true, true), repository.capabilities)
    }
}
