package org.matchlab.academic_net.gateway

import org.matchlab.academic_net.common.paging.PageRequest
import org.matchlab.academic_net.gateway.dto.ProjectDto
import org.matchlab.academic_net.gateway.dto.ProjectListDto

/**
 * Everybody's projects, as the search (02, 03) and the page of a project (04) read them. Every call throws
 * `RemoteFailure` when it fails and lets a `CancellationException` through, like [AccountApi].
 */
interface ProjectCatalogApi {
    /**
     * `GET /api/v1/search/projects`: `limit` and `offset` of [page] always, then what [params] has.
     *
     * Never sent, because the API does not define their values: `type` (API-GAP G40), `status` (G41), `mine` and
     * `profile_user_id` (G44), `duration` (G45).
     */
    suspend fun search(params: ProjectSearchParams, page: PageRequest): ProjectListDto

    /** `GET /api/v1/projects/{id}` → 200. [id] goes into the path escaped. */
    suspend fun get(id: String): ProjectDto
}

/**
 * What the project search sends besides the page. `null`, blank strings and blank tags are not sent.
 *
 * @property q the search text as the user typed it (API-GAP G53: what the server matches it against is not described).
 * @property field the name of a field of science, as text (API-GAP G46).
 * @property tags `tag=…&tag=…`, in this order (API-GAP G53: "all of them" or "any of them" is not described).
 */
data class ProjectSearchParams(
    val q: String? = null,
    val field: String? = null,
    val tags: List<String> = emptyList(),
)
