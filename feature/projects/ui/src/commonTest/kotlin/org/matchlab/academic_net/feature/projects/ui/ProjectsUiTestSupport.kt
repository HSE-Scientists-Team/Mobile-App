package org.matchlab.academic_net.feature.projects.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectPeriod
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.Role
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.RoleMember
import org.matchlab.academic_net.common.model.Sphere
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.feature.projects.domain.NewProject
import org.matchlab.academic_net.feature.projects.domain.ProjectConflictException
import org.matchlab.academic_net.feature.projects.domain.ProjectInfo
import org.matchlab.academic_net.feature.projects.domain.ProjectLifecycleAction
import org.matchlab.academic_net.feature.projects.domain.ProjectNotFoundException
import org.matchlab.academic_net.feature.projects.domain.ProjectsCapabilities
import org.matchlab.academic_net.feature.projects.domain.ProjectsRepository
import org.matchlab.academic_net.feature.projects.domain.RoleDraft

// What the tests of the projects screens share: the main dispatcher of a test, and a fake repository that plays the data
// layer (the cache the screens observe, the server that `loadProject` reads, and gates to hold an answer back).

@OptIn(ExperimentalCoroutinesApi::class)
internal fun installMainDispatcher() = Dispatchers.setMain(UnconfinedTestDispatcher())

@OptIn(ExperimentalCoroutinesApi::class)
internal fun uninstallMainDispatcher() = Dispatchers.resetMain()

/** Collects [flow] for the rest of the test, so that a `WhileSubscribed` state is active. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T> TestScope.keepCollecting(flow: Flow<T>) {
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect {} }
}

internal fun caps(
    canCreate: Boolean = false,
    canListMine: Boolean = false,
    canUnpublish: Boolean = false,
    canChangeLifecycle: Boolean = false,
) = ProjectsCapabilities(
    canCreate = canCreate,
    canListMine = canListMine,
    canUnpublish = canUnpublish,
    canChangeLifecycle = canChangeLifecycle,
)

internal val TestSphere = Sphere("Biology", "Biology")

internal fun role(
    id: String,
    title: String = "Роль $id",
    slots: Int = 1,
    occupied: Int = 0,
    skills: List<String> = emptyList(),
    members: List<RoleMember> = emptyList(),
) = Role(RoleId(id), title, skills, slots, members, occupied)

internal fun project(
    id: String = "p1",
    title: String = "Проект $id",
    status: ProjectStatus? = null,
    viewerIsInitiator: Boolean = true,
    roles: List<Role> = emptyList(),
    type: ProjectType? = null,
    sphere: Sphere? = TestSphere,
    period: ProjectPeriod? = null,
) = Project(
    id = ProjectId(id),
    title = title,
    type = type,
    sphere = sphere,
    problem = "Проблема",
    goal = "Цель",
    tags = listOf("ML"),
    initiator = UserSummary(UserId("owner-$id"), "Автор", isVerified = true),
    roles = roles,
    status = status,
    period = period,
    viewerIsInitiator = viewerIsInitiator,
)

/** An answer held back until the test opens the gate. */
internal class Gate {
    private val opened = CompletableDeferred<Unit>()

    suspend fun await() = opened.await()

    fun open() {
        opened.complete(Unit)
    }
}

/**
 * The data layer as the screens see it: [cache] is what `observeProject` shows; [server] is what `loadProject` reads into
 * it. Every call is recorded; a gate holds it back, a failure fails it. Nothing is scripted by default but the happy path:
 * unpublishing marks the project, a role is added to it, creating answers with [publishAnswer].
 */
