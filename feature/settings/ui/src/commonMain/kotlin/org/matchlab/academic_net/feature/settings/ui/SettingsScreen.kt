package org.matchlab.academic_net.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.components.AppButton
import org.matchlab.academic_net.components.AppButtonVariant
import org.matchlab.academic_net.components.AppConfirmDialog
import org.matchlab.academic_net.components.AppSnackbarHost
import org.matchlab.academic_net.components.AppTopBar
import org.matchlab.academic_net.components.LoadingState
import org.matchlab.academic_net.components.MessageState
import org.matchlab.academic_net.components.SectionHeader
import org.matchlab.academic_net.components.ToggleListItem
import org.matchlab.academic_net.components.format.failureText
import org.matchlab.academic_net.feature.settings.ui.generated.resources.Res
import org.matchlab.academic_net.feature.settings.ui.generated.resources.settings_load_error
import org.matchlab.academic_net.feature.settings.ui.generated.resources.settings_open_to_collaboration
import org.matchlab.academic_net.feature.settings.ui.generated.resources.settings_open_to_collaboration_hint
import org.matchlab.academic_net.feature.settings.ui.generated.resources.settings_privacy_title
import org.matchlab.academic_net.feature.settings.ui.generated.resources.settings_profile_missing_hint
import org.matchlab.academic_net.feature.settings.ui.generated.resources.settings_retry
import org.matchlab.academic_net.feature.settings.ui.generated.resources.settings_save_error
import org.matchlab.academic_net.feature.settings.ui.generated.resources.settings_sign_out
import org.matchlab.academic_net.feature.settings.ui.generated.resources.settings_sign_out_cancel
import org.matchlab.academic_net.feature.settings.ui.generated.resources.settings_sign_out_confirm
import org.matchlab.academic_net.feature.settings.ui.generated.resources.settings_sign_out_text
import org.matchlab.academic_net.feature.settings.ui.generated.resources.settings_sign_out_title
import org.matchlab.academic_net.feature.settings.ui.generated.resources.settings_statistics
import org.matchlab.academic_net.feature.settings.ui.generated.resources.settings_statistics_hint
import org.matchlab.academic_net.feature.settings.ui.generated.resources.settings_statistics_title
import org.matchlab.academic_net.feature.settings.ui.generated.resources.settings_title

/**
 * "18 Настройки": the switch "Открыт к сотрудничеству" under "Приватность и безопасность" (it is a field of the profile:
 * whether people can find the user in the people search and in the recommendations), the switch "Статистика
 * использования" in a section of its own, "Статистика", and "Выйти из аккаунта". The last two are not in the design.
 * "Заблокированные пользователи" of the design belongs to the second and third sprints and is not shown.
 *
 * An account that has not filled the profile yet has nothing to put the first switch in (API-GAP G19): it is off and
 * cannot be switched, and the hint under it says to fill the profile first.
 *
 * The statistics switch is a setting of the device and does not depend on the server: it is shown, and works, also while
 * the first setting is loading or has failed to load.
 *
 * @param onNavigateBack the back arrow of the app bar
 * @param onSignOut the user confirmed signing out; the app ends the session and leaves the screen
 */
