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
import org.matchlab.academic_net.feature.settings.ui.generated.resources.settings_title

/**
 * "18 Настройки · Приватность и безопасность": the one switch there is, "Открыт к сотрудничеству" (it is a field of the
 * profile: whether people can find the user in the people search and in the recommendations), and "Выйти из аккаунта",
 * which is not in the design. "Заблокированные пользователи" of the design belongs to the second and third sprints and is
 * not shown.
 *
 * An account that has not filled the profile yet has nothing to put the switch in (API-GAP G19): the switch is off and
 * cannot be switched, and the hint under it says to fill the profile first.
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
    val viewModel = viewModel { SettingsViewModel(dependencies.getOpenToCollaboration, dependencies.setOpenToCollaboration) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsScreen(
        state = state,
        onNavigateBack = onNavigateBack,
        onToggle = viewModel::onToggle,
        onSaveFailureShown = viewModel::onSaveFailureShown,
        onRetry = viewModel::onRetry,
        onSignOut = onSignOut,
        modifier = modifier,
    )
}

@Composable
internal fun SettingsScreen(
    state: SettingsUiState,
    onNavigateBack: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onSaveFailureShown: () -> Unit,
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbars = remember { SnackbarHostState() }
    var confirmingSignOut by rememberSaveable { mutableStateOf(false) }

    val failedToSave = (state as? SettingsUiState.Content)?.failedToSave == true
    val saveError = stringResource(Res.string.settings_save_error)
    val scope = rememberCoroutineScope()
    LaunchedEffect(failedToSave) {
        if (failedToSave) {
            // The switch has already gone back; the message only explains why. The state is
            // cleared first, and the message is shown from the scope of the screen, because
            // clearing the state restarts this effect and would take the message away.
            onSaveFailureShown()
            scope.launch { snackbars.showSnackbar(saveError) }
        }
    }

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
