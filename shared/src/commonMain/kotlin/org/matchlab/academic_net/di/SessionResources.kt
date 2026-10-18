package org.matchlab.academic_net.di

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import me.tatarka.inject.annotations.Inject

/**
 * What the object graph of one session owns and has to let go of when the session ends: the [scope] of its
 * background work and the resources opened for it (the session client of the Gateway). One per [SessionComponent].
 *
 * [close] ends them all: it cancels the [scope] first, so that nothing is still waiting for an answer, and then
 * closes the resources. It is what happens when the user signs out or the server refuses the token. Closing is
 * idempotent, and safe to call from any thread, as is [closeOnEnd]: a resource is opened by a data source on
 * whatever thread asks for it first, while the root closes the graph on the main one.
 *
 * Only what belongs to the session is closed here. The secure store, the saved session and the shared engine of the
 * Gateway belong to the process and are not touched.
 */
@OptIn(ExperimentalAtomicApi::class)
@Inject
@SessionScope
internal class SessionResources {
    /**
     * The scope for the background work of the session's data sources (the mock moderation of the profile). It is
     * cancelled by [close], so that work started for one account never runs on for the next one.
     */
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private class State(val resources: List<AutoCloseable>, val closed: Boolean)

    private val state = AtomicReference(State(emptyList(), closed = false))

    /** Whether [close] has been called. */
    val isClosed: Boolean get() = state.load().closed

    /** How many resources wait to be closed: the ones registered with [closeOnEnd] before [close]. */
    val openCount: Int get() = state.load().resources.size

    /**
     * Registers [resource] to be closed by [close] and returns it. A resource registered after [close] is closed at
     * once (and its failure thrown here): a data source asking late for its client, after the user has signed out,
     * must not leave a client that nobody will close.
     */
    fun <T : AutoCloseable> closeOnEnd(resource: T): T {
        while (true) {
            val current = state.load()
            if (current.closed) {
                resource.close()
                return resource
            }
            if (state.compareAndSet(current, State(current.resources + resource, closed = false))) return resource
        }
    }

    /**
     * Ends the session's work: cancels the [scope], then closes the registered resources, the last registered
     * first. A resource that fails to close does not stop the others; the first failure is thrown at the end, with
     * the later ones attached to it. The second call does nothing.
     */
    fun close() {
        val toClose = takeResources() ?: return
        scope.cancel(CancellationException("The session ended"))

        var failure: Throwable? = null
        for (resource in toClose.asReversed()) {
            try {
                resource.close()
            } catch (error: Throwable) {
                val first = failure
                if (first == null) failure = error else first.addSuppressed(error)
            }
        }
        failure?.let { throw it }
    }

    /** Marks the session closed and returns what was registered, or `null` if somebody has done so before. */
    private fun takeResources(): List<AutoCloseable>? {
        while (true) {
            val current = state.load()
            if (current.closed) return null
            if (state.compareAndSet(current, State(emptyList(), closed = true))) return current.resources
        }
    }
}
