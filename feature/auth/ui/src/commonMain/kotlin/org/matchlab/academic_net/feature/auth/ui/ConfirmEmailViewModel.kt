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
import org.matchlab.academic_net.feature.auth.domain.ConfirmationOutcome
import org.matchlab.academic_net.feature.auth.domain.RegistrationOutcome
import org.matchlab.academic_net.feature.auth.domain.SignInOutcome
import org.matchlab.academic_net.feature.auth.domain.usecase.ConfirmEmailUseCase
import org.matchlab.academic_net.feature.auth.domain.usecase.GetPendingRegistrationUseCase
import org.matchlab.academic_net.feature.auth.domain.usecase.ResendLetterUseCase
import org.matchlab.academic_net.feature.auth.domain.usecase.SignInPendingUseCase

/** Where the confirmation screen sends the user once there is nothing more to do on it (an event of the state). */
sealed interface ConfirmExit {
    /**
     * To the sign-in screen, with the address: the app cannot sign in by itself (the password of the registration
     * was lost with the process, or the address was confirmed with an account that has another password), so the
     * user types it.
     *
     * @property emailConfirmed the address is known to be confirmed, and the sign-in screen says so ("Почта
     * подтверждена. Войдите"); `false` when the user only said "I've confirmed" and the app cannot check it
     */
    data class SignIn(val emailConfirmed: Boolean) : ConfirmExit

    /** To the registration, with the address: the letter cannot be sent again without the password, which is lost. */
    data object Register : ConfirmExit
}

/**
 * Tells the app where the screen goes: calls the callback of [ConfirmEmailRoute] that fits [this] exit, with the
 * address of the screen. Exactly one of the three is called.
 */
internal fun ConfirmExit.dispatch(
    email: String,
    onSignInRequired: (email: String) -> Unit,
    onEmailConfirmed: (email: String) -> Unit,
    onRegisterAgain: (email: String) -> Unit,
) {
    when (this) {
        is ConfirmExit.SignIn -> if (emailConfirmed) onEmailConfirmed(email) else onSignInRequired(email)
        ConfirmExit.Register -> onRegisterAgain(email)
    }
}

/** What the confirmation screen is doing now. Only one thing at a time. */
enum class ConfirmAction {
    /** "Я подтвердил(а) почту": signing in with the remembered password. */
    SignIn,

    /** "Подтвердить": confirming the address with the pasted link. */
    ConfirmLink,

    /** "Отправить письмо ещё раз". */
    Resend,
}

/**
 * The confirmation of the address.
 *
 * Two kinds of problems are kept apart, as the screen tells them in two places: what came of "Я подтвердил(а)" and
 * of sending the letter again (under that button) and what came of the pasted link (under its field). The text of
 * the link is a secret in part: [toString] says only whether there is any.
 *
 * @property link what the link field holds, as typed or pasted
 * @property submitProblem why "Я подтвердил(а) почту" or "Отправить письмо ещё раз" got the user nowhere
 * @property linkProblem why the link did not confirm the address
 * @property running the action that is on its way, or has been answered and is about to take the screen away; the
 * screen takes no other action while there is one
 * @property resendInSeconds how long until the letter may be sent again, in whole seconds; `0` when it may be now
 * @property exit set when the screen must leave (an event: the screen leaves and tells
 * [ConfirmEmailViewModel.onExitHandled]); `null` otherwise
 */
@Immutable
data class ConfirmUiState(
    val link: String = "",
    val submitProblem: AuthProblem? = null,
    val linkProblem: AuthProblem? = null,
    val running: ConfirmAction? = null,
    val resendInSeconds: Int = 0,
    val exit: ConfirmExit? = null,
) {
    /** Whether the pause before the next letter lasts: the button says how long, and is switched off. */
    val isResendWaiting: Boolean
        get() = resendInSeconds > 0

    /** What the user pasted is a secret (it is the token of the registration): logs get no text of it. */
    override fun toString(): String =
        "ConfirmUiState(hasLink=${link.isNotEmpty()}, submitProblem=$submitProblem, linkProblem=$linkProblem, " +
            "running=$running, resendInSeconds=$resendInSeconds, exit=$exit)"
}

