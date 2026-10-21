package org.matchlab.academic_net.feature.reviews.domain

import org.matchlab.academic_net.common.model.UserId

/** What the review form (16) needs from the server. Every function throws the `RemoteFailure` of a failed request. */
interface ReviewsRepository {
    /**
     * The projects the viewer and [targetId] completed together and the viewer has not reviewed [targetId] in yet, the
     * most recently completed first, each project once. The list is empty when there are none (and for the viewer
     * themselves). The server has no such query, so it is put together from the viewer's completed projects and the
     * reviews already written about [targetId].
     */
    suspend fun getReviewableProjects(targetId: UserId): List<ReviewableProject>

    /**
     * Sends [draft] as the viewer's review of [targetId]. The draft must be valid ([ReviewDraft.validate] is empty):
     * a draft that is not is an `IllegalArgumentException`, a bug of the caller.
     *
     * @throws ReviewRefusal when the server refuses a well-formed review.
     */
    suspend fun sendReview(targetId: UserId, draft: ReviewDraft)
}
