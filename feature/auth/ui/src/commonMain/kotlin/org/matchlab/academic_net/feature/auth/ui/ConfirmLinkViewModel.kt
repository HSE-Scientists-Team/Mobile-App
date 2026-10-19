package org.matchlab.academic_net.feature.auth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.matchlab.academic_net.common.result.SubmitResult
import org.matchlab.academic_net.feature.auth.domain.ConfirmationOutcome
import org.matchlab.academic_net.feature.auth.domain.usecase.ConfirmEmailUseCase

/**
 * What [ConfirmLinkScreen] shows.
 *
 * @property problem why the address could not be confirmed; `null` while the request is on its way
 * @property isConfirmed the address is confirmed and the app could not sign in by itself (it does not remember the
 * password of the registration): the screen leaves for the sign-in screen, which says so
 */
data class ConfirmLinkUiState(
    val problem: AuthProblem? = null,
    val isConfirmed: Boolean = false,
) {
    /** A request is on its way (or the answer is being passed on): the screen shows the progress. */
    val isRunning: Boolean get() = problem == null && !isConfirmed
}

/**
 * The address is confirmed with the link of the letter that the system opened in the app (an App Link): the request
 * goes at once, with nothing to type. [email] is the address the user last used (the registration of this run of the
 * app, if there is one, is looked up by it), [link] the link that was opened.
 *
 * - **Confirmed and signed in:** the session appears in the auth state and the app's root leaves this screen; the state
 *   stays "running" until it does. That is the case when the registration was made in this run of the app.
 * - **Confirmed, not signed in:** [ConfirmLinkUiState.isConfirmed]; the screen goes to the sign-in, whose note says
 *   "Почта подтверждена. Войдите".
 * - **A problem:** the link does not work, has expired or is of an organization that may not register
 *   ([AuthProblem.LinkInvalid], [AuthProblem.LinkExpired], [AuthProblem.DomainNotAllowed]), or the call failed.
 *   Only a failed call can be repeated ([onRetry]); the other problems are final and the screen is closed.
 *
 * One request at a time: [onRetry] does nothing while one is on its way or after the address was confirmed (a second
 * request would spend the token again).
 */
class ConfirmLinkViewModel(
    private val email: String,
    private val link: String,
    private val confirmEmail: ConfirmEmailUseCase,
) : ViewModel() {
    private val state = MutableStateFlow(ConfirmLinkUiState())
    val uiState: StateFlow<ConfirmLinkUiState> = state.asStateFlow()

    init {
        confirm()
    }

    /** "Повторить" after a failed call. */
    fun onRetry() {
        val current = state.value
        if (current.problem !is AuthProblem.CallFailed) return
        state.value = ConfirmLinkUiState()
        confirm()
    }

    private fun confirm() {
        viewModelScope.launch {
            when (val result = confirmEmail(email, link)) {
                is SubmitResult.Success -> when (val outcome = result.value) {
                    // Signed in: the app's root leaves this screen. Otherwise the address is confirmed all the same.
                    is ConfirmationOutcome.Confirmed -> if (!outcome.signedIn) state.update { it.copy(isConfirmed = true) }
                    ConfirmationOutcome.InvalidLink -> state.update { it.copy(problem = AuthProblem.LinkInvalid) }
                    ConfirmationOutcome.LinkExpired -> state.update { it.copy(problem = AuthProblem.LinkExpired) }
                    ConfirmationOutcome.DomainNotAllowed -> state.update { it.copy(problem = AuthProblem.DomainNotAllowed) }
                }
                is SubmitResult.Invalid -> state.update { it.copy(problem = AuthProblem.LinkEmpty) }
                is SubmitResult.Failure -> state.update { it.copy(problem = result.cause.asProblem()) }
            }
        }
    }
}
