package org.matchlab.academic_net.feature.profile.ui.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.result.LoadResult
import org.matchlab.academic_net.feature.profile.domain.PortfolioWork
import org.matchlab.academic_net.feature.profile.domain.UserWorks
import org.matchlab.academic_net.feature.profile.domain.usecase.GetUserWorksUseCase

/** What "09e Все работы" shows: the loading, the answer of the server or the failure. */
sealed interface UserWorksUiState {
    data object Loading : UserWorksUiState

    /** The server has no such person. */
    data object NotFound : UserWorksUiState

    /** The request failed with [cause]; "Повторить" asks again. */
    data class Error(val cause: Throwable) : UserWorksUiState

    /**
     * The works of the person, newest first; the list may be empty.
     *
     * @property works each work once: the list of the screen is keyed by the id of a work, and a key used twice would
     *  crash it, so a work that came twice (whatever the server does) is kept once
     * @property isViewer the works are the viewer's own: a card opens the work for editing instead of its link
     */
    data class Content(val works: List<PortfolioWork>, val isViewer: Boolean) : UserWorksUiState
}

/**
 * "09e Все работы": the full list of the works behind "Все" of the profile.
 *
 * The list of the viewer's own works is edited from here (a card opens the form of the work), so it is asked for again
 * when the screen is shown after the form ([onScreenShown]); the list of somebody else's works does not change under the
 * viewer's hands and is loaded once.
 */
class UserWorksViewModel(
    private val userId: UserId,
    private val getUserWorks: GetUserWorksUseCase,
) : ViewModel() {
    private val state = MutableStateFlow<UserWorksUiState>(UserWorksUiState.Loading)

    val uiState: StateFlow<UserWorksUiState> = state.asStateFlow()

    private var refreshJob: Job? = null

    init {
        load()
    }

    /** "Повторить" after a failed load; it does nothing in any other state. */
    fun onRetry() {
        if (state.value !is UserWorksUiState.Error) return
        state.value = UserWorksUiState.Loading
        load()
    }

    /**
     * The screen is shown (again). On the viewer's own list that is already there the works are asked for again, quietly:
     * the list on the screen stays until the answer comes, and a failed refresh is not told. The first load is not
     * doubled (the list is not there yet), and a refresh that is still running is not started again.
     */
    fun onScreenShown() {
        val content = state.value as? UserWorksUiState.Content ?: return
        if (!content.isViewer || refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            val result = getUserWorks(userId)
            if (result is LoadResult.Found) {
                state.value = result.value.toContent()
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            state.value = when (val result = getUserWorks(userId)) {
                is LoadResult.Found -> result.value.toContent()
                LoadResult.NotFound -> UserWorksUiState.NotFound
                is LoadResult.Failure -> UserWorksUiState.Error(result.cause)
            }
        }
    }

    private fun UserWorks.toContent() = UserWorksUiState.Content(items.distinctBy { it.id }, isViewer)
}