/**
 * "21 Подтвердите почту": the letter with the link was sent to [email]. Opening the link (in the mail app, in the
 * browser) confirms the address on the server; the screen has three ways on:
 *
 * - "Я подтвердил(а) почту" signs in with the password the app still remembers, which only works once the address
 *   is confirmed ([onSignedInClick]);
 * - the link pasted into the field confirms the address from here, and the app signs in by itself
 *   ([onConfirmLinkClick]);
 * - the letter is sent again, once the pause is over ([onResendClick]); its link replaces the old one.
 *
 * A success does not navigate from here: the session appears in the auth state, and the app's root swaps the
 * screens. Where the password is lost (the process was killed after the letter was sent) there is nothing to sign
 * in with, and the screen asks to leave for the sign-in or the registration ([ConfirmExit]).
 *
 * **One thing at a time.** While an action is on its way the others do nothing, and the same after it succeeded,
 * until the screen has gone: a second sign-in would ask for a second session, a second letter would void the link
 * of the first.
 *
 * **The pause before the next letter** is counted here, in whole seconds, from the time the repository says is left
 * for the registration of [email] (a screen opened later shows what is really left; none known is `0`), and again
 * from the pause of each letter that is sent from this screen.
 *
 * @param email the address the letter was sent to, as the registration screen reported it
 */
