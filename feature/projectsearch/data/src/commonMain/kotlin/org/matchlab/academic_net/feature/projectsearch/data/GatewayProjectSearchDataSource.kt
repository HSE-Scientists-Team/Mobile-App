package org.matchlab.academic_net.feature.projectsearch.data

import org.matchlab.academic_net.common.paging.Page
import org.matchlab.academic_net.common.paging.PageRequest
import org.matchlab.academic_net.feature.projectsearch.domain.FoundProject
import org.matchlab.academic_net.feature.projectsearch.domain.ProjectQuery
import org.matchlab.academic_net.feature.projectsearch.domain.ProjectSearchCapabilities
import org.matchlab.academic_net.gateway.ProjectCatalogApi
import org.matchlab.academic_net.gateway.ProjectSearchParams
import org.matchlab.academic_net.gateway.mapping.ProjectAssembler
import org.matchlab.academic_net.gateway.mapping.pageOf

/**
 * [ProjectSearchDataSource] over `GET /api/v1/search/projects`. Any status but a 2xx and every failure of the network is
 * thrown as the `RemoteFailure` it is: the search has no outcome of its own.
 *
 * The server alone matches and orders the projects, so a page is taken as it comes: the [assembler] reads the projects
 * and looks up their owners (API-GAP G47), a project without an `id` or an `owner_user_id` is not shown but is counted in
 * the offset of the next page, and `total` is the server's count (never less than what has been received). The owner's
 * university is the line under the author's name. A failure of the owners' request, other than "no such person", fails
 * the whole page.
 *
 * Nothing is filtered on the client: the viewer's own projects and projects in any status may be among the answer
 * (API-GAP G53: the API does not describe what the search includes, its order, or the stability of `offset` between two
 * pages).
 *
 * What it sends: `q`, the sphere as its code in `field`, the keywords as `tag`, and the type and the duration as their
 * codes in `type` and `duration`, each only when exactly one chip of its group is chosen (the parameters hold one value:
 * none or several chips send nothing). The codes are the app's own (`docs/api-values.md`, [capabilities] has both filters
 * on). It never sends `status`, `mine` and `profile_user_id`.
 *
 * @param assembler turns the server's projects into the app's, with their owners.
 */
class GatewayProjectSearchDataSource(
    private val api: ProjectCatalogApi,
    private val assembler: ProjectAssembler,
) : ProjectSearchDataSource {
    override val capabilities: ProjectSearchCapabilities =
        ProjectSearchCapabilities(typeFilter = true, durationFilter = true)

    override suspend fun search(query: ProjectQuery, page: PageRequest): Page<FoundProject> {
        val answer = api.search(query.toParams(), page)
        val found = assembler.assemble(answer.items)
            .map { FoundProject(it.project, it.owner?.details?.university.orEmpty().trim()) }
        return pageOf(found, received = answer.items.size, reportedTotal = answer.total, request = page)
    }
}

/**
 * What the query asks for, as the parameters of the project search; a value that restricts nothing is left out.
 * API-GAP G53: `q` goes as typed (only the outer spaces are cut), commas and all. The sphere goes as its code in `field`,
 * the keywords as `tag`. The type and the duration go as their codes only when exactly one is chosen: `type` and
 * `duration` hold one value, so two chips of a group (and none) send nothing, and the server answers for any value
 * (`docs/api-values.md`, §3).
 */
internal fun ProjectQuery.toParams(): ProjectSearchParams = ProjectSearchParams(
    q = text.trim().ifEmpty { null },
    type = filters.types.singleOrNull()?.code,
    field = filters.sphere?.id?.trim()?.ifEmpty { null },
    duration = filters.durations.singleOrNull()?.code,
    tags = filters.keywords.map(String::trim).filter(String::isNotEmpty),
)
