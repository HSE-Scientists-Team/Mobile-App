package org.matchlab.academic_net.gateway

import org.matchlab.academic_net.gateway.dto.PeopleListDto
import org.matchlab.academic_net.gateway.dto.ProjectListDto

/** What the Gateway recommends. Every call throws `RemoteFailure` when it fails and lets a `CancellationException` through. */
interface RecommendationsApi {
    /**
     * `GET /api/v1/recommendations/people?limit`: random people open to collaboration (API-GAP G32: they are not chosen
     * for the viewer's projects). API-GAP G33: the spec gives [limit] no bounds and no default and does not say whether
     * the viewer can be among the people. [limit] must be positive (`IllegalArgumentException`, no request otherwise).
     */
    suspend fun people(limit: Int): PeopleListDto

    /**
     * `GET /api/v1/recommendations/projects?limit`: the projects the server recommends, in its order. The server checks
     * each one again (visible, open, the viewer not a member, a free place) and has a fallback list when its cache is
     * down. The `recommendation` block of the answer (model, factors, scores) is not read. Like [people], [limit] has no
     * bounds in the spec (API-GAP G33) and must be positive (`IllegalArgumentException`, no request otherwise).
     */
    suspend fun projects(limit: Int): ProjectListDto
}
