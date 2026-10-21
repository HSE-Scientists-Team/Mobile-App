package org.matchlab.academic_net.gateway.internal

import org.matchlab.academic_net.common.paging.PageRequest
import org.matchlab.academic_net.gateway.ProjectJoinFilter
import org.matchlab.academic_net.gateway.ProjectJoinsApi
import org.matchlab.academic_net.gateway.dto.ProjectJoinDecisionDto
import org.matchlab.academic_net.gateway.dto.ProjectJoinDto
import org.matchlab.academic_net.gateway.dto.ProjectJoinInputDto
import org.matchlab.academic_net.gateway.dto.ProjectJoinListDto

/** [ProjectJoinsApi] over the client of a session. */
internal class KtorProjectJoinsApi(private val requests: ApiRequests) : ProjectJoinsApi {
    override suspend fun apply(projectId: String, input: ProjectJoinInputDto): ProjectJoinDto =
        requests.post("projects/${pathSegment(projectId)}/applications", input)

    override suspend fun invite(projectId: String, input: ProjectJoinInputDto): ProjectJoinDto =
        requests.post("projects/${pathSegment(projectId)}/invitations", input)

    override suspend fun listForProject(
        projectId: String,
        filter: ProjectJoinFilter,
        page: PageRequest,
    ): ProjectJoinListDto =
        requests.get("projects/${pathSegment(projectId)}/joins", query = filter.query(page))

    override suspend fun listMine(filter: ProjectJoinFilter, page: PageRequest): ProjectJoinListDto =
        requests.get("project-joins", query = filter.query(page))

    override suspend fun decide(joinId: String, accept: Boolean): ProjectJoinDto =
        requests.post("project-joins/${pathSegment(joinId)}/decision", ProjectJoinDecisionDto(accept))

    override suspend fun withdraw(joinId: String): ProjectJoinDto =
        requests.deleteFor("project-joins/${pathSegment(joinId)}")

    /** `limit` and `offset` always go (the bounds are those of [PageRequest]); a blank or missing filter is left out. */
    private fun ProjectJoinFilter.query(page: PageRequest): Map<String, String?> = buildMap {
        put("limit", page.limit.toString())
        put("offset", page.offset.toString())
        roleId?.takeIf { it.isNotBlank() }?.let { put("role_id", it) }
        status?.takeIf { it.isNotBlank() }?.let { put("status", it) }
    }
}
