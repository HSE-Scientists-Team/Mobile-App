package org.matchlab.academic_net.feature.responses.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
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
import org.matchlab.academic_net.feature.responses.domain.Candidate
import org.matchlab.academic_net.feature.responses.domain.MyJoinsRepository
import org.matchlab.academic_net.feature.responses.domain.ProjectJoinsRepository
import org.matchlab.academic_net.feature.responses.domain.ProjectResponse
import org.matchlab.academic_net.feature.responses.domain.ResponsesProjectSource
import kotlin.time.Clock
import kotlin.time.Instant

// What the tests of the responses screens share: the main dispatcher, the fixtures and fakes of the three ports the use
// cases stand on. The view models are tested with the real use cases over these fakes.

@OptIn(ExperimentalCoroutinesApi::class)
internal fun installMainDispatcher() = Dispatchers.setMain(UnconfinedTestDispatcher())

@OptIn(ExperimentalCoroutinesApi::class)
internal fun uninstallMainDispatcher() = Dispatchers.resetMain()

internal fun at(seconds: Long): Instant = Instant.fromEpochSeconds(seconds)

/** The clock of the tests: 10 days after the epoch, in UTC. */
internal object FixedClock : Clock {
    val now: Instant = Instant.fromEpochSeconds(10L * 86_400 + 12 * 3600)

    override fun now(): Instant = now
}

internal val UtcZone: () -> TimeZone = { TimeZone.UTC }

internal fun join(
    id: String = "j1",
    projectId: String = "p1",
    roleId: String = "r1",
    candidateId: String? = "u1",
    type: ProjectJoinType? = ProjectJoinType.Application,
    status: ProjectJoinStatus? = ProjectJoinStatus.Pending,
    createdAt: Instant? = null,
) = ProjectJoin(
    id = ProjectJoinId(id),
    projectId = ProjectId(projectId),
    roleId = RoleId(roleId),
    candidateId = candidateId?.let(::UserId),
    type = type,
    status = status,
    message = "",
    createdAt = createdAt,
)

internal fun role(id: String = "r1", slots: Int = 1, occupied: Int = 0, skills: List<String> = emptyList()) =
    Role(RoleId(id), "Роль $id", skills, slots, emptyList(), occupied)

internal fun project(
    id: String = "p1",
    roles: List<Role> = listOf(role()),
    status: ProjectStatus? = ProjectStatus.Recruiting,
    viewerIsInitiator: Boolean = true,
) = Project(
    id = ProjectId(id),
    title = "Проект $id",
    type = null,
    sphere = null,
    problem = "",
    goal = "",
    tags = emptyList(),
    initiator = UserSummary(UserId("owner-$id"), "Автор $id"),
    roles = roles,
    status = status,
    period = null,
    viewerIsInitiator = viewerIsInitiator,
)

internal fun candidate(id: String = "u1", name: String = "Имя $id", hasProfile: Boolean = true) = Candidate(
    Collaborator(UserSummary(UserId(id), if (hasProfile) name else ""), "", Study(), ""),
    emptyList(),
    hasProfile,
)

internal class Gate {
    private val opened = CompletableDeferred<Unit>()

    suspend fun await() = opened.await()

    fun open() {
        opened.complete(Unit)
    }
}

/** The viewer's list in memory: [server] is what `refreshMyJoins` reads into it. Every action is recorded. */
internal class FakeMyJoins(initial: List<ProjectJoin>? = null) : MyJoinsRepository {
    val joins = MutableStateFlow(initial)
    var server: List<ProjectJoin> = initial.orEmpty()
    val seenAt = MutableStateFlow<Instant?>(null)
    val calls = mutableListOf<String>()
    var refreshes = 0
    var refreshFailure: Throwable? = null
    var failure: Throwable? = null
    var gate: Gate? = null
    var result: ((ProjectJoinId, Boolean?) -> ProjectJoin)? = null
    val marked = mutableListOf<Instant>()

    override fun observeMyJoins(): Flow<List<ProjectJoin>?> = joins

