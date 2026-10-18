package org.matchlab.academic_net.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.matchlab.academic_net.Backend
import org.matchlab.academic_net.SessionDependencies
import org.matchlab.academic_net.feature.auth.domain.Session
import org.matchlab.academic_net.feature.auth.ui.AuthUiDependencies
import org.matchlab.academic_net.securestore.SecureStore

/**
 * The graph of the signed-in user, kept for as long as the session lasts. The root of the app asks it for the
 * dependencies of the screens when the state says "signed in", and closes it when the state says "signed out".
 *
 * Every call comes from the main thread, from the composition.
 */
internal interface SessionGraphs {
    /**
     * The dependencies of the screens of [session]. Called again for the same session (the activity was recreated
     * by a rotation, the composition restarted) it gives back the same graph, with its caches, so that nothing the
     * user has done is lost and no second graph is built. A different session ends the one that was open first.
     */
    fun open(session: Session): SessionDependencies

    /**
     * Ends the open graph, if there is one: its coroutines are cancelled and its Gateway session closed. The next
     * [open] builds a new graph, even for a session equal to the closed one. Does nothing when no graph is open.
     */
    fun close()
}

/**
 * [SessionGraphs] over [app]: one [SessionComponent] at a time, made by [create].
 *
 * The saved session, the sign-in state and the Gateway client belong to [app] and are never touched by [close]:
 * ending a graph is not signing out (the signing-out, or the refusal of the server, comes first and is what makes
 * the root call [close]).
 */
internal class ComponentSessionGraphs(
    private val app: AppComponent,
    private val create: (AppComponent, Session) -> SessionComponent = ::createSessionComponent,
) : SessionGraphs {
    private var open: Open? = null

    private class Open(val session: Session, val component: SessionComponent)

    override fun open(session: Session): SessionDependencies {
        open?.takeIf { it.session == session }?.let { return it.component.dependencies }

        // Another user's graph is over the moment this one begins. The root closes it on "signed out" first; this
        // is for a session that is replaced without passing through that.
        close()
        val component = create(app, session)
        open = Open(session, component)
        return component.dependencies
    }

    override fun close() {
        val ended = open ?: return
        open = null
        ended.component.close()
    }
}

/**
 * The graphs of the process: the [app] one, and the [sessions] of its signed-in user. One pair per process, so
 * everything survives the destruction of an activity and the user does not sign in again after a rotation.
 */
internal class AppGraphs(val app: AppComponent, val sessions: SessionGraphs) {
    /** What the sign-in screens and the root need. */
    val auth: AuthUiDependencies get() = app.auth

    /**
     * Starts what the process does by itself, whatever the screens do: reads the saved session (the state leaves
     * `Restoring` at once, with the session if there is one, without waiting for the network) and then asks the
     * server whether it still accepts the token. It runs in [scope], not in the composition: the check can take as
     * long as a request does (a slow network), and a rotation or a recreated activity must not cut it off half-way.
     * Reading the session cannot be cut off either, so the sign-in screen is never shown to a user who is signed in.
     *
     * Only an explicit refusal of the server ends the session; a failure to reach it does not (see
     * `AuthRepository.restoreSession`). Calling it again does nothing: the session is restored once.
     */
    fun start(scope: CoroutineScope): Job = scope.launch { auth.restoreSession() }
}

/**
 * Holds the graphs of the process. It outlives activities, so the state of the session and the local chats keep what
 * the user has changed on rotation. The secure store is created by the platform on the first use.
 */
internal object AppGraph {
    private var instance: AppGraphs? = null

    /** The work the process starts by itself (see [AppGraphs.start]); it lives as long as the process. */
    private val processScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * The graphs of the process; made, and started, on the first call, from the arguments of that call. [backend] is
     * fixed from then on: the process does not change its mode, and a later call with another one gets the graphs of
     * the first.
     */
    fun get(secureStore: () -> SecureStore, backend: Backend): AppGraphs =
        instance ?: createGraphs(secureStore(), backend).also { instance = it }

    private fun createGraphs(secureStore: SecureStore, backend: Backend): AppGraphs {
        val app = createAppComponent(secureStore, backend)
        return AppGraphs(app, ComponentSessionGraphs(app)).also { it.start(processScope) }
    }
}
