package org.matchlab.academic_net.feature.reviews.ui

import org.matchlab.academic_net.feature.reviews.domain.GetReviewableProjectsUseCase
import org.matchlab.academic_net.feature.reviews.domain.SendReviewUseCase

/**
 * What the screens of the reviews feature need from the outside; the app creates it once and hands it to
 * [ReviewFormRoute].
 */
class ReviewsUiDependencies(
    val getReviewableProjects: GetReviewableProjectsUseCase,
    val sendReview: SendReviewUseCase,
)
