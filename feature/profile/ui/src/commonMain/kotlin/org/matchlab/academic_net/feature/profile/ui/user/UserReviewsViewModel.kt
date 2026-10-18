package org.matchlab.academic_net.feature.profile.ui.user

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.result.LoadResult
import org.matchlab.academic_net.feature.profile.domain.Review
import org.matchlab.academic_net.feature.profile.domain.ReviewsSummary
import org.matchlab.academic_net.feature.profile.domain.usecase.GetReviewsPageUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.GetUserReviewsUseCase

/** What "09b Отзывы о пользователе" shows: the loading, the answer of the server or the failure. */
sealed interface UserReviewsUiState {
    data object Loading : UserReviewsUiState

    /** The server has no such person. */
    data object NotFound : UserReviewsUiState

    /** The first page could not be loaded because of [cause]; "Повторить" asks again. */
    data class Error(val cause: Throwable) : UserReviewsUiState

    /**
     * The numbers over all reviews and the reviews that are loaded, newest first; may be empty.
     *
     * @property items the pages loaded so far, joined; a review that came twice (the list shifted between two requests)
     *  is there once
     * @property hasMore the last page was full, so the server may have more (API-GAP G16: it gives no total)
     * @property isLoadingMore the next page is on its way: the footer of the list says "Загрузка…"
     * @property loadMoreFailure the next page could not be loaded because of this; the footer says so and offers
     *  "Повторить", and no page is asked for until it is pressed
     * @property nextOffset where the next page starts: how many reviews the server has sent so far, a repeat that
     *  [items] dropped included (API-GAP G21, G34: the list may shift between two requests). It is not the size of
     *  [items]: counting only what is kept would start the next page too early and ask for the same reviews again
     */
    @Immutable
    data class Content(
        val summary: ReviewsSummary,
        val items: List<Review>,
        val hasMore: Boolean,
        val isLoadingMore: Boolean = false,
        val loadMoreFailure: Throwable? = null,
        val nextOffset: Int = items.size,
    ) : UserReviewsUiState
}

/**
 * "09b Отзывы о пользователе": "читать отзывы о других клиентах". The first page comes with the numbers
 * ([GetUserReviewsUseCase]); the following ones are asked for as the list is scrolled ([onLoadMore]) from the number of
 * reviews the server has sent so far ([UserReviewsUiState.Content.nextOffset]), which is the `offset` of the API. It
 * counts what came, not what was kept: a review shown once although it came twice is still two places of the server's
 * list.
 *
 * API-GAP G21: the API does not describe the order of the list; it is shown as it comes and taken as the newest first.
 *
 * **One request at a time.** [onLoadMore] is called by the screen each time the end of the list is near, more than once
 * for the same end, so it guards itself: nothing is asked for while a page is on its way, after a failure (only
 * [onRetryLoadMore] asks again) and once the list is over.
 */
class UserReviewsViewModel(
    private val userId: UserId,
    private val getUserReviews: GetUserReviewsUseCase,
    private val getReviewsPage: GetReviewsPageUseCase,
) : ViewModel() {
    private val state = MutableStateFlow<UserReviewsUiState>(UserReviewsUiState.Loading)

    val uiState: StateFlow<UserReviewsUiState> = state.asStateFlow()

    init {
        load()
    }

    /** "Повторить" after a failed first load; it does nothing in any other state. */
    fun onRetry() {
        if (state.value !is UserReviewsUiState.Error) return
        state.value = UserReviewsUiState.Loading
        load()
    }

    /** The end of the list is near: asks for the next page unless that is not possible now (see the class). */
    fun onLoadMore() {
        val content = state.value as? UserReviewsUiState.Content ?: return
        if (content.isLoadingMore || content.loadMoreFailure != null || !content.hasMore) return
        loadNextPage(content)
    }

    /** "Повторить" under the list after the next page failed. */
    fun onRetryLoadMore() {
        val content = state.value as? UserReviewsUiState.Content ?: return
        if (content.isLoadingMore || content.loadMoreFailure == null) return
        loadNextPage(content)
    }

    private fun loadNextPage(content: UserReviewsUiState.Content) {
        state.value = content.copy(isLoadingMore = true, loadMoreFailure = null)
        viewModelScope.launch {
            // The next page starts after everything the server has sent, repeats included (not after what is shown).
            val result = getReviewsPage(userId, offset = content.nextOffset)
            updateContent { current ->
                when (result) {
                    is LoadResult.Found -> current.copy(
                        items = (current.items + result.value.items).distinctBy { it.id },
                        hasMore = result.value.hasMore,
                        isLoadingMore = false,
                        nextOffset = current.nextOffset + result.value.items.size,
                    )
                    // The person is gone since the first page: nothing more to ask for.
                    LoadResult.NotFound -> current.copy(hasMore = false, isLoadingMore = false)
                    is LoadResult.Failure -> current.copy(isLoadingMore = false, loadMoreFailure = result.cause)
                }
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            state.value = when (val result = getUserReviews(userId)) {
                is LoadResult.Found -> UserReviewsUiState.Content(
                    summary = result.value.summary,
                    items = result.value.firstPage.items.distinctBy { it.id },
                    hasMore = result.value.firstPage.hasMore,
                    nextOffset = result.value.firstPage.items.size,
                )
                LoadResult.NotFound -> UserReviewsUiState.NotFound
                is LoadResult.Failure -> UserReviewsUiState.Error(result.cause)
            }
        }
    }

    private fun updateContent(transform: (UserReviewsUiState.Content) -> UserReviewsUiState.Content) {
        state.update { current -> if (current is UserReviewsUiState.Content) transform(current) else current }
    }
}
