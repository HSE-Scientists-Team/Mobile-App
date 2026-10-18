package org.matchlab.academic_net.gateway

import org.matchlab.academic_net.gateway.dto.UserReviewListDto

/**
 * The reviews people wrote about each other. Every call throws `RemoteFailure` when it fails and lets a
 * `CancellationException` through, like [AccountApi].
 */
interface ReviewsApi {
    /**
     * `GET /api/v1/users/{user_id}/reviews?limit&offset`: the reviews about [userId], also when [userId] is the
     * viewer (the API's `/users/me/reviews` is not used). [limit] is 1..[MaxLimit], [offset] 0..[MaxOffset] (a
     * call outside the range is an `IllegalArgumentException`). There is no total (API-GAP G16) and no single
     * review by id (API-GAP G14): the client pages through the list.
     */
    suspend fun userReviews(userId: String, limit: Int, offset: Int): UserReviewListDto

    companion object {
        /** The most reviews one call returns. */
        const val MaxLimit = 100

        /** The largest `offset` the API accepts. */
        const val MaxOffset = 10_000
    }
}
