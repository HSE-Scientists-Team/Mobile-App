package org.matchlab.academic_net.feature.auth.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.components.LoadingState
import org.matchlab.academic_net.components.MessageState
import org.matchlab.academic_net.feature.auth.ui.generated.resources.Res
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_link_close
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_link_failed_title
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_link_retry

/**
 * The address is confirmed from the link of the letter that opened the app: the request goes at once and the screen
 * shows the progress, then either leaves (the session appears and the app's root swaps the screens, or [onConfirmed]
 * sends the user to the sign-in) or says what is wrong.
 *
 * @param email the address the user last used; it picks the registration whose password lets the app sign in by itself
 * @param link the link that was opened, exactly as the system gave it
 * @param onConfirmed the address is confirmed and the app is not signed in: the sign-in screen, with its note
 * @param onClose the link does not work (or the user gave up): the sign-in screen as it was
 */
@Composable
fun ConfirmLinkRoute(
    dependencies: AuthUiDependencies,
    email: String,
    link: String,
    onConfirmed: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = viewModel(key = link) {
        ConfirmLinkViewModel(email = email, link = link, confirmEmail = dependencies.confirmEmail)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.isConfirmed) { if (state.isConfirmed) onConfirmed() }

    val problem = state.problem
    when {
        problem == null -> LoadingState(modifier)
        // A failed call can be made again; the other problems are final.
        problem is AuthProblem.CallFailed -> MessageState(
            title = stringResource(Res.string.auth_link_failed_title),
            modifier = modifier,
            text = problem.text(),
            actionText = stringResource(Res.string.auth_link_retry),
            onAction = viewModel::onRetry,
        )
        else -> MessageState(
            title = stringResource(Res.string.auth_link_failed_title),
            modifier = modifier,
            text = problem.text(),
            actionText = stringResource(Res.string.auth_link_close),
            onAction = onClose,
        )
    }
}
