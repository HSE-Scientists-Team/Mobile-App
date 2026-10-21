package org.matchlab.academic_net.feature.reviews.domain

import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.result.SubmitResult
import org.matchlab.academic_net.utils.suspendRunCatching

/**
 * "Общий проект": the projects [targetId] can be reviewed in (see [ReviewsRepository.getReviewableProjects]). A failed
 * request is a failed [Result]; cancellation is rethrown.
 */
class GetReviewableProjectsUseCase(private val repository: ReviewsRepository) {
    suspend operator fun invoke(targetId: UserId): Result<List<ReviewableProject>> =
        suspendRunCatching { repository.getReviewableProjects(targetId) }
}

/**
 * "Отправить отзыв": the draft is normalized and validated (`Invalid`: nothing is sent), then sent. A refusal of the server
 * is a `Failure` whose cause is a [ReviewRefusal]; any other failure is the `RemoteFailure` itself and may be repeated. A
 * repeat of a review that went through is refused by the server (409), but the screen still guards against it.
 */
class SendReviewUseCase(private val repository: ReviewsRepository) {
    suspend operator fun invoke(targetId: UserId, draft: ReviewDraft): SubmitResult<Unit, ReviewDraftError> {
        val normalized = draft.normalized()
        val errors = normalized.validate()
        if (errors.isNotEmpty()) return SubmitResult.Invalid(errors)
        return suspendRunCatching { repository.sendReview(targetId, normalized) }.fold(
            onSuccess = { SubmitResult.Success(Unit) },
            onFailure = { SubmitResult.Failure(it) },
        )
    }
}
