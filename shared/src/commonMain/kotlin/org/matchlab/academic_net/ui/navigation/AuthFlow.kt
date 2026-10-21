package org.matchlab.academic_net.ui.navigation

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver

/**
 * What the screens of the sign-in flow hand to one another: the address, and whether the sign-in screen says that the
 * address was just confirmed. It is kept by [AuthNavHost] in saved state, so it survives the death of the process.
 *
 * **The password is not in it, and is never passed from one screen to another**: it stays in the form that the user
 * typed it in, in memory only (see the view models of `feature:auth:ui`). The address is kept in a field of its own
 * and not in the routes, because it has characters that routes carry badly (`+`).
 *
 * @property email the address to put into the fields of the screen that is shown next; empty if there is none yet
 * @property emailConfirmed the address was confirmed and the app could not sign in by itself (the password of the
 * registration is gone with the process that was killed): the sign-in screen says "Почта подтверждена. Войдите"
 */
internal data class AuthFlow(val email: String, val emailConfirmed: Boolean = false) {
    /**
     * The user leaves the sign-in screen for the registration with [typed], the address typed so far. Whatever the
     * sign-in screen said about the address is over.
     */
    fun registrationStarted(typed: String): AuthFlow = AuthFlow(typed)

    /**
     * The address changed on one screen and the next one has to start with it: the letter was sent to it, the user
     * asked for the sign-in or for another registration with it. The note of the sign-in screen is not touched.
     */
    fun addressChanged(address: String): AuthFlow = copy(email = address)

    /**
     * The flow returns to the sign-in screen for [address], where the user signs in by hand. [confirmed]: the address
     * is confirmed, and the screen says so; otherwise the sign-in screen does not know whether it is.
     */
    fun returnedToSignIn(address: String, confirmed: Boolean): AuthFlow = AuthFlow(address, confirmed)

    /** The address is personal data: logs get only whether there is one. */
    override fun toString(): String = "AuthFlow(hasEmail=${email.isNotEmpty()}, emailConfirmed=$emailConfirmed)"

    companion object {
        /** Saves the flow in the saved state of the screen (the address and the flag, nothing else exists to save). */
        val Saver: Saver<AuthFlow, Any> = listSaver(
            save = { flow -> listOf(flow.email, flow.emailConfirmed) },
            restore = { saved -> AuthFlow(email = saved[0] as String, emailConfirmed = saved[1] as Boolean) },
        )
    }
}
