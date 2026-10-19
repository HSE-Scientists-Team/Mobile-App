package org.matchlab.academic_net.feature.responses.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.matchlab.academic_net.common.model.Collaborator
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectJoin
import org.matchlab.academic_net.common.model.ProjectJoinId
import org.matchlab.academic_net.common.model.ProjectJoinStatus
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.Study
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.common.paging.PageRequest
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.feature.responses.domain.Candidate
import org.matchlab.academic_net.feature.responses.domain.JoinRefusal
import org.matchlab.academic_net.feature.responses.domain.JoinRefusedException
import org.matchlab.academic_net.feature.responses.domain.ProjectResponse
import org.matchlab.academic_net.feature.responses.domain.ProjectResponsesUnavailableException
import org.matchlab.academic_net.gateway.ProjectJoinFilter
import org.matchlab.academic_net.gateway.ProjectJoinsApi
import org.matchlab.academic_net.gateway.UserDirectory
import org.matchlab.academic_net.gateway.dto.ProjectJoinDto
import org.matchlab.academic_net.gateway.dto.ProjectJoinInputDto
import org.matchlab.academic_net.gateway.dto.ProjectJoinListDto
import org.matchlab.academic_net.gateway.dto.UserProfileDto
import org.matchlab.academic_net.gateway.mapping.toCollaborator
import org.matchlab.academic_net.gateway.mapping.toProjectJoin
import org.matchlab.academic_net.gateway.mapping.toProjectJoinPage
import org.matchlab.academic_net.utils.suspendRunCatching
import kotlin.time.Instant

/**
 * [ResponsesDataSource] over the Gateway's applications and invitations ([ProjectJoinsApi]) and the public profiles of the
 * candidates ([UserDirectory]). The values the API leaves open (`type`, `status`) are the codes the app defines
 * (`docs/api-values.md`, `ProjectJoinType`, `ProjectJoinStatus`); a code it does not know is `null` and the record is
 * kept.
 *
 * | Member | What it does |
 * |---|---|
 * | `refreshMyJoins` | `GET /project-joins` whole into the cache of the viewer's list: pages of 100, until the server's total is reached and at most 10 pages; a record that cannot be read is skipped, a repeat of an `id` dropped (API-GAP G66); no filter is sent |
 * | `refreshProjectResponses` | `GET /projects/{id}/joins` whole, the same way, then the profile of each distinct candidate (one request per person, API-GAP G47, at most 4 at a time, kept by [UserDirectory]); a person whose profile cannot be read (a failure or "no such person") is a candidate without a profile, a record without a candidate id has no candidate (G55); a 403 or 404 is [ProjectResponsesUnavailableException] |
 * | `apply`, `invite` | `POST /projects/{id}/applications` and `.../invitations` with the project, the role, the candidate (invitation) and the message (API-GAP G60); the application is added to the viewer's list when it is known, else the list is read (best effort; if that fails it stays unknown and the record shows after the first successful read); a 409 of `apply` reads the list again (best effort) before it is thrown, since the application may be on the server already |
 * | `withdraw`, `decide` | `DELETE /project-joins/{id}` and `POST /project-joins/{id}/decision`; the answer replaces the record in every cached list that has it |
 * | `observeInvitationsSeenAt`, `markInvitationsSeen` | the time of the newest invitation seen on 15, kept in memory for the session (API-GAP G61) |
 *
 * **Refusals.** A 403, 404 or 409 of an action is [JoinRefusedException] with [JoinRefusal.NotAllowed], [JoinRefusal.NotFound]
 * or [JoinRefusal.Conflict]; any other failure is thrown as the `RemoteFailure` it is. An answer that cannot be read as a
 * record is [RemoteFailure.Malformed]. An answer to a decision or a withdrawal whose `status` the app does not know gets the
 * status of the action (accepted, declined, withdrawn): the app knows what it just did (API-GAP G59).
 *
 * **Order.** Everything that changes a cache — reads and actions — runs one after another ([lock]), so that a slow read
 * never puts an older record over the one an action has just changed. Assumption: reading the list of a project together
 * with the candidates' profiles holds the lock, so actions wait until it is done. There is no automatic retry after a failure
 * (API-GAP G48: the API has no idempotency keys, a repeat of an application could make a second one).
 */
