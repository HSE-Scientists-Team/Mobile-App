package org.matchlab.academic_net.feature.responses.domain

import kotlinx.coroutines.flow.Flow
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectJoin
import org.matchlab.academic_net.common.model.ProjectJoinId
import org.matchlab.academic_net.common.model.RoleId
import kotlin.time.Instant

/**
 * The viewer's own side (15, 15c): their applications and the invitations they got. One list per session, read whole
 * (pages of 100, at most 10; API-GAP G66: the server's order, a repeat of an id dropped).
 */
interface MyJoinsRepository {
    /** The list as this session knows it: `null` until the first read succeeded. Never reads, never fails. */
    fun observeMyJoins(): Flow<List<ProjectJoin>?>

    /** Reads the whole list again into [observeMyJoins] and returns it. Throws on failure; the known list stays. */
    suspend fun refreshMyJoins(): List<ProjectJoin>

    /**
     * `POST /projects/{id}/applications` with the project, the role and [message] (`null`: none). The record is added
     * to the list. @throws JoinRefusedException 403, 404, 409
     */
    suspend fun apply(projectId: ProjectId, roleId: RoleId, message: String?): ProjectJoin

    /** Takes the viewer's application back; the record in the list is replaced. @throws JoinRefusedException */
    suspend fun withdraw(id: ProjectJoinId): ProjectJoin

    /** Accepts or declines an invitation; the record in the list is replaced. @throws JoinRefusedException */
    suspend fun decide(id: ProjectJoinId, accept: Boolean): ProjectJoin

    /** The time of the newest invitation the viewer has seen on 15; `null` before any. API-GAP G61. */
    fun observeInvitationsSeenAt(): Flow<Instant?>

    /** Moves [observeInvitationsSeenAt] to [upTo] unless it is later already. */
    fun markInvitationsSeen(upTo: Instant)
}
