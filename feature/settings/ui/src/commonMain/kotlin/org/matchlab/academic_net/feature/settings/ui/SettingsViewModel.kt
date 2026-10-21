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
import org.matchlab.academic_net.feature.settings.domain.usecase.ObserveStatisticsUseCase
import org.matchlab.academic_net.feature.settings.domain.usecase.SetOpenToCollaborationUseCase
import org.matchlab.academic_net.feature.settings.domain.usecase.SetStatisticsUseCase

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
 * "18 Настройки": two switches that have nothing to do with each other.
 *
 * **"Открыт к сотрудничеству"** (the section "Приватность и безопасность") is on the server: it is loaded once, when the
 * screen is created, and again on [onRetry]. A change is applied optimistically and saved; it goes back when saving
 * fails. While a value is being saved the switch takes no taps ([SettingsUiState.Content.isSaving]), so two saves cannot
 * overtake each other and leave the screen with the older value.
 *
 * **"Статистика использования"** (the section "Статистика") is a setting of the device: it is read from the store at
 * once and never fails to load, so it does not wait for the server setting and shows in every state of it
 * ([uiState]). Its own state is [statistics] and [statisticsFailedToSave], kept apart from [uiState] for that reason.
 * A change is applied optimistically as well and goes back, to what is in force, when the choice could not be saved. A save takes a moment on
 * the device, so the switch is not dimmed; instead a tap during a save is ignored, which gives the same guarantee.
 */
class SettingsViewModel(
    private val getOpenToCollaboration: GetOpenToCollaborationUseCase,
    private val setOpenToCollaboration: SetOpenToCollaborationUseCase,
    observeStatistics: ObserveStatisticsUseCase,
    private val setStatistics: SetStatisticsUseCase,
) : ViewModel() {
    private val state = MutableStateFlow<SettingsUiState>(SettingsUiState.Loading)

    val uiState: StateFlow<SettingsUiState> = state.asStateFlow()

    private val statisticsState = MutableStateFlow(true)

    /**
     * Whether usage statistics are sent from this device, as the switch shows it. It is `true` until the setting answers
     * (the app counts statistics as allowed until the person says otherwise), then follows the setting, and runs ahead
     * of it while a change is being saved.
     */
    val statistics: StateFlow<Boolean> = statisticsState.asStateFlow()

    private val statisticsFailure = MutableStateFlow(false)

    /**
     * The new value of [statistics] could not be saved and the switch went back to the old one; the screen tells the
     * user once (with the message of the other switch, "Не удалось сохранить настройку") and calls
     * [onStatisticsSaveFailureShown].
     */
    val statisticsFailedToSave: StateFlow<Boolean> = statisticsFailure.asStateFlow()

    /** A change of [statistics] is being saved; taps wait out of the way until the answer comes. */
    private var isSavingStatistics = false

    /** The last value the setting itself reported, which is what is in force whatever the switch has shown meanwhile. */
    private var reportedStatistics: Boolean? = null

    init {
        load()
        viewModelScope.launch {
            observeStatistics().collect {
                reportedStatistics = it
                statisticsState.value = it
            }
        }
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

    /**
     * The switch "Статистика использования" was flipped: shows the new value at once and saves it. A flip while a save
     * is on its way, or to the value the switch already has, does nothing; when saving fails the switch goes back to the
     * value it had and [statisticsFailedToSave] is raised.
     */
    fun onStatisticsToggle(enabled: Boolean) {
        val previous = statisticsState.value
        if (isSavingStatistics || previous == enabled) return
        isSavingStatistics = true
        statisticsFailure.value = false
        statisticsState.value = enabled
        viewModelScope.launch {
            try {
                setStatistics(enabled).onFailure {
                    // Back to what is in force: the old value, or the one the setting itself reported (a refusal that
                    // could not be saved is in force all the same, see StatisticsPreference.set).
                    statisticsState.value = reportedStatistics ?: previous
                    statisticsFailure.value = true
                }
            } finally {
                isSavingStatistics = false
            }
        }
    }

    /** The screen has told the user that [statisticsFailedToSave]. */
    fun onStatisticsSaveFailureShown() {
        statisticsFailure.value = false
    }

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
