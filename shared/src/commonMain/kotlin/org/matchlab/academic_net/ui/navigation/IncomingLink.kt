package org.matchlab.academic_net.ui.navigation

import org.matchlab.academic_net.feature.auth.domain.AuthState
import org.matchlab.academic_net.gateway.contract.LetterLinks

/** What the root does with a link the system opened in the app. */
internal sealed interface IncomingLinkAction {
    /** The link is not for the app, or the user is signed in: it is dropped. */
    data object Ignore : IncomingLinkAction

    /** The saved session is still being read: the link is kept until it is known whether the user is signed in. */
    data object Wait : IncomingLinkAction

    /** The user is signed out and the link is the one of the confirmation letter: the address is confirmed with it. */
    data class Confirm(val link: String) : IncomingLinkAction
}

/**
 * The decision for [link] in the state [state] of the session.
 *
 * Only the link of the confirmation letter ([LetterLinks]) is taken, and only from a user who is signed out: a signed-in
 * user has nothing to confirm here, and the letter of another registration must not be spent on their session. While the
 * saved session is being read ([AuthState.Restoring]) the link waits, so that a user whose session is restored is not
 * shown the confirmation first.
 */
internal fun incomingLinkAction(link: String, state: AuthState): IncomingLinkAction = when {
    LetterLinks.tokenOf(link) == null -> IncomingLinkAction.Ignore
    state is AuthState.Restoring -> IncomingLinkAction.Wait
    state is AuthState.SignedIn -> IncomingLinkAction.Ignore
    else -> IncomingLinkAction.Confirm(link)
}
