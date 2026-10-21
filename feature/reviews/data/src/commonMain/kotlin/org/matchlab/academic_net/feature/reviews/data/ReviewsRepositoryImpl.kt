package org.matchlab.academic_net.feature.reviews.data

import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.feature.reviews.domain.ReviewDraft
import org.matchlab.academic_net.feature.reviews.domain.ReviewableProject
import org.matchlab.academic_net.feature.reviews.domain.ReviewsRepository

/** [ReviewsRepository] over a [ReviewsDataSource]. */
class ReviewsRepositoryImpl(private val dataSource: ReviewsDataSource) : ReviewsRepository {
    override suspend fun getReviewableProjects(targetId: UserId): List<ReviewableProject> =
        dataSource.getReviewableProjects(targetId)

    override suspend fun sendReview(targetId: UserId, draft: ReviewDraft) = dataSource.sendReview(targetId, draft)
}
