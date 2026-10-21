package org.matchlab.academic_net.di

import me.tatarka.inject.annotations.Provides
import org.matchlab.academic_net.feature.reviews.data.ReviewsDataSource
import org.matchlab.academic_net.feature.reviews.data.ReviewsRepositoryImpl
import org.matchlab.academic_net.feature.reviews.domain.GetReviewableProjectsUseCase
import org.matchlab.academic_net.feature.reviews.domain.ReviewsRepository
import org.matchlab.academic_net.feature.reviews.domain.SendReviewUseCase
import org.matchlab.academic_net.feature.reviews.ui.ReviewsUiDependencies

/** The reviews feature (16): its repository, use cases and what its form receives. */
internal interface ReviewsModule {
    @Provides
    @SessionScope
    fun reviewsRepository(source: ReviewsDataSource): ReviewsRepository = ReviewsRepositoryImpl(source)

    @Provides
    fun getReviewableProjectsUseCase(repository: ReviewsRepository): GetReviewableProjectsUseCase =
        GetReviewableProjectsUseCase(repository)

    @Provides
    fun sendReviewUseCase(repository: ReviewsRepository): SendReviewUseCase = SendReviewUseCase(repository)

    @Provides
    fun reviewsUiDependencies(
        getReviewableProjects: GetReviewableProjectsUseCase,
        sendReview: SendReviewUseCase,
    ): ReviewsUiDependencies = ReviewsUiDependencies(getReviewableProjects, sendReview)
}
