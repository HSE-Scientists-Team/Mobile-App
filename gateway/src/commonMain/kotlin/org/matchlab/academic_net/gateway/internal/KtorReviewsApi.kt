package org.matchlab.academic_net.gateway.internal

import org.matchlab.academic_net.gateway.ReviewsApi
import org.matchlab.academic_net.gateway.dto.UserReviewDto
import org.matchlab.academic_net.gateway.dto.UserReviewInputDto
import org.matchlab.academic_net.gateway.dto.UserReviewListDto

/** [ReviewsApi] over the client of a session. */
internal class KtorReviewsApi(private val requests: ApiRequests) : ReviewsApi {
    override suspend fun userReviews(userId: String, limit: Int, offset: Int): UserReviewListDto {
        require(limit in 1..ReviewsApi.MaxLimit) { "limit must be 1..${ReviewsApi.MaxLimit}: $limit" }
        require(offset in 0..ReviewsApi.MaxOffset) { "offset must be 0..${ReviewsApi.MaxOffset}: $offset" }
        return requests.get(
            "users/${pathSegment(userId)}/reviews",
            mapOf("limit" to limit.toString(), "offset" to offset.toString()),
        )
    }

    override suspend fun create(input: UserReviewInputDto): UserReviewDto = requests.post("users/me/reviews", input)
}
