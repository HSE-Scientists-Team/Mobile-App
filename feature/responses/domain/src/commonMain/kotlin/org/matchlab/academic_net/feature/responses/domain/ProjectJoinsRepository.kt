package org.matchlab.academic_net.feature.responses.domain

import kotlinx.coroutines.flow.Flow
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectJoin
import org.matchlab.academic_net.common.model.ProjectJoinId
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.UserId

/** The initiator's side (07d, 09k). The list of each project is read whole, like [MyJoinsRepository]. */
interface ProjectJoinsRepository {
    /** The records of the project with their candidates: `null` until read. Never reads, never fails. */
    fun observeProjectResponses(projectId: ProjectId): Flow<List<ProjectResponse>?>

    /**
     * Reads all the records of the project again, with the candidates' profiles (one request per person, G47; a person
     * whose profile cannot be read has `hasProfile = false`). @throws ProjectResponsesUnavailableException 403 or 404
     */
    suspend fun refreshProjectResponses(projectId: ProjectId): List<ProjectResponse>

    /** Accepts or declines an application; the record in the list is replaced. @throws JoinRefusedException */
    suspend fun decide(id: ProjectJoinId, accept: Boolean): ProjectJoin

    /** `POST /projects/{id}/invitations` with the role, [candidateId] and [message]. @throws JoinRefusedException */
    suspend fun invite(projectId: ProjectId, roleId: RoleId, candidateId: UserId, message: String?): ProjectJoin
}
