package org.matchlab.academic_net.feature.reviews.data

import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.feature.reviews.domain.ReviewDraft
import org.matchlab.academic_net.feature.reviews.domain.ReviewRefusal
import org.matchlab.academic_net.feature.reviews.domain.ReviewableProject
import org.matchlab.academic_net.feature.reviews.domain.ReviewsRepository

/** Where reviews are written: the Gateway ([GatewayReviewsDataSource]). Mirrors [ReviewsRepository]: the same members, the same meaning. */
interface ReviewsDataSource {
    /** The projects the viewer shares with [targetId] and has not reviewed them in. Throws the `RemoteFailure` of a failed request. */
    suspend fun getReviewableProjects(targetId: UserId): List<ReviewableProject>

    /** Sends the review of a valid [draft]. @throws ReviewRefusal 400, 403, 404, 409; else the `RemoteFailure`. No retry. */
    suspend fun sendReview(targetId: UserId, draft: ReviewDraft)
}
