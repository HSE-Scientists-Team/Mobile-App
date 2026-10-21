package org.matchlab.academic_net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.matchlab.academic_net.analytics.Analytics
import org.matchlab.academic_net.analytics.StatisticsPreference
import org.matchlab.academic_net.feature.auth.domain.AuthState

/**
 * Tells the [analytics] what it may do: whether it may send anything, and whose events they are. The shared code and
 * the screens know nothing of this; they only change the [preference] (the sign-up form, the settings).
 *
 * **Sending follows the choice.** The choice is read from the store first ([StatisticsPreference.load]); nothing is
 * decided before that, so a session that is restored at the start of the app never meets a choice that is still on its
 * way from the store. After that sending is on when the choice is `true` and off when it is `false`.
 *
 * **The default is "on", and it is not saved.** The setting is on until the person says otherwise (the decision of the
 * customer), so a device where nobody has chosen yet (`null`) sends while somebody is signed in and sends nothing
 * while nobody is (the sign-in and the sign-up screens, the splash). The default is only applied, never written to the
 * store: the store cannot tell "never chosen" from "cannot be read for the moment", and a default that was written
 * would replace a "no" that a failing read had hidden. A choice that is made (in the sign-up form or in the settings) is
 * saved by whoever makes it and is never replaced.
 *
 * **The profile follows the account.** A signed-in user becomes the user profile of the analytics (their id only), and
 * a sign-out removes it; the profile is set before sending is switched on for it. The state `Restoring` decides nothing:
 * it is neither, and sending stays off in it unless the person has already said yes.
 */
internal class AnalyticsController(
    private val analytics: Analytics,
    private val preference: StatisticsPreference,
    private val authState: StateFlow<AuthState>,
) {
    /** What the analytics should be told: the profile (`null`: nobody, or not known yet) and whether it may send. */
    private data class Settings(val signedInUserId: String?, val isSignedOut: Boolean, val sendingAllowed: Boolean)

    /**
     * Starts following the choice and the session in [scope], which lives as long as the process: the work ends with
     * the scope. Call it once.
     */
    fun start(scope: CoroutineScope) {
        scope.launch {
            preference.load()
            combine(preference.enabled, authState) { choice, state ->
                Settings(
                    signedInUserId = (state as? AuthState.SignedIn)?.session?.userId?.value,
                    isSignedOut = state is AuthState.SignedOut,
                    sendingAllowed = choice ?: (state is AuthState.SignedIn),
                )
            }
                .distinctUntilChanged()
                .collect { settings ->
                    // The profile first, so that what the choice allows is already the user's.
                    when {
                        settings.signedInUserId != null -> analytics.setUserProfileId(settings.signedInUserId)
                        settings.isSignedOut -> analytics.setUserProfileId(null)
                    }
                    analytics.setEnabled(settings.sendingAllowed)
                }
        }
    }
}
