package org.matchlab.academic_net.feature.profile.ui.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.result.LoadResult
import org.matchlab.academic_net.feature.profile.domain.usecase.GetUserProfileUseCase
import org.matchlab.academic_net.feature.profile.ui.ProfileUiModel
import org.matchlab.academic_net.feature.profile.ui.toUiModel

/** What "09 Профиль коллаборатора" shows: the loading, the answer of the server or the failure. */
sealed interface UserProfileUiState {
    /** The profile is being requested. */
    data object Loading : UserProfileUiState

    /** The server has no such person. */
    data object NotFound : UserProfileUiState

    /** The request failed with [cause] (the screen says why with `failureText`); "Повторить" asks again. */
    data class Error(val cause: Throwable) : UserProfileUiState

    /** The profile was found. */
    data class Content(val profile: ProfileUiModel) : UserProfileUiState
}

/**
 * "09 Профиль коллаборатора": "просматривать профили других клиентов". It loads the profile once,
 * when it is created, and again on [onRetry].
 */
class UserProfileViewModel(
    private val userId: UserId,
    private val getUserProfile: GetUserProfileUseCase,
) : ViewModel() {
    private val state = MutableStateFlow<UserProfileUiState>(UserProfileUiState.Loading)

    val uiState: StateFlow<UserProfileUiState> = state.asStateFlow()

    init {
        load()
    }

    /** The last [onReloadRequested] token that was acted on. */
    private var handledReloadToken = 0

    /**
     * The host says the page is out of date (a review was left on it): reads the profile again when [token] is newer than
     * the last one acted on. A page that is on the screen is read quietly: it stays until the new one comes, and a failed
     * read leaves it as it is (the page is not wrong, only old). A page that failed is read the usual way; one that is
     * still loading is left to that load.
     */
    fun onReloadRequested(token: Int) {
        if (token <= handledReloadToken) return
        handledReloadToken = token
        if (state.value is UserProfileUiState.Content) {
            viewModelScope.launch {
                val result = getUserProfile(userId)
                if (result is LoadResult.Found) state.value = UserProfileUiState.Content(result.value.toUiModel())
            }
        } else if (state.value !is UserProfileUiState.Loading) {
            // A load that is already running reads the page as it is now.
            state.value = UserProfileUiState.Loading
            load()
        }
    }

    /** "Повторить" after a failed load; it does nothing in any other state. */
    fun onRetry() {
        if (state.value !is UserProfileUiState.Error) return
        state.value = UserProfileUiState.Loading
        load()
    }

    private fun load() {
        viewModelScope.launch {
            state.value = when (val result = getUserProfile(userId)) {
                is LoadResult.Found -> UserProfileUiState.Content(result.value.toUiModel())
                LoadResult.NotFound -> UserProfileUiState.NotFound
                is LoadResult.Failure -> UserProfileUiState.Error(result.cause)
            }
        }
    }
}