class GatewayResponsesDataSource(
    private val api: ProjectJoinsApi,
    private val users: UserDirectory,
) : ResponsesDataSource {
    // The viewer's list; null until the first read (or the first application) succeeded.
    private val mine = MutableStateFlow<List<ProjectJoin>?>(null)

    // The lists of the projects the viewer manages that this session has read.
    private val byProject = MutableStateFlow<Map<ProjectId, List<ProjectResponse>>>(emptyMap())

    private val seenAt = MutableStateFlow<Instant?>(null)

    private val lock = Mutex()

    override fun observeMyJoins(): Flow<List<ProjectJoin>?> = mine

    override suspend fun refreshMyJoins(): List<ProjectJoin> = lock.withLock { readMine() }

    /** Reads the viewer's whole list into [mine]; the caller holds [lock]. */
    private suspend fun readMine(): List<ProjectJoin> {
        val joins = readAll { api.listMine(ProjectJoinFilter(), it) }
        mine.value = joins
        return joins
    }

    override suspend fun apply(projectId: ProjectId, roleId: RoleId, message: String?): ProjectJoin = lock.withLock {
        val input = ProjectJoinInputDto(projectId = projectId.value, roleId = roleId.value, message = message)
        val join = try {
            refusing { api.apply(projectId.value, input) }.readable()
        } catch (refused: JoinRefusedException) {
            // 409: the application may be on the server already (a lost answer, another device): the list is read again,
            // best effort, so that "Вы откликнулись" shows it.
            if (refused.refusal == JoinRefusal.Conflict) suspendRunCatching { readMine() }
            throw refused
        }
        if (mine.value == null) {
            // The list is not known yet: it is not made "known" from this one record. It is read now, best effort; if that
            // fails, the list stays unknown and the record shows up after the first successful read.
            suspendRunCatching { readMine() }
        } else {
            mine.update { list -> listOf(join) + list.orEmpty().filter { it.id != join.id } }
        }
        join
    }

    override suspend fun withdraw(id: ProjectJoinId): ProjectJoin = lock.withLock {
        // API-GAP G59: an answer with a status the app does not know is "taken back": that is what was asked.
        val join = refusing { api.withdraw(id.value) }.readable().withStatusOr(ProjectJoinStatus.Withdrawn)
        replaceInCaches(join)
        join
    }

    override suspend fun decide(id: ProjectJoinId, accept: Boolean): ProjectJoin = lock.withLock {
        val byAction = if (accept) ProjectJoinStatus.Accepted else ProjectJoinStatus.Declined
        // API-GAP G59: an answer with a status the app does not know is what was asked.
        val join = refusing { api.decide(id.value, accept) }.readable().withStatusOr(byAction)
        replaceInCaches(join)
        join
    }

    override fun observeInvitationsSeenAt(): Flow<Instant?> = seenAt

    override fun markInvitationsSeen(upTo: Instant) {
        seenAt.update { current -> if (current == null || upTo > current) upTo else current }
    }

    override fun observeProjectResponses(projectId: ProjectId): Flow<List<ProjectResponse>?> =
        byProject.map { it[projectId] }.distinctUntilChanged()

    override suspend fun refreshProjectResponses(projectId: ProjectId): List<ProjectResponse> = lock.withLock {
        val joins = try {
            readAll { api.listForProject(projectId.value, ProjectJoinFilter(), it) }
        } catch (failure: RemoteFailure.Http) {
            if (failure.status == FORBIDDEN || failure.status == NOT_FOUND) throw ProjectResponsesUnavailableException(projectId)
            throw failure
        }
        val candidates = candidatesOf(joins)
        val responses = joins.map { join -> ProjectResponse(join, join.candidateId?.let { candidates[it] }) }
        byProject.update { it + (projectId to responses) }
        responses
    }

    override suspend fun invite(projectId: ProjectId, roleId: RoleId, candidateId: UserId, message: String?): ProjectJoin =
        lock.withLock {
            val input = ProjectJoinInputDto(
                projectId = projectId.value,
                roleId = roleId.value,
                candidateUserId = candidateId.value,
                message = message,
            )
            // The initiator's own invitations are not shown on 07d, so the cached list of the project is left alone.
            refusing { api.invite(projectId.value, input) }.readable()
        }

    /**
     * Reads a list whole: pages of the largest size the API gives, while the server says there is more, at most
     * [MAX_PAGES] pages (1000 records). Records the app cannot read are skipped (`toProjectJoinPage`); a repeat of an `id`
     * (the list may shift between two requests) is dropped, the first stays.
     */
    private suspend fun readAll(read: suspend (PageRequest) -> ProjectJoinListDto): List<ProjectJoin> {
        val joins = LinkedHashMap<ProjectJoinId, ProjectJoin>()
        var request: PageRequest? = PageRequest(offset = 0, limit = PAGE_SIZE)
        var pages = 0
        while (request != null && pages < MAX_PAGES) {
            val page = read(request).toProjectJoinPage(request)
            page.items.forEach { if (it.id !in joins) joins[it.id] = it }
            pages++
            request = page.next()
        }
        return joins.values.toList()
    }

    /** The candidates of [joins], each distinct person asked for once; a person without a readable profile has none. */
    private suspend fun candidatesOf(joins: List<ProjectJoin>): Map<UserId, Candidate> = coroutineScope {
        joins.mapNotNull { it.candidateId }.distinct()
            .map { id -> async { id to candidateOf(id) } }
            .awaitAll()
            .toMap()
    }

    /** The candidate [id] with the profile the directory has; a failure of the request is a candidate without a profile. */
    private suspend fun candidateOf(id: UserId): Candidate {
        val profile = suspendRunCatching { users.profile(id.value) }.getOrNull()
        if (profile == null) {
            val person = Collaborator(UserSummary(id, ""), university = "", study = Study(null, null), field = "")
            return Candidate(person = person, skills = emptyList(), hasProfile = false)
        }
        return profile.toCandidate(id)
    }

    /** The answer of the server as a record, or [RemoteFailure.Malformed] when it cannot be shown (no id, project or role). */
    private fun ProjectJoinDto.readable(): ProjectJoin = toProjectJoin() ?: throw RemoteFailure.Malformed()

    private fun ProjectJoin.withStatusOr(byAction: ProjectJoinStatus): ProjectJoin =
        if (status == null) copy(status = byAction) else this

    /** The record [join] in every cached list that has a record with its id; the candidates stay as they were. */
    private fun replaceInCaches(join: ProjectJoin) {
        mine.update { list -> list?.map { if (it.id == join.id) join else it } }
        byProject.update { all ->
            all.mapValues { (_, responses) ->
                responses.map { if (it.join.id == join.id) it.copy(join = join) else it }
            }
        }
    }

    /** Runs [block]; a 403, 404 or 409 of the server is [JoinRefusedException], any other failure passes. */
    private inline fun <T> refusing(block: () -> T): T =
        try {
            block()
        } catch (failure: RemoteFailure.Http) {
            throw when (failure.status) {
                FORBIDDEN -> JoinRefusedException(JoinRefusal.NotAllowed)
                NOT_FOUND -> JoinRefusedException(JoinRefusal.NotFound)
                CONFLICT -> JoinRefusedException(JoinRefusal.Conflict)
                else -> failure
            }
        }

    private companion object {
        const val FORBIDDEN = 403
        const val NOT_FOUND = 404
        const val CONFLICT = 409

        /** The lists are read in the largest pages the API gives, up to [MAX_PAGES] of them. */
        const val PAGE_SIZE = PageRequest.MaxLimit
        const val MAX_PAGES = 10
    }
}

/** The candidate of the profile; the id of the request is the candidate's (the profile may leave `user_id` out, G55). */
private fun UserProfileDto.toCandidate(id: UserId): Candidate {
    val person = toCollaborator()
    return Candidate(person = person.copy(user = person.user.copy(id = id)), skills = details.skills, hasProfile = true)
}
