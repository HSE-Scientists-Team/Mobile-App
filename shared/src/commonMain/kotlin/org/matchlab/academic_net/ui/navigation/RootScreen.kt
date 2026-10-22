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
 * guest's screen, not even for a frame.
 */
internal sealed interface RootScreen {
    /**
     * "22 Заставка": the saved session is being read (`AuthState.Restoring`). The sign-in screen is not shown yet, because
     * the answer may be "signed in" and it would flash on the screen of a user who is.
     */
    data object Splash : RootScreen

    /**
     * A visitor who has not signed in: the main screen without the bottom bar, where people and projects can be looked at
     * and searched, and anything else asks for the sign-in, which opens over it. [start] is what the first screen of
     * that sign-in starts with.
     */
    data class Guest(val start: SignInStart) : RootScreen

    /** The tabs and everything above them, for the signed-in user [session]. */
    data class Main(val session: Session) : RootScreen
}

/**
 * The screen the root shows for [this] state of the session.
 *
 * - `Restoring` is the splash.
 * - `SignedOut` is the browsing of a visitor. The sign-in is not shown at once: it opens over the browsing when the
 *   visitor asks for it (or, after the server stopped accepting their session, by itself, with the note that says so),
 *   starting with the address the user last used (see [signInStart]).
 * - `SignedIn` is the tabs, at once: restoring a session does not wait for the network.
 */
internal fun AuthState.toRootScreen(): RootScreen = when (this) {
    AuthState.Restoring -> RootScreen.Splash
    is AuthState.SignedOut -> RootScreen.Guest(signInStart())
    is AuthState.SignedIn -> RootScreen.Main(session)
}
