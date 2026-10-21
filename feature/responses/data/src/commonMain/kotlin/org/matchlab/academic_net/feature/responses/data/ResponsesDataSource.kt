package org.matchlab.academic_net.feature.responses.data

import kotlinx.coroutines.flow.Flow
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectJoin
import org.matchlab.academic_net.common.model.ProjectJoinId
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.feature.responses.domain.JoinRefusedException
import org.matchlab.academic_net.feature.responses.domain.MyJoinsRepository
import org.matchlab.academic_net.feature.responses.domain.ProjectJoinsRepository
import org.matchlab.academic_net.feature.responses.domain.ProjectResponse
import org.matchlab.academic_net.feature.responses.domain.ProjectResponsesUnavailableException
import kotlin.time.Instant

/**
 * Where applications and invitations come from: the Gateway ([GatewayResponsesDataSource]). Mirrors both
 * [MyJoinsRepository] (the viewer's side) and [ProjectJoinsRepository] (the initiator's side): the same members, the same
 * meaning. One source serves both, so a decision made on either side is seen by the other.
 */
interface ResponsesDataSource {
    /** The viewer's list as this session knows it: `null` until the first read succeeded. */
    fun observeMyJoins(): Flow<List<ProjectJoin>?>

    /** Reads the viewer's whole list again; throws on failure, the known list stays. */
    suspend fun refreshMyJoins(): List<ProjectJoin>

    /** `POST /projects/{id}/applications`; the record is added to the viewer's list. @throws JoinRefusedException */
    suspend fun apply(projectId: ProjectId, roleId: RoleId, message: String?): ProjectJoin

    /** Takes the viewer's application back. @throws JoinRefusedException */
    suspend fun withdraw(id: ProjectJoinId): ProjectJoin

    /** Accepts or declines a record; every cached list that has it is updated. @throws JoinRefusedException */
    suspend fun decide(id: ProjectJoinId, accept: Boolean): ProjectJoin

    /** The time of the newest invitation the viewer has seen on 15; `null` before any. */
    fun observeInvitationsSeenAt(): Flow<Instant?>

    /** Moves the time up to [upTo] unless it is later already. */
    fun markInvitationsSeen(upTo: Instant)

    /** The records of the project with their candidates: `null` until read. */
    fun observeProjectResponses(projectId: ProjectId): Flow<List<ProjectResponse>?>

    /** Reads the whole list of the project again. @throws ProjectResponsesUnavailableException 403 or 404 */
    suspend fun refreshProjectResponses(projectId: ProjectId): List<ProjectResponse>

    /** `POST /projects/{id}/invitations`. @throws JoinRefusedException */
    suspend fun invite(projectId: ProjectId, roleId: RoleId, candidateId: UserId, message: String?): ProjectJoin
}
