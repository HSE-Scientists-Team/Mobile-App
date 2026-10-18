package org.matchlab.academic_net.gateway

import org.matchlab.academic_net.gateway.dto.PeopleListDto

/** What the Gateway recommends. Every call throws `RemoteFailure` when it fails and lets a `CancellationException` through. */
interface RecommendationsApi {
    /**
     * `GET /api/v1/recommendations/people?limit`: random people open to collaboration (API-GAP G32: they are not chosen
     * for the viewer's projects). API-GAP G33: the spec gives [limit] no bounds and no default and does not say whether
     * the viewer can be among the people. [limit] must be positive (`IllegalArgumentException`, no request otherwise).
     */
    suspend fun people(limit: Int): PeopleListDto
}
