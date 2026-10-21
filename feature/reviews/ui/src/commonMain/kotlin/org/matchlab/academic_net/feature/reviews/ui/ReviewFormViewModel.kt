package org.matchlab.academic_net.feature.reviews.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.result.SubmitResult
import org.matchlab.academic_net.feature.reviews.domain.GetReviewableProjectsUseCase
import org.matchlab.academic_net.feature.reviews.domain.ReviewDraft
import org.matchlab.academic_net.feature.reviews.domain.ReviewDraftError
import org.matchlab.academic_net.feature.reviews.domain.ReviewLimits
import org.matchlab.academic_net.feature.reviews.domain.ReviewRefusal
import org.matchlab.academic_net.feature.reviews.domain.ReviewableProject
import org.matchlab.academic_net.feature.reviews.domain.SendReviewUseCase

/** The common projects the form offers (the picker 16c-16f): being read, read, or not read. */
sealed interface ReviewProjects {
    /** The projects are being requested (16d). */
    data object Loading : ReviewProjects

    /** The request failed with [cause] (16f); "Повторить" asks again. */
    data class Failed(val cause: Throwable) : ReviewProjects

    /**
     * The request succeeded. An empty [projects] is "Нет общих завершённых проектов" (16e): the viewer has nobody to
     * review in this person's projects, or has reviewed them all.
     */
    data class Loaded(val projects: List<ReviewableProject>) : ReviewProjects
}

/** Why "Отправить отзыв" did not go through (16i, 16j, 16k). The form stays as it is. */
sealed interface ReviewSendFailure {
    /** 409: the viewer has already reviewed this person for this project (16i). */
    data object AlreadyReviewed : ReviewSendFailure

    /** 403, 404 or 400: the project is not completed, or one of the two was not on its team (16j). */
    data object NotAllowed : ReviewSendFailure

    /** Anything else (no network, a timeout, a 5xx): 16k "Не удалось отправить отзыв. Проверьте соединение". */
    data class Failed(val cause: Throwable) : ReviewSendFailure
}

/**
 * What 16 / 16b-16l show of the form.
 *
 * @property projects the picker's content
 * @property draft what the user has filled so far
 * @property errors the fields that do not pass the check, after a tap on "Отправить отзыв" (16g); an error goes away
 * when its field is edited
 * @property isSubmitting the review is on its way, or went through and the screen is closing: the fields are inactive,
 * the button shows an arc and a second review cannot be sent
 * @property failure why the last send failed (16i, 16j, 16k); cleared by the next edit and the next send
 * @property isSent the review went through: the screen reports it to the host and closes
 */
@Immutable
data class ReviewFormUiState(
    val projects: ReviewProjects = ReviewProjects.Loading,
    val draft: ReviewDraft = ReviewDraft(),
    val errors: Set<ReviewDraftError> = emptySet(),
    val isSubmitting: Boolean = false,
    val failure: ReviewSendFailure? = null,
    val isSent: Boolean = false,
) {
    /** The project of the [draft], when the picker has it; the field "Проект" shows it. */
    val chosenProject: ReviewableProject?
        get() = (projects as? ReviewProjects.Loaded)?.projects?.firstOrNull { it.id == draft.projectId }

    /** The length of the title as the counter "N / 255" says it: the trimmed title, in Unicode code points. */
    val titleLength: Int get() = draft.titleLength

    /** The length of the comment as the counter "N / 10 000" says it: the trimmed comment, in Unicode code points. */
    val textLength: Int get() = draft.textLength

    /** The title is over its limit: its counter turns red as it is typed. */
    val isTitleOverLimit: Boolean get() = titleLength > ReviewLimits.MaxTitle

    /** The comment is over its limit: its counter turns red as it is typed (16l). */
    val isTextOverLimit: Boolean get() = textLength > ReviewLimits.MaxText
}

/**
 * 16 "Отзыв о работе": the review of a person for a project the two completed together.
 *
 * The common projects are read when the view model is created and again on [onRetryProjects]; when there is exactly one it
 * is chosen at once (the field and the picker stay open to the user). The checks are the domain's ([SendReviewUseCase]
 * answers `Invalid` and sends nothing), and they are shown only after the first tap on "Отправить отзыв" (16g); editing a
 * field takes its error back. [onSubmit] is guarded by [ReviewFormUiState.isSubmitting], which holds after a success as
 * well: the review is made, the screen is closing, and a repeat would be refused by the server (409) anyway. The text a
 * server sends with a refusal is never shown: 409 is [ReviewSendFailure.AlreadyReviewed], 403/404/400 are
 * [ReviewSendFailure.NotAllowed], every other failure is [ReviewSendFailure.Failed].
 *
 * The state carries no localized text: the screen words it.
 */
