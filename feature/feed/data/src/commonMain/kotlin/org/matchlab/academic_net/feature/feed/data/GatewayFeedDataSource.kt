package org.matchlab.academic_net.feature.feed.data

import org.matchlab.academic_net.common.model.Collaborator
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.gateway.RecommendationsApi
import org.matchlab.academic_net.gateway.mapping.toCollaborator

/**
 * [FeedDataSource] over `GET /api/v1/recommendations/people?limit`; failures are thrown as `RemoteFailure`.
 *
 * API-GAP G32: the server answers with random people open to collaboration, not with people for the viewer's
 * projects. API-GAP G33: the API does not say whether the viewer can be recommended to themselves, or one person
 * twice, nor what the bounds of `limit` are; the records without a `user_id`, the viewer and the repeats are therefore
 * dropped here, and fewer than [limit] people may be shown.
 *
 * @param viewerId the signed-in user, who is not offered to themselves.
 * @param limit how many people to ask for; positive.
 */
class GatewayFeedDataSource(
    private val api: RecommendationsApi,
    private val viewerId: UserId,
    private val limit: Int = DefaultLimit,
) : FeedDataSource {
    init {
        require(limit > 0) { "The feed must ask for at least one person: $limit" }
    }

    override suspend fun getRecommendedPeople(): List<Collaborator> =
        api.people(limit).items
            .mapNotNull { it.toCollaborator() }
            // API-GAP G33: the API does not say whether the viewer can be recommended to themselves, or twice.
            .filter { it.user.id != viewerId }
            .distinctBy { it.user.id }

    companion object {
        /** How many people the feed asks for: the block is a short list, not a search. */
        const val DefaultLimit = 10
    }
}
