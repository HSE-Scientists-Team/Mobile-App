package org.matchlab.academic_net.feature.settings.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.matchlab.academic_net.feature.settings.domain.PrivacyOption
import org.matchlab.academic_net.feature.settings.domain.PrivacySettings
import org.matchlab.academic_net.feature.settings.domain.usecase.GetPrivacySettingsUseCase
import org.matchlab.academic_net.feature.settings.domain.usecase.SetPrivacyOptionUseCase

/** What the settings screen shows: the loading, the failure or the settings themselves. */
sealed interface SettingsUiState {
    /** The settings are being requested. */
    data object Loading : SettingsUiState

    /** The request failed; "Повторить" asks again. */
    data object Error : SettingsUiState

    /**
     * The settings as the switches show them, which may be ahead of the server: a switch moves at
     * once and goes back when saving fails.
     *
     * @property pending the options being saved; their rows ignore taps until the answer comes
     * @property failedToSave the option that could not be saved and went back to its old value;
     *  the screen tells the user once and calls [SettingsViewModel.onSaveFailureShown]
     */
    @Immutable
    data class Content(
        val privacy: PrivacySettings,
        val pending: Set<PrivacyOption> = emptySet(),
        val failedToSave: PrivacyOption? = null,
    ) : SettingsUiState
}

/**
 * "18 Настройки · Приватность и безопасность": loads the settings once, when it is created, and
 * again on [onRetry]. Every switch is saved on its own: a change is applied optimistically and
 * only the changed option is patched with the answer, so two switches saved at the same time
 * cannot overwrite each other.
 */
class SettingsViewModel(
    private val getPrivacySettings: GetPrivacySettingsUseCase,
    private val setPrivacyOption: SetPrivacyOptionUseCase,
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

    /** A switch was flipped: shows the new value at once and saves it. */
    fun onOptionChange(option: PrivacyOption, enabled: Boolean) {
        val content = state.value as? SettingsUiState.Content ?: return
        if (option in content.pending) return
        val previous = content.privacy.isEnabled(option)
        if (previous == enabled) return
        state.value = content.copy(
            privacy = content.privacy.with(option, enabled),
            pending = content.pending + option,
            failedToSave = null,
        )
        viewModelScope.launch {
            setPrivacyOption(option, enabled).fold(
                onSuccess = { saved ->
                    // The answer is the whole state of the server; take only what was asked for.
                    updateContent {
                        it.copy(privacy = it.privacy.with(option, saved.isEnabled(option)), pending = it.pending - option)
                    }
                },
                onFailure = {
                    updateContent {
                        it.copy(privacy = it.privacy.with(option, previous), pending = it.pending - option, failedToSave = option)
                    }
                },
            )
        }
    }

    /** The screen has told the user that [SettingsUiState.Content.failedToSave] failed. */
    fun onSaveFailureShown() = updateContent { it.copy(failedToSave = null) }

    private fun load() {
        viewModelScope.launch {
            state.value = getPrivacySettings().fold(
                onSuccess = { SettingsUiState.Content(it) },
                onFailure = { SettingsUiState.Error },
            )
        }
    }

    private fun updateContent(transform: (SettingsUiState.Content) -> SettingsUiState.Content) {
        state.update { current -> if (current is SettingsUiState.Content) transform(current) else current }
    }
}