class ReviewFormViewModel(
    private val targetId: UserId,
    private val getReviewableProjects: GetReviewableProjectsUseCase,
    private val sendReview: SendReviewUseCase,
) : ViewModel() {
    private val state = MutableStateFlow(ReviewFormUiState())
    val uiState: StateFlow<ReviewFormUiState> = state.asStateFlow()

    init {
        loadProjects()
    }

    /** "Повторить" of the picker after a failed read; it does nothing in any other state. */
    fun onRetryProjects() {
        var retry = false
        state.update {
            retry = it.projects is ReviewProjects.Failed
            if (retry) it.copy(projects = ReviewProjects.Loading) else it
        }
        if (retry) loadProjects()
    }

    /** The project the user confirmed in the picker. A project the picker does not offer is ignored. */
    fun onProjectSelected(id: ProjectId) {
        state.update {
            val offered = (it.projects as? ReviewProjects.Loaded)?.projects.orEmpty().any { project -> project.id == id }
            if (it.isSubmitting || !offered) {
                it
            } else {
                it.copy(draft = it.draft.copy(projectId = id), errors = it.errors - ReviewDraftError.ProjectMissing, failure = null)
            }
        }
    }

    /** A tap on a star: [rating] is its number, or 0 for a tap on the chosen one. A mark outside 0..5 is limited. */
    fun onRatingChange(rating: Int) {
        state.update {
            if (it.isSubmitting) {
                it
            } else {
                val limited = rating.coerceIn(0, ReviewLimits.MaxRating)
                it.copy(
                    draft = it.draft.copy(rating = limited),
                    errors = if (limited > 0) it.errors - ReviewDraftError.RatingMissing else it.errors,
                    failure = null,
                )
            }
        }
    }

    /** The text of the title field. */
    fun onTitleChange(title: String) {
        state.update {
            if (it.isSubmitting) {
                it
            } else {
                it.copy(
                    draft = it.draft.copy(title = title),
                    errors = it.errors - ReviewDraftError.TitleBlank - ReviewDraftError.TitleTooLong,
                    failure = null,
                )
            }
        }
    }

    /** The text of the comment field. */
    fun onTextChange(text: String) {
        state.update {
            if (it.isSubmitting) {
                it
            } else {
                it.copy(
                    draft = it.draft.copy(text = text),
                    errors = it.errors - ReviewDraftError.TextBlank - ReviewDraftError.TextTooLong,
                    failure = null,
                )
            }
        }
    }

    /** "Отправить отзыв" (and the check mark of the app bar). One tap is one request: the guard holds after a success. */
    fun onSubmit() {
        var draft: ReviewDraft? = null
        state.update {
            if (it.isSubmitting) {
                it
            } else {
                draft = it.draft
                it.copy(isSubmitting = true, failure = null)
            }
        }
        val sent = draft ?: return
        viewModelScope.launch {
            when (val result = sendReview(targetId, sent)) {
                // The guard stays up: the review is made, and the screen is closing.
                is SubmitResult.Success -> state.update { it.copy(isSent = true) }
                is SubmitResult.Invalid -> state.update { it.copy(isSubmitting = false, errors = result.errors) }
                is SubmitResult.Failure -> state.update { it.copy(isSubmitting = false, failure = result.cause.toSendFailure()) }
            }
        }
    }

    private fun loadProjects() {
        viewModelScope.launch {
            getReviewableProjects(targetId).fold(
                onSuccess = { projects ->
                    state.update {
                        // One common project is not a choice: it is taken, but the field stays open to the user.
                        val draft = if (projects.size == 1 && it.draft.projectId == null) {
                            it.draft.copy(projectId = projects.single().id)
                        } else {
                            it.draft
                        }
                        val errors = if (draft.projectId != null) it.errors - ReviewDraftError.ProjectMissing else it.errors
                        it.copy(projects = ReviewProjects.Loaded(projects), draft = draft, errors = errors)
                    }
                },
                onFailure = { cause -> state.update { it.copy(projects = ReviewProjects.Failed(cause)) } },
            )
        }
    }
}

private fun Throwable.toSendFailure(): ReviewSendFailure = when (this) {
    is ReviewRefusal.AlreadyReviewed -> ReviewSendFailure.AlreadyReviewed
    is ReviewRefusal.NotAllowed -> ReviewSendFailure.NotAllowed
    else -> ReviewSendFailure.Failed(this)
}
