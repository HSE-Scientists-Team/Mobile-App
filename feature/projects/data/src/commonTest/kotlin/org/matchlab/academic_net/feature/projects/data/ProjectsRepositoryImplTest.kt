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
import org.matchlab.academic_net.feature.projects.domain.ProjectsCapabilities
import org.matchlab.academic_net.feature.projects.domain.RoleDraft

/** The repository only passes everything to the source; `publishProject` is the source's `createProject`. */
class ProjectsRepositoryImplTest {
    private val project = Project(
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
        override suspend fun getSpheres(): List<Sphere> = listOf(project.sphere).also { calls += "spheres" }
    }

    @Test
    fun everyMemberIsThePassedOnToTheSource() = runTest {
        val source = RecordingSource(project)
        val repository = ProjectsRepositoryImpl(source)
        val id = ProjectId("p1")

        assertSame(project, repository.loadProject(id))
        assertSame(project, repository.getProject(id))
        assertSame(project, repository.publishProject(NewProject("Новый", ProjectType.Practical, project.sphere, "P", "G", emptyList(), emptyList(), ProjectPeriod(YearMonth(2026, 10), YearMonth(2026, 11)))))
        assertSame(project, repository.unpublishProject(id))
        assertSame(source.role, repository.addRole(id, RoleDraft("Дизайнер")))
        assertEquals(listOf(project.sphere), repository.getSpheres())
        assertEquals(listOf(project), repository.observeMyProjects().first())
        assertEquals(project, repository.observeProject(id).first())
        assertEquals(
            listOf("load p1", "get p1", "create Новый", "unpublish p1", "role p1 Дизайнер", "spheres"),
            source.calls,
        )
    }

    @Test
    fun theCapabilitiesAreTheSourcesAtTheMomentOfAsking() {
        val source = RecordingSource(project)
        val repository = ProjectsRepositoryImpl(source)
        assertEquals(ProjectsCapabilities(false, false, false), repository.capabilities)

        source.capabilities = ProjectsCapabilities(canCreate = true, canListMine = true, canUnpublish = true)

        assertEquals(ProjectsCapabilities(true, true, true), repository.capabilities)
    }
}
