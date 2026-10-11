package org.matchlab.academic_net.feature.profile.domain.usecase

import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.result.LoadResult
import org.matchlab.academic_net.feature.profile.domain.PortfolioWork
import org.matchlab.academic_net.feature.profile.domain.ProfileRepository
import org.matchlab.academic_net.feature.profile.domain.Review
import org.matchlab.academic_net.feature.profile.domain.ReviewId
import org.matchlab.academic_net.feature.profile.domain.UserProfile
import org.matchlab.academic_net.feature.profile.domain.UserReviews
import org.matchlab.academic_net.utils.suspendRunCatching

/**
 * Runs the request and turns its answer into a [LoadResult]: `null` is [LoadResult.NotFound], a
 * thrown error is [LoadResult.Failure]. Cancellation of the caller is not a failure and is rethrown.
 */
internal suspend inline fun <T : Any> loadResult(block: () -> T?): LoadResult<T> =
    suspendRunCatching(block).fold(
        onSuccess = { value -> if (value == null) LoadResult.NotFound else LoadResult.Found(value) },
        onFailure = { LoadResult.Failure(it) },
    )

/** "Просматривать профили других клиентов": the page of one person. */
class GetUserProfileUseCase(private val repository: ProfileRepository) {
    suspend operator fun invoke(id: UserId): LoadResult<UserProfile> = loadResult { repository.getProfile(id) }
}

/** "Работы и публикации": all works of one person. */
class GetUserWorksUseCase(private val repository: ProfileRepository) {
    suspend operator fun invoke(id: UserId): LoadResult<List<PortfolioWork>> =
        loadResult { repository.getWorks(id) }
}

/** "Читать отзывы": all reviews about one person with their numbers. */
class GetUserReviewsUseCase(private val repository: ProfileRepository) {
    suspend operator fun invoke(id: UserId): LoadResult<UserReviews> = loadResult { repository.getReviews(id) }
}

/** One review in full. */
class GetReviewUseCase(private val repository: ProfileRepository) {
    suspend operator fun invoke(id: ReviewId): LoadResult<Review> = loadResult { repository.getReview(id) }
}
