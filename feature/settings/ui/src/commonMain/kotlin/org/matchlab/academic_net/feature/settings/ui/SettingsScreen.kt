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
import org.matchlab.academic_net.feature.settings.domain.PrivacyOption
import org.matchlab.academic_net.feature.settings.ui.generated.resources.Res
import org.matchlab.academic_net.feature.settings.ui.generated.resources.settings_load_error
import org.matchlab.academic_net.feature.settings.ui.generated.resources.settings_privacy_title
import org.matchlab.academic_net.feature.settings.ui.generated.resources.settings_retry
import org.matchlab.academic_net.feature.settings.ui.generated.resources.settings_save_error
import org.matchlab.academic_net.feature.settings.ui.generated.resources.settings_show_contacts
import org.matchlab.academic_net.feature.settings.ui.generated.resources.settings_show_contacts_hint
import org.matchlab.academic_net.feature.settings.ui.generated.resources.settings_show_projects
import org.matchlab.academic_net.feature.settings.ui.generated.resources.settings_show_projects_hint
import org.matchlab.academic_net.feature.settings.ui.generated.resources.settings_sign_out
import org.matchlab.academic_net.feature.settings.ui.generated.resources.settings_sign_out_cancel
import org.matchlab.academic_net.feature.settings.ui.generated.resources.settings_sign_out_confirm
import org.matchlab.academic_net.feature.settings.ui.generated.resources.settings_sign_out_text
import org.matchlab.academic_net.feature.settings.ui.generated.resources.settings_sign_out_title
import org.matchlab.academic_net.feature.settings.ui.generated.resources.settings_title

/**
 * "18 Настройки · Приватность и безопасность": two switches of what others see and "Выйти из
 * аккаунта", which is not in the design. "Заблокированные пользователи" of the design belongs to
 * the second and third sprints and is not shown.
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
    val viewModel = viewModel { SettingsViewModel(dependencies.getPrivacySettings, dependencies.setPrivacyOption) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsScreen(
        state = state,
        onNavigateBack = onNavigateBack,
        onOptionChange = viewModel::onOptionChange,
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
    onOptionChange: (PrivacyOption, Boolean) -> Unit,
    onSaveFailureShown: () -> Unit,
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbars = remember { SnackbarHostState() }
    var confirmingSignOut by rememberSaveable { mutableStateOf(false) }

    val failedToSave = (state as? SettingsUiState.Content)?.failedToSave
    val saveError = stringResource(Res.string.settings_save_error)
    val scope = rememberCoroutineScope()
    LaunchedEffect(failedToSave) {
        if (failedToSave != null) {
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
                    SettingsUiState.Error -> MessageState(
                        title = stringResource(Res.string.settings_load_error),
                        modifier = Modifier.height(200.dp),
                        actionText = stringResource(Res.string.settings_retry),
                        onAction = onRetry,
                    )
                    is SettingsUiState.Content -> {
                        PrivacyToggle(
                            option = PrivacyOption.ParticipatedProjects,
                            title = stringResource(Res.string.settings_show_projects),
                            subtitle = stringResource(Res.string.settings_show_projects_hint),
                            state = state,
                            onOptionChange = onOptionChange,
                        )
                        PrivacyToggle(
                            option = PrivacyOption.Contacts,
                            title = stringResource(Res.string.settings_show_contacts),
                            subtitle = stringResource(Res.string.settings_show_contacts_hint),
                            state = state,
                            onOptionChange = onOptionChange,
                        )
                    }
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

@Composable
private fun PrivacyToggle(
    option: PrivacyOption,
    title: String,
    subtitle: String,
    state: SettingsUiState.Content,
    onOptionChange: (PrivacyOption, Boolean) -> Unit,
) {
    ToggleListItem(
        title = title,
        checked = state.privacy.isEnabled(option),
        onCheckedChange = { onOptionChange(option, it) },
        subtitle = subtitle,
        enabled = option !in state.pending,
    )
}
