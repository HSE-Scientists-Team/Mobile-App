package org.matchlab.academic_net.feature.projects.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.matchlab.academic_net.common.model.Project
import kotlinx.datetime.YearMonth
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectPeriod
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.Role
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.Sphere
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary

val TestSphere = Sphere("cs", "Computer Science")
val TestUser = UserSummary(UserId("me"), "Вы")

fun validDraft() = ProjectDraft(
    title = "AI-навигация по кампусу",
    type = ProjectType.Scientific,
    sphere = TestSphere,
    problem = "GPS не работает внутри зданий",
    goal = "Строить маршруты внутри корпусов",
    start = YearMonth(2026, 10),
    end = YearMonth(2027, 3),
)

fun testProject(
    id: String = "p1",
    status: ProjectStatus? = ProjectStatus.Recruiting,
    roles: List<Role> = emptyList(),
) = Project(
    id = ProjectId(id),
    title = "Project $id",
    type = ProjectType.Scientific,
    sphere = TestSphere,
    problem = "problem",
    goal = "goal",
    tags = emptyList(),
    initiator = TestUser,
    roles = roles,
    status = status,
    period = ProjectPeriod(YearMonth(2026, 10), YearMonth(2027, 3)),
    viewerIsInitiator = true,
)

/** Repository that keeps projects in memory and can be told to fail. */
class FakeProjectsRepository(
    initial: List<Project> = emptyList(),
    override val capabilities: ProjectsCapabilities = ProjectsCapabilities(
        canCreate = true,
        canListMine = true,
        canUnpublish = true,
        canChangeLifecycle = true,
    ),
) : ProjectsRepository {
    val projects = MutableStateFlow(initial)
    var failure: Throwable? = null
    var published: NewProject? = null
        private set

    /** The ids [loadProject] was asked for, in order. */
    val loaded = mutableListOf<ProjectId>()

    private fun failIfNeeded() {
        failure?.let { throw it }
    }

    override fun observeMyProjects(): Flow<List<Project>> = projects

    override fun observeProject(id: ProjectId): Flow<Project?> =
        projects.map { all -> all.firstOrNull { it.id == id } }

    override suspend fun loadProject(id: ProjectId): Project? {
        loaded += id
        loadFailures[id]?.let { throw it }
        failIfNeeded()
        return projects.value.firstOrNull { it.id == id }
    }

    override suspend fun getProject(id: ProjectId): Project? {
        failIfNeeded()
        return projects.value.firstOrNull { it.id == id }
    }

    override suspend fun publishProject(project: NewProject): Project {
        failIfNeeded()
        published = project
        val created = testProject(id = "new").copy(
            title = project.title,
            type = project.type,
            sphere = project.sphere,
            problem = project.problem,
            goal = project.goal,
            tags = project.tags,
            period = project.period,
            roles = project.roles.mapIndexed { index, role -> Role(RoleId("r$index"), role.title, role.skills) },
        )
        projects.value = listOf(created) + projects.value
        return created
    }

    override suspend fun unpublishProject(id: ProjectId): Project {
        failIfNeeded()
        val current = projects.value.firstOrNull { it.id == id } ?: throw ProjectNotFoundException(id)
        val updated = current.copy(status = ProjectStatus.Unpublished)
        projects.value = projects.value.map { if (it.id == id) updated else it }
        return updated
    }

    override suspend fun addRole(projectId: ProjectId, role: RoleDraft): Role {
        failIfNeeded()
        val current = projects.value.firstOrNull { it.id == projectId } ?: throw ProjectNotFoundException(projectId)
        val added = Role(RoleId("r${current.roles.size}"), role.title, role.skills)
        projects.value = projects.value.map { if (it.id == projectId) it.copy(roles = it.roles + added) else it }
        return added
    }

    /** The calls of the lifecycle, role and description methods, in order (what the use cases really sent). */
    val lifecycleCalls = mutableListOf<Pair<ProjectId, ProjectLifecycleAction>>()
    val updatedInfos = mutableListOf<Pair<ProjectId, ProjectInfo>>()
    val updatedRoles = mutableListOf<Triple<ProjectId, RoleId, RoleDraft>>()
    val removedRoles = mutableListOf<Pair<ProjectId, RoleId>>()

    override suspend fun changeLifecycle(id: ProjectId, action: ProjectLifecycleAction): Project {
        lifecycleCalls += id to action
        failIfNeeded()
        val current = projects.value.firstOrNull { it.id == id } ?: throw ProjectNotFoundException(id)
        val status = when (action) {
            ProjectLifecycleAction.Start -> ProjectStatus.Started
            ProjectLifecycleAction.Complete -> ProjectStatus.Finished
            ProjectLifecycleAction.Cancel -> ProjectStatus.Cancelled
        }
        val updated = current.copy(status = status)
        projects.value = projects.value.map { if (it.id == id) updated else it }
        return updated
    }

    override suspend fun updateProject(id: ProjectId, info: ProjectInfo): Project {
        updatedInfos += id to info
        failIfNeeded()
        val current = projects.value.firstOrNull { it.id == id } ?: throw ProjectNotFoundException(id)
        val updated = current.copy(
            title = info.title,
            type = info.type,
            sphere = info.sphere,
            problem = info.problem,
            goal = info.goal,
            tags = info.tags,
            period = info.period,
        )
        projects.value = projects.value.map { if (it.id == id) updated else it }
        return updated
    }

    override suspend fun updateRole(projectId: ProjectId, roleId: RoleId, role: RoleDraft): Role {
        updatedRoles += Triple(projectId, roleId, role)
        failIfNeeded()
        val current = projects.value.firstOrNull { it.id == projectId } ?: throw ProjectNotFoundException(projectId)
        val old = current.roles.firstOrNull { it.id == roleId } ?: throw ProjectConflictException(projectId)
        val updated = old.copy(title = role.title, skills = role.skills)
        projects.value = projects.value.map {
            if (it.id == projectId) it.copy(roles = it.roles.map { r -> if (r.id == roleId) updated else r }) else it
        }
        return updated
    }

    override suspend fun removeRole(projectId: ProjectId, roleId: RoleId): Project {
        removedRoles += projectId to roleId
        failIfNeeded()
        val current = projects.value.firstOrNull { it.id == projectId } ?: throw ProjectNotFoundException(projectId)
        val updated = current.copy(roles = current.roles.filterNot { it.id == roleId })
        projects.value = projects.value.map { if (it.id == projectId) updated else it }
        return updated
    }

    /** The calls of [removeMember], in order; [removeMemberFailure] is thrown by it when set. */
    val removedMembers = mutableListOf<Pair<ProjectId, UserId>>()
    var removeMemberFailure: Throwable? = null

    override suspend fun removeMember(projectId: ProjectId, userId: UserId): Project {
        removedMembers += projectId to userId
        failIfNeeded()
        removeMemberFailure?.let { throw it }
        val current = projects.value.firstOrNull { it.id == projectId } ?: throw ProjectNotFoundException(projectId)
        val updated = current.copy(members = current.members.filterNot { it.userId == userId })
        projects.value = projects.value.map { if (it.id == projectId) updated else it }
        return updated
    }

    /** Projects [loadProject] fails for, whatever [failure] says. */
    val loadFailures = mutableMapOf<ProjectId, Throwable>()

    override suspend fun getSpheres(): List<Sphere> {
        failIfNeeded()
        return listOf(TestSphere)
    }
}
