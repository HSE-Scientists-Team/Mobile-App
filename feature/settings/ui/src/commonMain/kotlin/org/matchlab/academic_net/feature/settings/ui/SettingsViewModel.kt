package org.matchlab.academic_net.feature.settings.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.matchlab.academic_net.feature.settings.domain.ProfileMissingException
import org.matchlab.academic_net.feature.settings.domain.usecase.GetOpenToCollaborationUseCase
import org.matchlab.academic_net.feature.settings.domain.usecase.SetOpenToCollaborationUseCase

/** What the settings screen shows: the loading, the failure or the settings themselves. */
sealed interface SettingsUiState {
    /** The settings are being requested. */
    data object Loading : SettingsUiState

    /** The request failed with [cause] (the screen says why with `failureText`); "Повторить" asks again. */
    data class Error(val cause: Throwable) : SettingsUiState

    /**
     * The settings as the switch shows them, which may be ahead of the server: the switch moves at once and goes back
     * when saving fails.
     *
     * @property openToCollaboration whether people can find the user in the people search and in the recommendations;
     *  `null` when the user has no profile yet (API-GAP G19: the switch is a field of the profile, and an account that
     *  never saved the form of the profile has none): the switch is off and cannot be switched, and a hint says what to
     *  do
     * @property isSaving the new value is being saved; the switch ignores taps until the answer comes
     * @property failedToSave the new value could not be saved and the switch went back to its old value; the screen tells
     *  the user once and calls [SettingsViewModel.onSaveFailureShown]
     */
    @Immutable
    data class Content(
        val openToCollaboration: Boolean?,
        val isSaving: Boolean = false,
        val failedToSave: Boolean = false,
    ) : SettingsUiState
}

/**
 * "18 Настройки · Приватность и безопасность": loads the one setting there is, "Открыт к сотрудничеству", once, when it
 * is created, and again on [onRetry]. A change is applied optimistically and saved; it goes back when saving fails.
 *
 * **One save at a time**: while a value is being saved the switch takes no taps ([SettingsUiState.Content.isSaving]), so
 * two saves cannot overtake each other and leave the screen with the older value.
 */
class SettingsViewModel(
    private val getOpenToCollaboration: GetOpenToCollaborationUseCase,
    private val setOpenToCollaboration: SetOpenToCollaborationUseCase,
) : ViewModel() {
    private val state = MutableStateFlow<SettingsUiState>(SettingsUiState.Loading)

    val uiState: StateFlow<SettingsUiState> = state.asStateFlow()

    init {
        load()
    }

    /** "Повторить" after a failed load; a request that is still running is not doubled. */
    fun onRetry() {
        if (state.value !is SettingsUiState.Error) return
        state.value = SettingsUiState.Loading
        load()
    }

    /** The switch was flipped: shows the new value at once and saves it. */
    fun onToggle(open: Boolean) {
        val content = state.value as? SettingsUiState.Content ?: return
        val previous = content.openToCollaboration ?: return
        if (content.isSaving || previous == open) return
        state.value = content.copy(openToCollaboration = open, isSaving = true, failedToSave = false)
        viewModelScope.launch {
            setOpenToCollaboration(open).fold(
                // The answer is the value the server has now.
                onSuccess = { saved -> updateContent { it.copy(openToCollaboration = saved, isSaving = false) } },
                onFailure = { failure ->
                    updateContent {
                        if (failure is ProfileMissingException) {
                            // There is no profile to put the switch in: it is off and cannot be switched, with a hint.
                            it.copy(openToCollaboration = null, isSaving = false)
                        } else {
                            it.copy(openToCollaboration = previous, isSaving = false, failedToSave = true)
                        }
                    }
                },
            )
        }
    }

    /** The screen has told the user that [SettingsUiState.Content.failedToSave] failed. */
    fun onSaveFailureShown() = updateContent { it.copy(failedToSave = false) }

    private fun load() {
        viewModelScope.launch {
            state.value = getOpenToCollaboration().fold(
                onSuccess = { SettingsUiState.Content(it) },
                onFailure = { SettingsUiState.Error(it) },
            )
        }
    }

    private fun updateContent(transform: (SettingsUiState.Content) -> SettingsUiState.Content) {
        state.update { current -> if (current is SettingsUiState.Content) transform(current) else current }
    }
}
