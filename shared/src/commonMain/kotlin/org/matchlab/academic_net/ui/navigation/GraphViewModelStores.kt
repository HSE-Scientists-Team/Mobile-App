package org.matchlab.academic_net.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import org.matchlab.academic_net.di.SessionGraphs
import org.matchlab.academic_net.feature.auth.domain.AuthState

/** A [ViewModelStoreOwner] of its own: what lives in it can be dropped without touching the activity's. */
internal class GraphViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}

/**
 * The view model stores of the two graphs of the root. It lives in the activity's store, so it survives a
 * rotation like any view model, and the graphs can still be cleared one by one: the navigation of
 * a graph keeps its entries' view models in the owner it is composed under.
 */
internal class GraphViewModelStores : ViewModel() {
    val auth = GraphViewModelStoreOwner()
    val main = GraphViewModelStoreOwner()

    /**
     * Lets go of what the graph that [state] has just ended left behind. The root calls it whenever the state of the
     * session changes between signed in and signed out, and again, harmlessly, on a start in either of them:
     *
     * - `SignedIn`: the sign-in flow is over; its view models (the forms, whose passwords were only in memory) are
     *   cleared.
     * - `SignedOut`: the session is over, by the user's choice or the server's refusal. The view models of the tabs
     *   and everything above them (the states of the screens, the drafts, the running work) are cleared first, and
     *   then the [sessions] graph is closed (its caches, its local chats, its Gateway session). After that nothing of
     *   the account is alive. The saved session is not touched here: the sign-out that ended the session erased it,
     *   and a start in this state has nothing to erase.
     * - `Restoring`: nothing is decided yet, nothing is released.
     */
    fun release(state: AuthState, sessions: SessionGraphs) {
        when (state) {
            is AuthState.SignedIn -> auth.viewModelStore.clear()
            is AuthState.SignedOut -> {
                main.viewModelStore.clear()
                sessions.close()
            }
            AuthState.Restoring -> Unit
        }
    }

    override fun onCleared() {
        auth.viewModelStore.clear()
        main.viewModelStore.clear()
    }
}
