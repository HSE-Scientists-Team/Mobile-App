package org.matchlab.academic_net.gateway

import org.matchlab.academic_net.common.paging.PageRequest
import org.matchlab.academic_net.gateway.dto.ProjectDto
import org.matchlab.academic_net.gateway.dto.ProjectListDto

/**
 * Everybody's projects, as the search (02, 03), the list "Я инициатор" (06) and the page of a project (04) read them.
 * Every call throws `RemoteFailure` when it fails and lets a `CancellationException` through, like [AccountApi].
 */
interface ProjectCatalogApi {
    /**
     * `GET /api/v1/search/projects`: `limit` and `offset` of [page] always, then what [params] has.
     *
     * Only the parameters of the search are sent: [ProjectSearchParams.mine] and [ProjectSearchParams.status] are not (the
     * search is the app's search screen, which has no use for them), nor is `profile_user_id` (its meaning is not
     * described).
     */
    suspend fun search(params: ProjectSearchParams, page: PageRequest): ProjectListDto

    /**
     * `GET /api/v1/projects` ("Каталог и мои проекты"): the same parameters as [search], the same answer, and also
     * [ProjectSearchParams.mine] and [ProjectSearchParams.status] when they are set (`docs/api-values.md`; the filters
     * need a signed-in session). `profile_user_id` is never sent: its meaning is not described.
     */
    suspend fun list(params: ProjectSearchParams, page: PageRequest): ProjectListDto

    /** `GET /api/v1/projects/{id}` → 200. [id] goes into the path escaped. */
    suspend fun get(id: String): ProjectDto
}

/**
 * What the project search and the catalog send besides the page. `null`, blank strings and blank tags are not sent.
 *
 * @property q the search text as the user typed it (API-GAP G53: what the server matches it against is not described).
 * @property type the code of a project type, the `ProjectType.code` (docs/api-values.md); the parameter holds one value.
 * @property field the name of a sphere, the `Sphere.id` (docs/api-values.md).
 * @property duration the code of a duration, the `ProjectDuration.code` (docs/api-values.md); the parameter holds one
 *  value.
 * @property tags `tag=…&tag=…`, in this order (API-GAP G53: "all of them" or "any of them" is not described).
 * @property mine the viewer's relation to the projects, one of the [org.matchlab.academic_net.gateway.contract.ProjectMineFilter]
 *  values; sent by [ProjectCatalogApi.list] only, never by [ProjectCatalogApi.search].
 * @property status the code of a project status, the `ProjectStatus.code` (`completed`, ...); sent by
 *  [ProjectCatalogApi.list] only, never by [ProjectCatalogApi.search].
 */
data class ProjectSearchParams(
    val q: String? = null,
    val type: String? = null,
    val field: String? = null,
    val duration: String? = null,
    val tags: List<String> = emptyList(),
    val mine: String? = null,
    val status: String? = null,
)
