package org.matchlab.academic_net.feature.responses.data

import kotlinx.coroutines.flow.Flow
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectJoin
import org.matchlab.academic_net.common.model.ProjectJoinId
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.feature.responses.domain.MyJoinsRepository
import org.matchlab.academic_net.feature.responses.domain.ProjectJoinsRepository
import org.matchlab.academic_net.feature.responses.domain.ProjectResponse
import kotlin.time.Instant

/**
 * The one repository of the responses feature: it serves both sides, the viewer's ([MyJoinsRepository]) and the
 * initiator's ([ProjectJoinsRepository]), over the same [dataSource]. One instance per session, so that a decision made on
 * one side is seen on the other.
 */
class ResponsesRepositoryImpl(private val dataSource: ResponsesDataSource) : MyJoinsRepository, ProjectJoinsRepository {
    override fun observeMyJoins(): Flow<List<ProjectJoin>?> = dataSource.observeMyJoins()

    override suspend fun refreshMyJoins(): List<ProjectJoin> = dataSource.refreshMyJoins()

    override suspend fun apply(projectId: ProjectId, roleId: RoleId, message: String?): ProjectJoin =
        dataSource.apply(projectId, roleId, message)

    override suspend fun withdraw(id: ProjectJoinId): ProjectJoin = dataSource.withdraw(id)

    override suspend fun decide(id: ProjectJoinId, accept: Boolean): ProjectJoin = dataSource.decide(id, accept)

    override fun observeInvitationsSeenAt(): Flow<Instant?> = dataSource.observeInvitationsSeenAt()

    override fun markInvitationsSeen(upTo: Instant) = dataSource.markInvitationsSeen(upTo)

    override fun observeProjectResponses(projectId: ProjectId): Flow<List<ProjectResponse>?> =
        dataSource.observeProjectResponses(projectId)

    override suspend fun refreshProjectResponses(projectId: ProjectId): List<ProjectResponse> =
        dataSource.refreshProjectResponses(projectId)

    override suspend fun invite(projectId: ProjectId, roleId: RoleId, candidateId: UserId, message: String?): ProjectJoin =
        dataSource.invite(projectId, roleId, candidateId, message)
}
