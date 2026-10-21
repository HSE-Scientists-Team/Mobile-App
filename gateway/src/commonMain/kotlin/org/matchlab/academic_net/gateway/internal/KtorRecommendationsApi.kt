package org.matchlab.academic_net.gateway.internal

import org.matchlab.academic_net.gateway.RecommendationsApi
import org.matchlab.academic_net.gateway.dto.PeopleListDto
import org.matchlab.academic_net.gateway.dto.ProjectListDto

/** [RecommendationsApi] over the client of a session. */
internal class KtorRecommendationsApi(private val requests: ApiRequests) : RecommendationsApi {
    override suspend fun people(limit: Int): PeopleListDto {
        // API-GAP G33: the spec gives `limit` no bounds; only a number that asks for nothing is refused here.
        require(limit > 0) { "limit must be positive: $limit" }
        return requests.get("recommendations/people", mapOf("limit" to limit.toString()))
    }

    override suspend fun projects(limit: Int): ProjectListDto {
        require(limit > 0) { "limit must be positive: $limit" }
        return requests.get("recommendations/projects", mapOf("limit" to limit.toString()))
    }
}
