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

    /** There is no session: the sign-in screen is shown. */
    data object SignedOut : AuthState

    data class SignedIn(val session: Session) : AuthState
}

/**
 * A code was sent to [email]; the next one can be requested in [resendAfter] (zero: right now).
 * [toString] hides the address.
 */
data class VerificationChallenge(val email: String, val resendAfter: Duration) {
    override fun toString(): String = "VerificationChallenge(resendAfter=$resendAfter)"
}

/** The server's answer to "send me a code". */
sealed interface CodeRequestOutcome {
    /** The e-mail with a code was sent. */
    data class Sent(val challenge: VerificationChallenge) : CodeRequestOutcome

    /** The address's domain is not in the registry of universities; nothing was sent. */
    data object UntrustedDomain : CodeRequestOutcome

    /** Too many codes were requested for the address; the next request is possible in [retryAfter]. */
    data class TooManyRequests(val retryAfter: Duration) : CodeRequestOutcome
}

/** The server's answer to "this is the code from the e-mail". */
sealed interface CodeVerificationOutcome {
    /** The code is right: the user is signed in with [session] (the account is created on the first time). */
    data class Verified(val session: Session) : CodeVerificationOutcome

    /** The code is wrong; [attemptsLeft] more tries remain before the code is burned. */
    data class WrongCode(val attemptsLeft: Int) : CodeVerificationOutcome

    /** The code is out of date, was burned or never requested: a new one is needed. */
    data object Expired : CodeVerificationOutcome

    /** The attempts are spent; the code is burned and a new one is needed. */
    data object TooManyAttempts : CodeVerificationOutcome
}
