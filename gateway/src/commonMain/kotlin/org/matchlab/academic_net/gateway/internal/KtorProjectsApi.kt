package org.matchlab.academic_net.gateway.internal

import org.matchlab.academic_net.common.paging.PageRequest
import org.matchlab.academic_net.gateway.ProjectCatalogApi
import org.matchlab.academic_net.gateway.ProjectSearchParams
import org.matchlab.academic_net.gateway.ProjectsApi
import org.matchlab.academic_net.gateway.dto.ProjectDto
import org.matchlab.academic_net.gateway.dto.ProjectListDto
import org.matchlab.academic_net.gateway.dto.ProjectRoleInputDto

/**
 * [ProjectsApi] and [ProjectCatalogApi] over the client of a session. One class serves both interfaces, because they
 * read the same `GET /projects/{id}`.
 */
internal class KtorProjectsApi(private val requests: ApiRequests) : ProjectsApi, ProjectCatalogApi {
    override suspend fun get(id: String): ProjectDto = requests.get(projectPath(id))

    override suspend fun addRole(id: String, input: ProjectRoleInputDto): ProjectDto =
        requests.post("${projectPath(id)}/roles", input)

    override suspend fun search(params: ProjectSearchParams, page: PageRequest): ProjectListDto =
        requests.get(
            "search/projects",
            query = params.query(page),
            repeatedQuery = params.repeatedQuery(),
        )

    private fun projectPath(id: String) = "projects/${pathSegment(id)}"

    /**
     * The parameters with one value. `limit` and `offset` always go (the bounds are those of [PageRequest]); a text
     * only if it is not blank. API-GAP G40, G41, G44, G45: `type`, `status`, `mine`, `profile_user_id` and `duration`
     * are never among them, their values are not defined.
     */
    private fun ProjectSearchParams.query(page: PageRequest): Map<String, String?> = buildMap {
        put("limit", page.limit.toString())
        put("offset", page.offset.toString())
        q?.takeIf { it.isNotBlank() }?.let { put("q", it) }
        field?.takeIf { it.isNotBlank() }?.let { put("field", it) }
    }

    /** The array of the query: each tag is a `tag=…` of its own, in the given order; a blank one is left out. */
    private fun ProjectSearchParams.repeatedQuery(): Map<String, List<String>> =
        mapOf("tag" to tags.filter { it.isNotBlank() })
}
