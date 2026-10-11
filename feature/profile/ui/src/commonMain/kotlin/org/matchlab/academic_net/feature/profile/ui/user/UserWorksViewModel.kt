package org.matchlab.academic_net.feature.profile.ui.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.result.LoadResult
import org.matchlab.academic_net.feature.profile.domain.PortfolioWork
import org.matchlab.academic_net.feature.profile.domain.usecase.GetUserWorksUseCase

/** What "09e Все работы" shows: the loading, the answer of the server or the failure. */
sealed interface UserWorksUiState {
    data object Loading : UserWorksUiState

    /** The server has no such person. */
    data object NotFound : UserWorksUiState

    data object Error : UserWorksUiState

    /** The works of the person, newest first; the list may be empty. */
    data class Content(val works: List<PortfolioWork>) : UserWorksUiState
}

/** "09e Все работы": the full list of the works behind "Все" of the profile. */
class UserWorksViewModel(
    private val userId: UserId,
    private val getUserWorks: GetUserWorksUseCase,
) : ViewModel() {
    private val state = MutableStateFlow<UserWorksUiState>(UserWorksUiState.Loading)

    val uiState: StateFlow<UserWorksUiState> = state.asStateFlow()

    init {
        load()
    }

    /** "Повторить" after a failed load; it does nothing in any other state. */
    fun onRetry() {
        if (state.value !is UserWorksUiState.Error) return
        state.value = UserWorksUiState.Loading
        load()
    }

    private fun load() {
        viewModelScope.launch {
            state.value = when (val result = getUserWorks(userId)) {
                is LoadResult.Found -> UserWorksUiState.Content(result.value)
                LoadResult.NotFound -> UserWorksUiState.NotFound
                is LoadResult.Failure -> UserWorksUiState.Error
            }
        }
    }
}