@Composable
fun SettingsRoute(
    dependencies: SettingsUiDependencies,
    onNavigateBack: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = viewModel {
        SettingsViewModel(
            getOpenToCollaboration = dependencies.getOpenToCollaboration,
            setOpenToCollaboration = dependencies.setOpenToCollaboration,
            observeStatistics = dependencies.observeStatistics,
            setStatistics = dependencies.setStatistics,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val statistics by viewModel.statistics.collectAsStateWithLifecycle()
    val statisticsFailedToSave by viewModel.statisticsFailedToSave.collectAsStateWithLifecycle()

    SettingsScreen(
        state = state,
        statistics = statistics,
        statisticsFailedToSave = statisticsFailedToSave,
        onNavigateBack = onNavigateBack,
        onToggle = viewModel::onToggle,
        onStatisticsToggle = viewModel::onStatisticsToggle,
        onSaveFailureShown = viewModel::onSaveFailureShown,
        onStatisticsSaveFailureShown = viewModel::onStatisticsSaveFailureShown,
        onRetry = viewModel::onRetry,
        onSignOut = onSignOut,
        modifier = modifier,
    )
}

@Composable
internal fun SettingsScreen(
    state: SettingsUiState,
    statistics: Boolean,
    statisticsFailedToSave: Boolean,
    onNavigateBack: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onStatisticsToggle: (Boolean) -> Unit,
    onSaveFailureShown: () -> Unit,
    onStatisticsSaveFailureShown: () -> Unit,
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbars = remember { SnackbarHostState() }
    var confirmingSignOut by rememberSaveable { mutableStateOf(false) }

    val saveError = stringResource(Res.string.settings_save_error)
    SaveFailureEffect(
        failed = (state as? SettingsUiState.Content)?.failedToSave == true,
        onShown = onSaveFailureShown,
        snackbars = snackbars,
        message = saveError,
    )
    // One effect for each switch: a flag that is raised while the other one is still up must not be lost with it.
    SaveFailureEffect(
        failed = statisticsFailedToSave,
        onShown = onStatisticsSaveFailureShown,
        snackbars = snackbars,
        message = saveError,
    )

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxSize()) {
            AppTopBar(
                title = stringResource(Res.string.settings_title),
                onNavigateBack = onNavigateBack,
            )
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionHeader(stringResource(Res.string.settings_privacy_title))
                when (state) {
                    SettingsUiState.Loading -> LoadingState(Modifier.height(120.dp))
                    is SettingsUiState.Error -> MessageState(
                        title = stringResource(Res.string.settings_load_error),
                        modifier = Modifier.height(200.dp),
                        text = failureText(state.cause),
                        actionText = stringResource(Res.string.settings_retry),
                        onAction = onRetry,
                    )
                    is SettingsUiState.Content -> OpenToCollaborationToggle(state, onToggle)
                }
                // A section of its own, 12dp below the end of the first one (the column puts 12dp between its parts).
                SectionHeader(stringResource(Res.string.settings_statistics_title), Modifier.padding(top = 12.dp))
                ToggleListItem(
                    title = stringResource(Res.string.settings_statistics),
                    checked = statistics,
                    onCheckedChange = onStatisticsToggle,
                    subtitle = stringResource(Res.string.settings_statistics_hint),
                )
                AppButton(
                    text = stringResource(Res.string.settings_sign_out),
                    onClick = { confirmingSignOut = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    variant = AppButtonVariant.OutlinedError,
                )
            }
        }
        AppSnackbarHost(
            hostState = snackbars,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp),
        )
    }

    if (confirmingSignOut) {
        AppConfirmDialog(
            title = stringResource(Res.string.settings_sign_out_title),
            text = stringResource(Res.string.settings_sign_out_text),
            confirmText = stringResource(Res.string.settings_sign_out_confirm),
            dismissText = stringResource(Res.string.settings_sign_out_cancel),
            onConfirm = {
                confirmingSignOut = false
                onSignOut()
            },
            onDismiss = { confirmingSignOut = false },
        )
    }
}

/**
 * Tells the user once that a switch could not be saved: while [failed] is up it clears the flag ([onShown]) and shows
 * [message] in [snackbars]. The switch has already gone back to its old value, the message only explains why.
 */
@Composable
private fun SaveFailureEffect(
    failed: Boolean,
    onShown: () -> Unit,
    snackbars: SnackbarHostState,
    message: String,
) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(failed) {
        if (failed) {
            // The flag is cleared first, and the message is shown from the scope of the screen, because clearing the
            // flag restarts this effect and would take the message away.
            onShown()
            scope.launch { snackbars.showSnackbar(message) }
        }
    }
}

/**
 * "Открыт к сотрудничеству": the switch with what it does under it. Without a profile (the value is `null`, API-GAP G19) it
 * is off and takes no taps, and the hint says to fill the profile first.
 */
@Composable
private fun OpenToCollaborationToggle(state: SettingsUiState.Content, onToggle: (Boolean) -> Unit) {
    val hasProfile = state.openToCollaboration != null
    ToggleListItem(
        title = stringResource(Res.string.settings_open_to_collaboration),
        checked = state.openToCollaboration == true,
        onCheckedChange = onToggle,
        subtitle = stringResource(
            if (hasProfile) Res.string.settings_open_to_collaboration_hint else Res.string.settings_profile_missing_hint,
        ),
        enabled = hasProfile && !state.isSaving,
        // The text is dimmed only without a profile; while the value is being saved only the switch is.
        dimText = !hasProfile,
    )
}
