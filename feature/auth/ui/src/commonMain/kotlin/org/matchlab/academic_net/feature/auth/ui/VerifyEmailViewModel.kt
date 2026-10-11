package org.matchlab.academic_net.feature.auth.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.matchlab.academic_net.common.result.SubmitResult
import org.matchlab.academic_net.feature.auth.domain.CodeRequestOutcome
import org.matchlab.academic_net.feature.auth.domain.CodeVerificationOutcome
import org.matchlab.academic_net.feature.auth.domain.VerificationCodeRules
import org.matchlab.academic_net.feature.auth.domain.usecase.GetLastChallengeUseCase
import org.matchlab.academic_net.feature.auth.domain.usecase.RequestEmailCodeUseCase
import org.matchlab.academic_net.feature.auth.domain.usecase.VerifyEmailCodeUseCase

/** What is wrong with the code or with asking for a new one. */
enum class VerifyError {
    WrongCode,

    /** The code has lived its time, or none was requested. */
    Expired,

    /** The attempts are spent and the code is burnt: only a new one helps. */
    TooManyAttempts,

    /** Too many codes were requested; [VerifyEmailUiState.resendInSeconds] is the wait. */
    TooManyRequests,
    Network,
}

@Immutable
data class VerifyEmailUiState(
    /** The address the code was sent to, as the server normalized it. */
    val email: String,
    /** Only digits, at most [VerificationCodeRules.LENGTH] of them. */
    val code: String = "",
    val error: VerifyError? = null,
    /** How many attempts are left after [VerifyError.WrongCode], `null` while unknown. */
    val attemptsLeft: Int? = null,
    val isVerifying: Boolean = false,
    /** Seconds until a new code may be asked for; `0` when it may be asked for now. */
    val resendInSeconds: Int = 0,
    val isResending: Boolean = false,
    /** The attempts are spent: the field is blocked until a new code is sent. */
    val isLocked: Boolean = false,
) {
    /** The address and the code are personal data and a secret: logs get neither. */
    override fun toString(): String =
        "VerifyEmailUiState(codeLength=${code.length}, error=$error, attemptsLeft=$attemptsLeft, " +
            "isVerifying=$isVerifying, resendInSeconds=$resendInSeconds, isResending=$isResending, " +
            "isLocked=$isLocked)"
}

/**
 * The code from the email: the sixth digit sends it at once, "Подтвердить" does the same for a
 * code that is complete. A successful check does not navigate from here: the session appears in
 * the auth state, and the app's root swaps the screens.
 */
class VerifyEmailViewModel(
    email: String,
    private val verifyCode: VerifyEmailCodeUseCase,
    private val requestCode: RequestEmailCodeUseCase,
    getLastChallenge: GetLastChallengeUseCase,
) : ViewModel() {
    private val state = MutableStateFlow(VerifyEmailUiState(email = email))
    val uiState: StateFlow<VerifyEmailUiState> = state.asStateFlow()

    private var countdown: Job? = null

    init {
        // The challenge is computed against the clock, so a screen opened again later shows the
        // time that is really left.
        val left = getLastChallenge(email)?.resendAfter?.wholeSecondsRoundedUp() ?: 0
        startCountdown(left)
    }

    fun onCodeChange(input: String) {
        val current = state.value
        if (current.isVerifying || current.isLocked) return
        val code = VerificationCodeRules.sanitize(input)
        state.update { it.copy(code = code, error = if (code == it.code) it.error else null) }
        if (code.length == VerificationCodeRules.LENGTH && code != current.code) verify()
    }

    /** "Подтвердить". */
    fun onConfirmClick() = verify()

    /** "Отправить код повторно"; works only when the countdown is over. */
    fun onResendClick() {
        val current = state.value
        if (current.resendInSeconds > 0 || current.isResending || current.isVerifying) return
        state.update { it.copy(isResending = true, error = null) }
        viewModelScope.launch {
            when (val result = requestCode(current.email)) {
                is SubmitResult.Success -> when (val outcome = result.value) {
                    is CodeRequestOutcome.Sent -> {
                        // A new code cancels the old one: the old attempts and the old input are gone.
                        state.update {
                            it.copy(
                                code = "",
                                error = null,
                                attemptsLeft = null,
                                isLocked = false,
                                isResending = false,
                            )
                        }
                        startCountdown(outcome.challenge.resendAfter.wholeSecondsRoundedUp())
                    }
                    is CodeRequestOutcome.TooManyRequests -> {
                        state.update { it.copy(error = VerifyError.TooManyRequests, isResending = false) }
                        startCountdown(outcome.retryAfter.wholeSecondsRoundedUp())
                    }
                    // The domain was accepted a moment ago; the registry changing in between is
                    // not worth a message of its own.
                    CodeRequestOutcome.UntrustedDomain ->
                        state.update { it.copy(error = VerifyError.Network, isResending = false) }
                }
                is SubmitResult.Invalid, is SubmitResult.Failure ->
                    state.update { it.copy(error = VerifyError.Network, isResending = false) }
            }
        }
    }

    private fun verify() {
        val current = state.value
        if (current.isVerifying || current.isLocked || current.code.length != VerificationCodeRules.LENGTH) return
        state.update { it.copy(isVerifying = true, error = null) }
        viewModelScope.launch {
            when (val result = verifyCode(current.email, current.code)) {
                is SubmitResult.Success -> state.update {
                    when (val outcome = result.value) {
                        // The session is in the auth state now; the app's root leaves this screen.
                        // The code is spent, so the screen stays "verifying": a second send of the
                        // same code would only come back as "expired".
                        is CodeVerificationOutcome.Verified -> it
                        is CodeVerificationOutcome.WrongCode -> it.copy(
                            isVerifying = false,
                            code = "",
                            error = VerifyError.WrongCode,
                            attemptsLeft = outcome.attemptsLeft,
                        )
                        CodeVerificationOutcome.Expired -> it.copy(
                            isVerifying = false,
                            code = "",
                            error = VerifyError.Expired,
                            attemptsLeft = null,
                        )
                        CodeVerificationOutcome.TooManyAttempts -> it.copy(
                            isVerifying = false,
                            code = "",
                            error = VerifyError.TooManyAttempts,
                            attemptsLeft = null,
                            isLocked = true,
                        )
                    }
                }
                // The code is complete here, so "incomplete" cannot come back; nothing to show.
                is SubmitResult.Invalid -> state.update { it.copy(isVerifying = false) }
                // The code stays in the field: "Подтвердить" tries again.
                is SubmitResult.Failure -> state.update {
                    it.copy(isVerifying = false, error = VerifyError.Network)
                }
            }
        }
    }

    private fun startCountdown(seconds: Int) {
        countdown?.cancel()
        state.update { it.copy(resendInSeconds = seconds) }
        if (seconds <= 0) return
        countdown = viewModelScope.launch {
            while (state.value.resendInSeconds > 0) {
                delay(1.seconds)
                state.update { it.copy(resendInSeconds = (it.resendInSeconds - 1).coerceAtLeast(0)) }
            }
        }
    }
}
