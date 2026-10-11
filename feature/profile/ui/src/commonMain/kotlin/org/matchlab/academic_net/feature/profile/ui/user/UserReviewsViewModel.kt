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
import org.matchlab.academic_net.feature.profile.domain.ReviewsSummary
import org.matchlab.academic_net.feature.profile.domain.usecase.GetUserReviewsUseCase

/** What "09b Отзывы о пользователе" shows: the loading, the answer of the server or the failure. */
sealed interface UserReviewsUiState {
    data object Loading : UserReviewsUiState

    /** The server has no such person. */
    data object NotFound : UserReviewsUiState

    data object Error : UserReviewsUiState

    /** The numbers over all reviews and the reviews themselves, newest first; may be empty. */
    data class Content(val summary: ReviewsSummary, val items: List<Review>) : UserReviewsUiState
}

/** "09b Отзывы о пользователе": "читать отзывы о других клиентах". */
class UserReviewsViewModel(
    private val userId: UserId,
    private val getUserReviews: GetUserReviewsUseCase,
) : ViewModel() {
    private val state = MutableStateFlow<UserReviewsUiState>(UserReviewsUiState.Loading)

    val uiState: StateFlow<UserReviewsUiState> = state.asStateFlow()

    init {
        load()
    }

    /** "Повторить" after a failed load; it does nothing in any other state. */
    fun onRetry() {
        if (state.value !is UserReviewsUiState.Error) return
        state.value = UserReviewsUiState.Loading
        load()
    }

    private fun load() {
        viewModelScope.launch {
            state.value = when (val result = getUserReviews(userId)) {
                is LoadResult.Found -> UserReviewsUiState.Content(result.value.summary, result.value.items)
                LoadResult.NotFound -> UserReviewsUiState.NotFound
                is LoadResult.Failure -> UserReviewsUiState.Error
            }
        }
    }
}
