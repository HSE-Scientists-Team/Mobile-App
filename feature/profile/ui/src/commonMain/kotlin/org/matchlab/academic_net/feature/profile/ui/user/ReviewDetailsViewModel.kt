package org.matchlab.academic_net.feature.profile.ui.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.result.LoadResult
import org.matchlab.academic_net.feature.profile.domain.Review
import org.matchlab.academic_net.feature.profile.domain.ReviewId
import org.matchlab.academic_net.feature.profile.domain.usecase.GetReviewUseCase

/** What "09d Отзыв — подробно" shows: the loading, the answer of the server or the failure. */
sealed interface ReviewDetailsUiState {
    data object Loading : ReviewDetailsUiState

    /** There is no such review about this person (it was deleted, or the id is wrong). */
    data object NotFound : ReviewDetailsUiState

    /** The request failed with [cause]; "Повторить" asks again. */
    data class Error(val cause: Throwable) : ReviewDetailsUiState

    data class Content(val review: Review) : ReviewDetailsUiState
}

/**
 * "09d Отзыв — подробно": one review about [userId] with its whole text. API-GAP G14: the API has no request for one
 * review, so the repository takes it from the pages it has loaded and, when the screen was restored and nothing was,
 * looks for it in the newest reviews; a review that is not found is [ReviewDetailsUiState.NotFound].
 */
class ReviewDetailsViewModel(
    private val userId: UserId,
    private val reviewId: ReviewId,
    private val getReview: GetReviewUseCase,
) : ViewModel() {
    private val state = MutableStateFlow<ReviewDetailsUiState>(ReviewDetailsUiState.Loading)

    val uiState: StateFlow<ReviewDetailsUiState> = state.asStateFlow()

    init {
        load()
    }

    /** "Повторить" after a failed load; it does nothing in any other state. */
    fun onRetry() {
        if (state.value !is ReviewDetailsUiState.Error) return
        state.value = ReviewDetailsUiState.Loading
        load()
    }

    private fun load() {
        viewModelScope.launch {
            state.value = when (val result = getReview(userId, reviewId)) {
                is LoadResult.Found -> ReviewDetailsUiState.Content(result.value)
                LoadResult.NotFound -> ReviewDetailsUiState.NotFound
                is LoadResult.Failure -> ReviewDetailsUiState.Error(result.cause)
            }
        }
    }
}