class ConfirmEmailViewModel(
    private val email: String,
    private val confirmEmail: ConfirmEmailUseCase,
    private val signInPending: SignInPendingUseCase,
    private val resendLetter: ResendLetterUseCase,
    getPendingRegistration: GetPendingRegistrationUseCase,
) : ViewModel() {
    private val state = MutableStateFlow(ConfirmUiState())
    val uiState: StateFlow<ConfirmUiState> = state.asStateFlow()

    private var countdown: Job? = null

    /** The screen was told to leave for another one that does not close it: it may be shown again. */
    private var isLeaving = false

    init {
        startCountdown(getPendingRegistration(email)?.resendAfter?.wholeSecondsRoundedUp() ?: 0)
    }

    fun onLinkChange(link: String) {
        state.update { it.copy(link = link, linkProblem = null) }
    }

    /** "Я подтвердил(а) почту". */
    fun onSignedInClick() {
        if (!begin(ConfirmAction.SignIn)) return
        viewModelScope.launch {
            signInPending(email).fold(
                onSuccess = { outcome ->
                    when (outcome) {
                        // There is no password in memory, so the app cannot know whether the address is confirmed.
                        null -> leave(ConfirmExit.SignIn(emailConfirmed = false))
                        // The session is in the auth state and the app's root leaves this screen; the screen stays
                        // "running" until it does.
                        SignInOutcome.SignedIn -> Unit
                        // The credentials are the right ones, so a refusal means that the link was not opened yet.
                        SignInOutcome.NotConfirmedYet, SignInOutcome.WrongCredentials ->
                            stop { it.copy(submitProblem = AuthProblem.NotConfirmedYet) }
                        SignInOutcome.AccountUnavailable ->
                            stop { it.copy(submitProblem = AuthProblem.AccountUnavailable) }
                    }
                },
                onFailure = { cause -> stop { it.copy(submitProblem = cause.asProblem()) } },
            )
        }
    }

    /** "Подтвердить" (and the "Done" key of the keyboard in the link field). */
    fun onConfirmLinkClick() {
        if (!begin(ConfirmAction.ConfirmLink)) return
        val link = state.value.link
        viewModelScope.launch {
            when (val result = confirmEmail(email, link)) {
                is SubmitResult.Success -> when (val outcome = result.value) {
                    is ConfirmationOutcome.Confirmed ->
                        // Signed in: the app's root leaves this screen. Otherwise the address is confirmed and the
                        // user signs in by hand.
                        if (!outcome.signedIn) leave(ConfirmExit.SignIn(emailConfirmed = true))
                    ConfirmationOutcome.InvalidLink -> stop { it.copy(linkProblem = AuthProblem.LinkInvalid) }
                    ConfirmationOutcome.LinkExpired -> stop { it.copy(linkProblem = AuthProblem.LinkExpired) }
                    ConfirmationOutcome.DomainNotAllowed -> stop { it.copy(linkProblem = AuthProblem.DomainNotAllowed) }
                }
                is SubmitResult.Invalid -> stop { it.copy(linkProblem = AuthProblem.LinkEmpty) }
                // The link stays in the field: "Подтвердить" tries again.
                is SubmitResult.Failure -> stop { it.copy(linkProblem = result.cause.asProblem()) }
            }
        }
    }

    /** "Отправить письмо ещё раз"; does something only when the pause is over. */
    fun onResendClick() {
        if (state.value.isResendWaiting) return
        if (!begin(ConfirmAction.Resend)) return
        viewModelScope.launch {
            resendLetter(email).fold(
                onSuccess = { outcome ->
                    when (outcome) {
                        // The password is gone, and with it the registration: the user registers again.
                        null -> leave(ConfirmExit.Register)
                        is RegistrationOutcome.LetterSent -> {
                            stop { it }
                            startCountdown(outcome.pending.resendAfter.wholeSecondsRoundedUp())
                        }
                        RegistrationOutcome.DomainNotAllowed ->
                            stop { it.copy(submitProblem = AuthProblem.DomainNotAllowed) }
                        // The address was confirmed meanwhile: there is an account, so what is left is to sign in.
                        RegistrationOutcome.AlreadyRegistered -> signInConfirmed()
                    }
                },
                onFailure = { cause -> stop { it.copy(submitProblem = cause.asLetterProblem()) } },
            )
        }
    }

    /**
     * The screen has passed [ConfirmUiState.exit] on. Without this the event would stay in the state, and the
     * screen, composed again if the user comes back to it, would leave once more. The screen stays "running" (see
     * [onReturned]).
     */
    fun onExitHandled() {
        state.update { it.copy(exit = null) }
    }

    /**
     * The screen is on top again after it had been left for another one (the sign-in or the registration screen
     * opened over it, and the user came back): it takes actions again. It does nothing until the screen was told to
     * leave and passed that on, so showing it for the first time, and again after a rotation, changes nothing.
     */
    fun onReturned() {
        if (!isLeaving || state.value.exit != null) return
        isLeaving = false
        state.update { it.copy(running = null) }
    }

    /** The letter was sent again but the address had an account already: signs in with the remembered password. */
    private suspend fun signInConfirmed() {
        signInPending(email).fold(
            onSuccess = { outcome ->
                when (outcome) {
                    SignInOutcome.SignedIn -> Unit
                    // The address is confirmed and the app cannot sign in with what it remembers (no password, or
                    // the account has another one): the user signs in by hand.
                    null,
                    SignInOutcome.NotConfirmedYet,
                    SignInOutcome.WrongCredentials,
                    SignInOutcome.AccountUnavailable,
                    -> leave(ConfirmExit.SignIn(emailConfirmed = true))
                }
            },
            onFailure = { cause -> stop { it.copy(submitProblem = cause.asProblem()) } },
        )
    }

    /** Starts [action] unless another one is running; returns whether it did. */
    private fun begin(action: ConfirmAction): Boolean {
        var started = false
        state.update { current ->
            started = current.running == null
            if (started) current.copy(running = action, submitProblem = null, linkProblem = null) else current
        }
        return started
    }

    /** Ends the action that was on its way and lets the user try again, with [change] made to the state. */
    private fun stop(change: (ConfirmUiState) -> ConfirmUiState) {
        state.update { change(it).copy(running = null) }
    }

    /** Sends the user away from the screen. The action stays "running": nothing more is done here. */
    private fun leave(exit: ConfirmExit) {
        isLeaving = true
        state.update { it.copy(exit = exit) }
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
