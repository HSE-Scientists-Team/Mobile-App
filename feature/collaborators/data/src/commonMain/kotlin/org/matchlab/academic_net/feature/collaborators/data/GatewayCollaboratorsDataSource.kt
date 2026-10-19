package org.matchlab.academic_net.feature.collaborators.data

import org.matchlab.academic_net.common.model.Collaborator
import org.matchlab.academic_net.common.paging.Page
import org.matchlab.academic_net.common.paging.PageRequest
import org.matchlab.academic_net.feature.collaborators.domain.CollaboratorQuery
import org.matchlab.academic_net.feature.collaborators.domain.CollaboratorsCapabilities
import org.matchlab.academic_net.gateway.PeopleSearchApi
import org.matchlab.academic_net.gateway.PeopleSearchParams
import org.matchlab.academic_net.gateway.mapping.toCollaboratorPage

/**
 * [CollaboratorsDataSource] over `GET /api/v1/search/people`. Any status but a 2xx and every failure of the network is
 * thrown as the `RemoteFailure` it is: the search has no outcome of its own.
 *
 * The server alone matches and orders the people, so a page is taken as it comes ([toCollaboratorPage]): a record
 * without a `user_id` is not shown but is counted in the offset of the next page, and `total` is the server's count
 * (never less than what has been received). API-GAP G34: the API does not describe the order, the stability of `offset`
 * between two pages or the exactness of `total`.
 *
 * What it sends besides the text and the switches: the type of the person's works as its code in `project_type`, only when
 * exactly one type is chosen (the parameter holds one value: none or two of two send nothing), the code being the app's
 * own (`docs/api-values.md`, [CollaboratorsCapabilities.projectTypeFilter] is on). What it does not send, because the API
 * does not define it: API-GAP G31, the role the search is started for, and what the text is matched against is the
 * server's secret.
 */
class GatewayCollaboratorsDataSource(private val api: PeopleSearchApi) : CollaboratorsDataSource {
    override val capabilities: CollaboratorsCapabilities = CollaboratorsCapabilities(projectTypeFilter = true)

    override suspend fun search(query: CollaboratorQuery, page: PageRequest): Page<Collaborator> =
        api.search(query.toParams(), page).toCollaboratorPage(page)

    // One record is asked for and only the count is read; the total is never less than what has been received.
    override suspend fun count(query: CollaboratorQuery): Int = search(query, CountProbe).total

    private companion object {
        /** The smallest page: only its total is needed. */
        val CountProbe = PageRequest(offset = 0, limit = 1)
    }
}

/**
 * What the query asks for, as the parameters of the people search; a value that restricts nothing is left out.
 * API-GAP G31: `q` goes as typed (only the outer spaces are cut), commas and all. `workTypes` goes as the code of its
 * type when there is exactly one (`project_type` holds one value; two of two mean "any" and send nothing). The filters
 * the sheet "08b" does not have (`interest`, `my_university`) are not sent. `open_only=true` goes when "Открыт к
 * сотрудничеству" is on.
 */
internal fun CollaboratorQuery.toParams(): PeopleSearchParams = PeopleSearchParams(
    q = text.trim().takeIf { it.isNotEmpty() },
    university = filters.university.trim().takeIf { it.isNotEmpty() },
    projectType = filters.workTypes.singleOrNull()?.code,
    skills = filters.keywords.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
    openOnly = filters.openOnly,
    verifiedOnly = filters.verifiedProfile,
    verifiedProjects = filters.hasVerifiedProjects,
    publications = filters.hasPublications,
)
