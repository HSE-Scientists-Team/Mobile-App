package org.matchlab.academic_net.gateway

import org.matchlab.academic_net.common.paging.PageRequest
import org.matchlab.academic_net.gateway.dto.ProjectJoinDto
import org.matchlab.academic_net.gateway.dto.ProjectJoinInputDto
import org.matchlab.academic_net.gateway.dto.ProjectJoinListDto

/**
 * Applications and invitations (`ProjectJoinRequest`). Every call throws `RemoteFailure` when it fails and lets a
 * `CancellationException` through, like [AccountApi]. Ids go into paths escaped.
 */
interface ProjectJoinsApi {
    /** `POST /api/v1/projects/{projectId}/applications` → 201. API-GAP G60. */
    suspend fun apply(projectId: String, input: ProjectJoinInputDto): ProjectJoinDto

    /** `POST /api/v1/projects/{projectId}/invitations` → 201. API-GAP G60. */
    suspend fun invite(projectId: String, input: ProjectJoinInputDto): ProjectJoinDto

    /** `GET /api/v1/projects/{projectId}/joins` (the initiator's view): `limit`, `offset` always, then what [filter] has. */
    suspend fun listForProject(projectId: String, filter: ProjectJoinFilter, page: PageRequest): ProjectJoinListDto

    /** `GET /api/v1/project-joins` (the viewer's applications and invitations). */
    suspend fun listMine(filter: ProjectJoinFilter, page: PageRequest): ProjectJoinListDto

    /** `POST /api/v1/project-joins/{joinId}/decision` `{"accept": …}` → 200. */
    suspend fun decide(joinId: String, accept: Boolean): ProjectJoinDto

    /** `DELETE /api/v1/project-joins/{joinId}` → 200: the record after it was taken back. */
    suspend fun withdraw(joinId: String): ProjectJoinDto
}

/**
 * The filters of the two lists. `null` and blank values are not sent.
 *
 * @property roleId `role_id`
 * @property status `status`: one of the `ProjectJoinStatus.code`s (not confirmed by the backend, `docs/api-values.md`)
 */
data class ProjectJoinFilter(
    val roleId: String? = null,
    val status: String? = null,
)
