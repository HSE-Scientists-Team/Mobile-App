package org.matchlab.academic_net.feature.auth.domain

import kotlin.jvm.JvmInline
import kotlin.time.Duration
import org.matchlab.academic_net.common.model.UserId

/**
 * The opaque token the server issues for a signed-in device.
 *
 * It is a secret: [toString] hides it, so that logs and crash reports cannot leak it.
 */
@JvmInline
value class AccessToken(val value: String) {
    override fun toString(): String = "AccessToken(***)"
}

/**
 * A password as the user typed it: it is never trimmed or changed in any other way.
 *
 * It is a secret: [toString] hides it, so that logs and crash reports cannot leak it. It lives in
 * memory only for as long as a registration waits for its confirmation and is never saved.
 */
@JvmInline
value class Password(val value: String) {
    override fun toString(): String = "Password(***)"
}

/**
 * The e-mail address and the password to register or to sign in with. The [email] is already
 * [EmailRules.normalize]d. [toString] shows neither of the two.
 */
data class Credentials(val email: String, val password: Password) {
    override fun toString(): String = "Credentials(***)"
}

/**
 * A signed-in user on this device. The [email] and the [accessToken] are private data: [toString]
 * shows only the [userId].
 */
data class Session(
    val userId: UserId,
    val email: String,
    val accessToken: AccessToken,
) {
    override fun toString(): String = "Session(userId=$userId)"
}

/** Whether the user is signed in. The app's screens depend on it: chats and the profile exist only in [SignedIn]. */
sealed interface AuthState {
    /** The saved session is being read; the app shows an empty background and decides nothing yet. */
    data object Restoring : AuthState

    /**
     * There is no session: the sign-in screen is shown.
     *
     * @property sessionExpired the server stopped accepting the session the user had; the sign-in
     * screen says so ("Сеанс завершён. Войдите снова"). It is `false` after a sign-out of the user.
     * @property lastEmail the address the user last signed in or registered with, for the sign-in
     * screen to fill in (after "Сеанс завершён" only the password is left to type); `null` if the
     * app has never seen one. It is kept by a sign-out and replaced by the next sign-in; it is not a
     * secret, but it is personal data: [toString] does not show it.
     */
    data class SignedOut(val sessionExpired: Boolean = false, val lastEmail: String? = null) : AuthState {
        override fun toString(): String = "SignedOut(sessionExpired=$sessionExpired, hasLastEmail=${lastEmail != null})"
    }

    data class SignedIn(val session: Session) : AuthState
}

/**
 * A registration whose letter has been sent and is waited for: the address is not confirmed yet.
 *
 * It exists only in this run of the app. The address is personal data: [toString] does not show it.
 *
 * @property resendAfter how long until the letter may be sent again (zero: right now). The pause is
 * for the screen to count down; the repository does not refuse an earlier request.
 * @property hasPassword the password of the registration is still in memory, so that the app can
 * sign in by itself once the address is confirmed; without it the user is asked to type the
 * password on the sign-in screen. The password is never saved: a process that was killed knows
 * nothing of its registrations.
 */
data class PendingRegistration(
    val email: String,
    val resendAfter: Duration,
    val hasPassword: Boolean,
) {
    override fun toString(): String = "PendingRegistration(resendAfter=$resendAfter, hasPassword=$hasPassword)"
}

/** The server's answer to "register this address". */
sealed interface RegistrationOutcome {
    /** The letter with the link was sent. The account does not exist yet: it is created when the link is opened. */
    data class LetterSent(val pending: PendingRegistration) : RegistrationOutcome

    /** The domain of the address does not belong to an organization that may register; nothing was sent. */
    data object DomainNotAllowed : RegistrationOutcome

    /**
     * The address already has an account; nothing was sent. After "send the letter again" it means
     * that the address was confirmed meanwhile.
     */
    data object AlreadyRegistered : RegistrationOutcome
}

/** The server's answer to "this is the link from the letter". */
sealed interface ConfirmationOutcome {
    /**
     * The address is confirmed and the account exists (also when it was confirmed before).
     *
     * @property signedIn the app signed in by itself with the password it still held; when it is
     * `false` the user is asked to sign in.
     */
    data class Confirmed(val signedIn: Boolean) : ConfirmationOutcome

    /** There is no token in the text, or the server does not know the token or it has been used already. */
    data object InvalidLink : ConfirmationOutcome

    /** The link is out of date (it lives 30 minutes): the letter has to be sent again. */
    data object LinkExpired : ConfirmationOutcome

    /** The domain of the address does not belong to an organization that may be confirmed any more. */
    data object DomainNotAllowed : ConfirmationOutcome
}

/** The server's answer to "sign me in". */
sealed interface SignInOutcome {
    /** The user is signed in: [AuthState.SignedIn] holds the session. */
    data object SignedIn : SignInOutcome

    /** The address or the password is wrong (also when the address has no account). */
    data object WrongCredentials : SignInOutcome

    /**
     * The address of a registration that is waited for has no account yet: the link of the letter
     * has not been opened. It is the answer to "I've confirmed", where the credentials are right.
     */
    data object NotConfirmedYet : SignInOutcome

    /** The server refuses the account: the address is not confirmed or the account is disabled. */
    data object AccountUnavailable : SignInOutcome
}