internal class FakeProjectsRepository(
    override var capabilities: ProjectsCapabilities = caps(),
) : ProjectsRepository {
    val cache = MutableStateFlow<Map<ProjectId, Project>>(emptyMap())
    val server = mutableMapOf<ProjectId, Project>()

    // ---- the list of the viewer's projects ----
    var myProjects: Flow<List<Project>> = flowOf(emptyList())
    var myProjectsCalls = 0

    // ---- reading ----
    val loads = mutableListOf<ProjectId>()
    var loadFailure: Throwable? = null
    var loadGate: Gate? = null

    // ---- unpublishing ----
    val unpublishes = mutableListOf<ProjectId>()
    var unpublishFailure: Throwable? = null
    var unpublishGate: Gate? = null

    // ---- adding a role ----
    val addedRoles = mutableListOf<Pair<ProjectId, RoleDraft>>()
    var addRoleFailure: Throwable? = null
    var addRoleGate: Gate? = null

    // ---- the lifecycle ----
    val lifecycleMoves = mutableListOf<Pair<ProjectId, ProjectLifecycleAction>>()
    var lifecycleFailure: Throwable? = null
    var lifecycleGate: Gate? = null

    // ---- the description ----
    val updatedInfos = mutableListOf<Pair<ProjectId, ProjectInfo>>()
    var updateProjectFailure: Throwable? = null
    var updateProjectGate: Gate? = null

    // ---- the roles of a published project ----
    val updatedRoles = mutableListOf<Triple<ProjectId, RoleId, RoleDraft>>()
    var updateRoleFailure: Throwable? = null
    var updateRoleGate: Gate? = null
    val removedRoles = mutableListOf<Pair<ProjectId, RoleId>>()
    var removeRoleFailure: Throwable? = null
    var removeRoleGate: Gate? = null

    // ---- creating ----
    val published = mutableListOf<NewProject>()
    var publishFailure: Throwable? = null
    var publishGate: Gate? = null
    var publishAnswer: Project = project("new")

    // ---- spheres ----
    var spheres: List<Sphere> = listOf(TestSphere)
    var spheresFailure: Throwable? = null
    var spheresGate: Gate? = null
    var spheresCalls = 0

    override fun observeMyProjects(): Flow<List<Project>> {
        myProjectsCalls++
        return myProjects
    }

    override fun observeProject(id: ProjectId): Flow<Project?> = cache.map { it[id] }

    override suspend fun loadProject(id: ProjectId): Project? {
        loads += id
        loadGate?.await()
        loadFailure?.let { throw it }
        val project = server[id]
        if (project != null) cache.update { it + (id to project) }
        return project
    }

    override suspend fun getProject(id: ProjectId): Project? = cache.value[id] ?: loadProject(id)

    override suspend fun publishProject(project: NewProject): Project {
        published += project
        publishGate?.await()
        publishFailure?.let { throw it }
        return publishAnswer
    }

    override suspend fun unpublishProject(id: ProjectId): Project {
        unpublishes += id
        unpublishGate?.await()
        unpublishFailure?.let { throw it }
        val current = cache.value[id] ?: throw ProjectNotFoundException(id)
        val updated = current.copy(status = ProjectStatus.Unpublished)
        cache.update { it + (id to updated) }
        return updated
    }

    override suspend fun addRole(projectId: ProjectId, role: RoleDraft): Role {
        addedRoles += projectId to role
        addRoleGate?.await()
        addRoleFailure?.let { throw it }
        val current = cache.value[projectId] ?: throw ProjectNotFoundException(projectId)
        val added = Role(RoleId("added-${current.roles.size}"), role.title, role.skills)
        cache.update { it + (projectId to current.copy(roles = current.roles + added)) }
        return added
    }

    override suspend fun changeLifecycle(id: ProjectId, action: ProjectLifecycleAction): Project {
        lifecycleMoves += id to action
        lifecycleGate?.await()
        lifecycleFailure?.let { throw it }
        val current = cache.value[id] ?: throw ProjectNotFoundException(id)
        val status = when (action) {
            ProjectLifecycleAction.Start -> ProjectStatus.Started
            ProjectLifecycleAction.Complete -> ProjectStatus.Finished
            ProjectLifecycleAction.Cancel -> ProjectStatus.Cancelled
        }
        val updated = current.copy(status = status)
        cache.update { it + (id to updated) }
        return updated
    }

    override suspend fun updateProject(id: ProjectId, info: ProjectInfo): Project {
        updatedInfos += id to info
        updateProjectGate?.await()
        updateProjectFailure?.let { throw it }
        val current = cache.value[id] ?: throw ProjectNotFoundException(id)
        val updated = current.copy(
            title = info.title,
            type = info.type,
            sphere = info.sphere,
            problem = info.problem,
            goal = info.goal,
            tags = info.tags,
            period = info.period,
        )
        cache.update { it + (id to updated) }
        return updated
    }

    override suspend fun updateRole(projectId: ProjectId, roleId: RoleId, role: RoleDraft): Role {
        updatedRoles += Triple(projectId, roleId, role)
        updateRoleGate?.await()
        updateRoleFailure?.let { throw it }
        val current = cache.value[projectId] ?: throw ProjectNotFoundException(projectId)
        val old = current.roles.firstOrNull { it.id == roleId } ?: throw ProjectConflictException(projectId)
        val updated = old.copy(title = role.title, skills = role.skills)
        cache.update {
            it + (projectId to current.copy(roles = current.roles.map { r -> if (r.id == roleId) updated else r }))
        }
        return updated
    }

    override suspend fun removeRole(projectId: ProjectId, roleId: RoleId): Project {
        removedRoles += projectId to roleId
        removeRoleGate?.await()
        removeRoleFailure?.let { throw it }
        val current = cache.value[projectId] ?: throw ProjectNotFoundException(projectId)
        val updated = current.copy(roles = current.roles.filterNot { it.id == roleId })
        cache.update { it + (projectId to updated) }
        return updated
    }

    // ---- removing a member ----
    val removedMembers = mutableListOf<Pair<ProjectId, UserId>>()
    var removeMemberFailure: Throwable? = null
    var removeMemberGate: Gate? = null

    override suspend fun removeMember(projectId: ProjectId, userId: UserId): Project {
        removedMembers += projectId to userId
        removeMemberGate?.await()
        removeMemberFailure?.let { throw it }
        val current = cache.value[projectId] ?: throw ProjectNotFoundException(projectId)
        val updated = current.copy(members = current.members.filterNot { it.userId == userId })
        cache.update { it + (projectId to updated) }
        return updated
    }

    override suspend fun getSpheres(): List<Sphere> {
        spheresCalls++
        spheresGate?.await()
        spheresFailure?.let { throw it }
        return spheres
    }
}
