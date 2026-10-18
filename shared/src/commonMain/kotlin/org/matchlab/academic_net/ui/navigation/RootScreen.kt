package org.matchlab.academic_net.ui.navigation

import org.matchlab.academic_net.feature.auth.domain.AuthState
import org.matchlab.academic_net.feature.auth.domain.Session
import org.matchlab.academic_net.feature.auth.ui.SignInStart
import org.matchlab.academic_net.feature.auth.ui.signInStart

/**
 * What the root of the app shows for a state of the session: one of three, and only one at a time.
 *
 * The choice is made from the state alone ([toRootScreen]), so it is the same on the first frame after a start, after a
 * rotation and after the process was restarted. In particular a user whose session was restored never sees the
 * sign-in screen, not even for a frame.
 */
internal sealed interface RootScreen {
    /**
     * "22 Заставка": the saved session is being read (`AuthState.Restoring`). The sign-in screen is not shown yet, because
     * the answer may be "signed in" and it would flash on the screen of a user who is.
     */
    data object Splash : RootScreen

    /** The sign-in flow: there is no session. [start] is what its first screen starts with. */
    data class SignIn(val start: SignInStart) : RootScreen

    /** The tabs and everything above them, for the signed-in user [session]. */
    data class Main(val session: Session) : RootScreen
}

/**
 * The screen the root shows for [this] state of the session.
 *
 * - `Restoring` is the splash.
 * - `SignedOut` is the sign-in flow, starting with the address the user last used and, if the server stopped accepting
 *   their session, with the note that says so (see [signInStart]).
 * - `SignedIn` is the tabs, at once: restoring a session does not wait for the network.
 */
internal fun AuthState.toRootScreen(): RootScreen = when (this) {
    AuthState.Restoring -> RootScreen.Splash
    is AuthState.SignedOut -> RootScreen.SignIn(signInStart())
    is AuthState.SignedIn -> RootScreen.Main(session)
}
