package org.matchlab.academic_net.feature.auth.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.time.Duration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.matchlab.academic_net.common.result.SubmitResult
import org.matchlab.academic_net.feature.auth.domain.CodeRequestOutcome
import org.matchlab.academic_net.feature.auth.domain.EmailError
import org.matchlab.academic_net.feature.auth.domain.usecase.RequestEmailCodeUseCase

/** Why the code was not requested. */
enum class SignInError {
    EmptyEmail,
    MalformedEmail,

    /** The domain of the email is not in the registry of the universities. */
    UntrustedDomain,

    /** Too many codes were requested for this address; see [SignInUiState.retryAfterSeconds]. */
    TooManyRequests,
    Network,
}

@Immutable
data class SignInUiState(
    /** As typed; the server gets it trimmed and in lower case. */
    val email: String = "",
    val error: SignInError? = null,
    /** How long to wait with [SignInError.TooManyRequests], in whole seconds rounded up. */
    val retryAfterSeconds: Int? = null,
    val isSending: Boolean = false,
    /**
     * Set once the code is sent: the screen leaves for the code screen of this email and calls
     * [SignInViewModel.onCodeSentHandled].
     */
    val codeSentTo: String? = null,
) {
    /** The address is personal data: logs get no [email] and no [codeSentTo], only whether they are set. */
    override fun toString(): String =
        "SignInUiState(emailLength=${email.length}, error=$error, retryAfterSeconds=$retryAfterSeconds, " +
            "isSending=$isSending, codeSent=${codeSentTo != null})"
}

/**
 * Sign in and registration are one action: the university email gets a code, and the code is
 * entered on the next screen. Which domains are allowed is decided by the server (the registry of
 * the universities); the client only keeps the address from being sent when it cannot be one.
 */
class SignInViewModel(
    private val requestCode: RequestEmailCodeUseCase,
) : ViewModel() {
    private val state = MutableStateFlow(SignInUiState())
    val uiState: StateFlow<SignInUiState> = state.asStateFlow()

    fun onEmailChange(email: String) {
        state.update { it.copy(email = email, error = null, retryAfterSeconds = null) }
    }

    /** "Получить код". */
    fun onGetCodeClick() {
        val current = state.value
        // A code that has been sent but not yet handed to the screen must not be asked for again:
        // the server replaces the code of an address each time.
        if (current.isSending || current.codeSentTo != null) return
        state.update { it.copy(isSending = true, error = null, retryAfterSeconds = null) }
        viewModelScope.launch {
            when (val result = requestCode(current.email)) {
                is SubmitResult.Success -> state.update {
                    when (val outcome = result.value) {
                        is CodeRequestOutcome.Sent ->
                            it.copy(isSending = false, codeSentTo = outcome.challenge.email)
                        CodeRequestOutcome.UntrustedDomain ->
                            it.copy(isSending = false, error = SignInError.UntrustedDomain)
                        is CodeRequestOutcome.TooManyRequests -> it.copy(
                            isSending = false,
                            error = SignInError.TooManyRequests,
                            retryAfterSeconds = outcome.retryAfter.wholeSecondsRoundedUp(),
                        )
                    }
                }
                is SubmitResult.Invalid -> state.update {
                    val error = if (EmailError.Empty in result.errors) SignInError.EmptyEmail else SignInError.MalformedEmail
                    it.copy(isSending = false, error = error)
                }
                is SubmitResult.Failure -> state.update { it.copy(isSending = false, error = SignInError.Network) }
            }
        }
    }

    /**
     * The screen has left for the code screen. Without this the event would stay in the state, and
     * coming back and asking for the code again would not be noticed.
     */
    fun onCodeSentHandled() {
        state.update { it.copy(codeSentTo = null) }
    }
}

/** A wait as whole seconds, rounded up: "через 1 с" is better than "через 0 с" with a refusal. */
internal fun Duration.wholeSecondsRoundedUp(): Int {
    val millis = inWholeMilliseconds
    return if (millis <= 0) 0 else ((millis + 999) / 1000).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}
