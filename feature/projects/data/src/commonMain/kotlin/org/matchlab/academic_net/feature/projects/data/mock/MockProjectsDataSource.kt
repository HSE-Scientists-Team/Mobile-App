package org.matchlab.academic_net.feature.projects.data.mock

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.common.model.Role
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.Sphere
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.feature.projects.data.ProjectsDataSource
import org.matchlab.academic_net.feature.projects.domain.NewProject
import org.matchlab.academic_net.feature.projects.domain.ProjectNotFoundException
import org.matchlab.academic_net.feature.projects.domain.RoleDraft
import org.matchlab.academic_net.utils.IdGenerator
import org.matchlab.academic_net.utils.UuidIdGenerator

/**
 * In-memory stand-in for the backend: keeps the projects in a flow so every screen sees changes
 * made on another one, and waits [latencyMillis] before answering like a network call would.
 */
class MockProjectsDataSource(
    private val currentUser: UserSummary = MockProjectsData.currentUser,
    initialProjects: List<Project> = MockProjectsData.myProjects(),
    private val spheres: List<Sphere> = MockProjectsData.spheres,
    private val idGenerator: IdGenerator = UuidIdGenerator,
    private val latencyMillis: Long = DefaultLatencyMillis,
) : ProjectsDataSource {
    private val projects = MutableStateFlow(initialProjects)

    override fun observeMyProjects(): Flow<List<Project>> =
        projects.map { all -> all.filter { it.initiator.id == currentUser.id } }

    override fun observeProject(id: ProjectId): Flow<Project?> =
        projects.map { all -> all.firstOrNull { it.id == id } }

    override suspend fun getProject(id: ProjectId): Project? {
        simulateNetwork()
        return projects.value.firstOrNull { it.id == id }
    }

    override suspend fun createProject(project: NewProject): Project {
        simulateNetwork()
        val created = Project(
            id = ProjectId(idGenerator.newId()),
            title = project.title,
            type = project.type,
            sphere = project.sphere,
            problem = project.problem,
            goal = project.goal,
            tags = project.tags,
            initiator = currentUser,
            roles = project.roles.map { it.toRole() },
            status = ProjectStatus.Recruiting,
            period = project.period,
            viewerIsInitiator = true,
        )
        // Newest first, as the server returns them.
        projects.update { listOf(created) + it }
        return created
    }

    override suspend fun unpublishProject(id: ProjectId): Project {
        simulateNetwork()
        return updateProject(id) { it.copy(status = ProjectStatus.Unpublished) }
    }

    override suspend fun addRole(projectId: ProjectId, role: RoleDraft): Role {
        simulateNetwork()
        val newRole = role.toRole()
        updateProject(projectId) { it.copy(roles = it.roles + newRole) }
        return newRole
    }

    override suspend fun getSpheres(): List<Sphere> {
        simulateNetwork()
        return spheres
    }

    private fun updateProject(id: ProjectId, transform: (Project) -> Project): Project {
        var updated: Project? = null
        projects.update { all ->
            all.map { project ->
                if (project.id == id) transform(project).also { updated = it } else project
            }
        }
        return updated ?: throw ProjectNotFoundException(id)
    }

    private fun RoleDraft.toRole() = Role(RoleId(idGenerator.newId()), title, skills)

    private suspend fun simulateNetwork() {
        if (latencyMillis > 0) delay(latencyMillis)
    }

    private companion object {
        const val DefaultLatencyMillis = 300L
    }
}
