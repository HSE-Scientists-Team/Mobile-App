package org.matchlab.academic_net.feature.responses.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.matchlab.academic_net.common.model.Collaborator
import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectJoin
import org.matchlab.academic_net.common.model.ProjectJoinId
import org.matchlab.academic_net.common.model.ProjectJoinStatus
import org.matchlab.academic_net.common.model.ProjectJoinType
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.common.model.Role
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.Study
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import kotlin.time.Instant

fun at(seconds: Long): Instant = Instant.fromEpochSeconds(seconds)

fun join(
    id: String = "j1",
    projectId: String = "p1",
    roleId: String = "r1",
    candidateId: String? = "u1",
    type: ProjectJoinType? = ProjectJoinType.Application,
    status: ProjectJoinStatus? = ProjectJoinStatus.Pending,
    createdAt: Instant? = null,
    message: String = "",
) = ProjectJoin(
    id = ProjectJoinId(id),
    projectId = ProjectId(projectId),
    roleId = RoleId(roleId),
    candidateId = candidateId?.let(::UserId),
    type = type,
    status = status,
    message = message,
    createdAt = createdAt,
)

fun role(id: String = "r1", slots: Int = 1, occupied: Int = 0) = Role(RoleId(id), "Role $id", slots = slots, occupied = occupied)

fun project(
    id: String = "p1",
    roles: List<Role> = listOf(role()),
    status: ProjectStatus? = ProjectStatus.Recruiting,
    viewerIsInitiator: Boolean = true,
) = Project(
    id = ProjectId(id),
    title = "Project $id",
    type = null,
    sphere = null,
    problem = "",
    goal = "",
    tags = emptyList(),
    initiator = UserSummary(UserId("owner"), "Owner"),
    roles = roles,
    status = status,
    period = null,
    viewerIsInitiator = viewerIsInitiator,
)

fun candidate(id: String = "u1") = Candidate(
    Collaborator(UserSummary(UserId(id), "Name $id"), "", Study(), ""),
    emptyList(),
    hasProfile = true,
)

/** An in-memory [MyJoinsRepository]; each action returns [result] (or throws [failure]) and notes the call. */
class FakeMyJoins(initial: List<ProjectJoin>? = null) : MyJoinsRepository {
    val joins = MutableStateFlow(initial)
    val seenAt = MutableStateFlow<Instant?>(null)
    val calls = mutableListOf<String>()
    var failure: Throwable? = null
    var refreshFailure: Throwable? = null
    var result: ProjectJoin = join()
    var refreshes = 0
    var marked = mutableListOf<Instant>()

    override fun observeMyJoins(): Flow<List<ProjectJoin>?> = joins

    override suspend fun refreshMyJoins(): List<ProjectJoin> {
        refreshes++
        calls += "refresh"
        refreshFailure?.let { throw it }
        return joins.value.orEmpty()
    }

    private fun act(call: String): ProjectJoin {
        calls += call
        failure?.let { throw it }
        return result
    }

    override suspend fun apply(projectId: ProjectId, roleId: RoleId, message: String?): ProjectJoin =
        act("apply ${projectId.value} ${roleId.value} ${message ?: "<none>"}")

    override suspend fun withdraw(id: ProjectJoinId): ProjectJoin = act("withdraw ${id.value}")

    override suspend fun decide(id: ProjectJoinId, accept: Boolean): ProjectJoin = act("decide ${id.value} $accept")

    override fun observeInvitationsSeenAt(): Flow<Instant?> = seenAt

    override fun markInvitationsSeen(upTo: Instant) {
        marked += upTo
    }
}

/** An in-memory [ProjectJoinsRepository]. */
class FakeProjectJoins : ProjectJoinsRepository {
    val responses = MutableStateFlow<Map<ProjectId, List<ProjectResponse>>>(emptyMap())
    val calls = mutableListOf<String>()
    var failure: Throwable? = null
    var refreshFailure: Throwable? = null
    var result: ProjectJoin = join()

    override fun observeProjectResponses(projectId: ProjectId): Flow<List<ProjectResponse>?> =
        responses.map { it[projectId] }

    override suspend fun refreshProjectResponses(projectId: ProjectId): List<ProjectResponse> {
        calls += "refresh ${projectId.value}"
        refreshFailure?.let { throw it }
        return responses.value[projectId].orEmpty()
    }

    override suspend fun decide(id: ProjectJoinId, accept: Boolean): ProjectJoin {
        calls += "decide ${id.value} $accept"
        failure?.let { throw it }
        return result
    }

    override suspend fun invite(projectId: ProjectId, roleId: RoleId, candidateId: UserId, message: String?): ProjectJoin {
        calls += "invite ${projectId.value} ${roleId.value} ${candidateId.value} ${message ?: "<none>"}"
        failure?.let { throw it }
        return result
    }
}

/** [ResponsesProjectSource] over a map of projects; counts every read. */
class FakeProjectSource(initial: List<Project> = emptyList()) : ResponsesProjectSource {
    val projects = MutableStateFlow(initial.associateBy { it.id })
    val getCalls = mutableListOf<ProjectId>()
    val refreshed = mutableListOf<ProjectId>()
    var myProjects: List<Project> = initial
    var myProjectsFailure: Throwable? = null
    val getFailures = mutableMapOf<ProjectId, Throwable>()

    override suspend fun getProject(id: ProjectId): Project? {
        getCalls += id
        getFailures[id]?.let { throw it }
        return projects.value[id]
    }

    override fun observeProject(id: ProjectId): Flow<Project?> = projects.map { it[id] }

    override suspend fun refreshProject(id: ProjectId) {
        refreshed += id
    }

    override suspend fun getMyProjects(): List<Project> {
        myProjectsFailure?.let { throw it }
        return myProjects
    }
}
