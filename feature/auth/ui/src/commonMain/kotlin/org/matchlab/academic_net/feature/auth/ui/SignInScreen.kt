package org.matchlab.academic_net.feature.auth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.components.AppButton
import org.matchlab.academic_net.components.AppTextField
import org.matchlab.academic_net.components.AppTopBar
import org.matchlab.academic_net.components.SecurityNote
import org.matchlab.academic_net.feature.auth.ui.generated.resources.Res
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_email_hint
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_email_label
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_error_email_empty
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_error_email_malformed
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_error_network
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_error_too_many_requests_minutes
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_error_too_many_requests_seconds
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_error_untrusted_domain
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_get_code
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_getting_code
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_secure_note
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_sign_in_subtitle
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_sign_in_title

/**
 * Sign in and registration by the email of a university. The design has no such screen: it is
 * built of the components of the design system.
 *
 * @param onCodeSent the code is on its way to the email (as the server normalized it); the app
 *  opens the code screen for it
 */
@Composable
fun SignInRoute(
    dependencies: AuthUiDependencies,
    onCodeSent: (email: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = viewModel { SignInViewModel(dependencies.requestCode) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.codeSentTo) {
        state.codeSentTo?.let { email ->
            onCodeSent(email)
            viewModel.onCodeSentHandled()
        }
    }

    SignInScreen(
        state = state,
        onEmailChange = viewModel::onEmailChange,
        onGetCodeClick = viewModel::onGetCodeClick,
        modifier = modifier,
    )
}

@Composable
internal fun SignInScreen(
    state: SignInUiState,
    onEmailChange: (String) -> Unit,
    onGetCodeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .imePadding(),
    ) {
        // The first screen of the app: there is nowhere to go back to.
        AppTopBar(title = stringResource(Res.string.auth_sign_in_title))
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = stringResource(Res.string.auth_sign_in_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AppTextField(
                value = state.email,
                onValueChange = onEmailChange,
                label = stringResource(Res.string.auth_email_label),
                placeholder = stringResource(Res.string.auth_email_hint),
                enabled = !state.isSending,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onGetCodeClick() }),
                errorText = state.errorText(),
            )
            AppButton(
                text = stringResource(
                    if (state.isSending) Res.string.auth_getting_code else Res.string.auth_get_code,
                ),
                onClick = onGetCodeClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isSending,
            )
            SecurityNote(stringResource(Res.string.auth_secure_note))
        }
    }
}

@Composable
private fun SignInUiState.errorText(): String? = when (error) {
    null -> null
    SignInError.EmptyEmail -> stringResource(Res.string.auth_error_email_empty)
    SignInError.MalformedEmail -> stringResource(Res.string.auth_error_email_malformed)
    SignInError.UntrustedDomain -> stringResource(Res.string.auth_error_untrusted_domain)
    SignInError.Network -> stringResource(Res.string.auth_error_network)
    SignInError.TooManyRequests -> {
        val seconds = retryAfterSeconds ?: 0
        if (seconds < 60) {
            stringResource(Res.string.auth_error_too_many_requests_seconds, seconds.coerceAtLeast(1))
        } else {
            stringResource(Res.string.auth_error_too_many_requests_minutes, (seconds + 59) / 60)
        }
    }
}