    override suspend fun refreshMyJoins(): List<ProjectJoin> {
        refreshes++
        refreshFailure?.let { throw it }
        joins.value = server
        return server
    }

    private suspend fun act(call: String, id: ProjectJoinId, accept: Boolean?, status: ProjectJoinStatus): ProjectJoin {
        calls += call
        gate?.await()
        failure?.let { throw it }
        val current = joins.value.orEmpty().firstOrNull { it.id == id } ?: join(id.value)
        val updated = result?.invoke(id, accept) ?: current.copy(status = status)
        joins.value = joins.value?.map { if (it.id == id) updated else it }
        return updated
    }

    override suspend fun apply(projectId: ProjectId, roleId: RoleId, message: String?): ProjectJoin {
        calls += "apply ${projectId.value} ${roleId.value} ${message ?: "<none>"}"
        gate?.await()
        failure?.let { throw it }
        return join("new", projectId.value, roleId.value).also { joins.value = listOf(it) + joins.value.orEmpty() }
    }

    override suspend fun withdraw(id: ProjectJoinId) = act("withdraw ${id.value}", id, null, ProjectJoinStatus.Withdrawn)

    override suspend fun decide(id: ProjectJoinId, accept: Boolean) =
        act("decide ${id.value} $accept", id, accept, if (accept) ProjectJoinStatus.Accepted else ProjectJoinStatus.Declined)

    override fun observeInvitationsSeenAt(): Flow<Instant?> = seenAt

    override fun markInvitationsSeen(upTo: Instant) {
        marked += upTo
    }
}

/** The initiator's lists in memory. */
internal class FakeProjectJoins : ProjectJoinsRepository {
    val responses = MutableStateFlow<Map<ProjectId, List<ProjectResponse>>>(emptyMap())
    var server: Map<ProjectId, List<ProjectResponse>> = emptyMap()
    val calls = mutableListOf<String>()
    var refreshFailure: Throwable? = null
    var failure: Throwable? = null
    var gate: Gate? = null
    var refreshGate: Gate? = null

    override fun observeProjectResponses(projectId: ProjectId): Flow<List<ProjectResponse>?> = responses.map { it[projectId] }

    override suspend fun refreshProjectResponses(projectId: ProjectId): List<ProjectResponse> {
        calls += "refresh ${projectId.value}"
        refreshGate?.await()
        refreshFailure?.let { throw it }
        server[projectId]?.let { list -> responses.value = responses.value + (projectId to list) }
        return responses.value[projectId].orEmpty()
    }

    override suspend fun decide(id: ProjectJoinId, accept: Boolean): ProjectJoin {
        calls += "decide ${id.value} $accept"
        gate?.await()
        failure?.let { throw it }
        var updated: ProjectJoin? = null
        responses.value = responses.value.mapValues { (_, list) ->
            list.map {
                if (it.join.id == id) {
                    val join = it.join.copy(status = if (accept) ProjectJoinStatus.Accepted else ProjectJoinStatus.Declined)
                    updated = join
                    it.copy(join = join)
                } else {
                    it
                }
            }
        }
        return updated ?: join(id.value)
    }

    override suspend fun invite(projectId: ProjectId, roleId: RoleId, candidateId: UserId, message: String?): ProjectJoin {
        calls += "invite ${projectId.value} ${roleId.value} ${candidateId.value} ${message ?: "<none>"}"
        gate?.await()
        failure?.let { throw it }
        return join("inv", projectId.value, roleId.value, candidateId.value, ProjectJoinType.Invitation)
    }
}

internal class FakeProjectSource(initial: List<Project> = emptyList()) : ResponsesProjectSource {
    val projects = MutableStateFlow(initial.associateBy { it.id })
    var myProjects: List<Project> = initial
    var myProjectsFailure: Throwable? = null
    var myProjectsGate: Gate? = null
    val getCalls = mutableListOf<ProjectId>()
    val getFailures = mutableMapOf<ProjectId, Throwable>()
    val refreshed = mutableListOf<ProjectId>()

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
        myProjectsGate?.await()
        myProjectsFailure?.let { throw it }
        return myProjects
    }
}
