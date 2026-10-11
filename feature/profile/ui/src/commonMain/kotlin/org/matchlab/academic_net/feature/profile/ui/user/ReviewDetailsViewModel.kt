package org.matchlab.academic_net.feature.profile.ui.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.matchlab.academic_net.common.result.LoadResult
import org.matchlab.academic_net.feature.profile.domain.Review
import org.matchlab.academic_net.feature.profile.domain.ReviewId
import org.matchlab.academic_net.feature.profile.domain.usecase.GetReviewUseCase

/** What "09d Отзыв — подробно" shows: the loading, the answer of the server or the failure. */
sealed interface ReviewDetailsUiState {
    data object Loading : ReviewDetailsUiState

    /** The server has no such review. */
    data object NotFound : ReviewDetailsUiState

    data object Error : ReviewDetailsUiState

    data class Content(val review: Review) : ReviewDetailsUiState
}

/** "09d Отзыв — подробно": one review with its whole text. */
class ReviewDetailsViewModel(
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
            state.value = when (val result = getReview(reviewId)) {
                is LoadResult.Found -> ReviewDetailsUiState.Content(result.value)
                LoadResult.NotFound -> ReviewDetailsUiState.NotFound
                is LoadResult.Failure -> ReviewDetailsUiState.Error
            }
        }
    }
}
