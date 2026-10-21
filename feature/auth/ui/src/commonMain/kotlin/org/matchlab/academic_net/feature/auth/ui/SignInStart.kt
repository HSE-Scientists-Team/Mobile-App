package org.matchlab.academic_net.feature.auth.ui

import androidx.compose.runtime.Immutable
import org.matchlab.academic_net.feature.auth.domain.AuthState

/**
 * What the sign-in screen starts with: the arguments [SignInRoute] takes from the state of a user who is signed out.
 *
 * @property email the address to put into the field: the one the user last signed in or registered with, so that
 * after "Сеанс завершён" or a sign-out only the password is left to type; empty if the app has never seen one. The
 * password is never part of it: it is not kept anywhere to be given back.
 * @property sessionExpired the server stopped accepting the session the user had: the screen says so
 */
@Immutable
data class SignInStart(
    val email: String,
    val sessionExpired: Boolean,
) {
    /** The address is personal data: logs get only whether there is one. */
    override fun toString(): String = "SignInStart(hasEmail=${email.isNotEmpty()}, sessionExpired=$sessionExpired)"
}

/**
 * The start of the sign-in screen for [this] state: what the app's root passes to [SignInRoute] as `email` and
 * `sessionExpired`.
 *
 * The sign-in screen is shown for this state and for no other: while the saved session is read
 * ([AuthState.Restoring]) the root shows [AuthSplash], and with a session ([AuthState.SignedIn]) it shows the tabs,
 * so a user whose session was restored never sees the sign-in screen, not even for a frame.
 */
fun AuthState.SignedOut.signInStart(): SignInStart =
    SignInStart(email = lastEmail.orEmpty(), sessionExpired = sessionExpired)

/** The note that the sign-in screen puts above its fields. */
internal enum class SignInBanner {
    /** "Сеанс завершён. Войдите снова": the server stopped accepting the session. */
    SessionEnded,

    /** "Почта подтверждена. Войдите": the address was confirmed and the app could not sign in by itself. */
    EmailConfirmed,
}

/**
 * The note of the sign-in screen for the two flags it is given, or `null` for none. Only one note is shown at a time,
 * and the end of the session comes first: it is what the user needs to know before anything else.
 */
internal fun signInBanner(sessionExpired: Boolean, emailConfirmed: Boolean): SignInBanner? = when {
    sessionExpired -> SignInBanner.SessionEnded
    emailConfirmed -> SignInBanner.EmailConfirmed
    else -> null
}
