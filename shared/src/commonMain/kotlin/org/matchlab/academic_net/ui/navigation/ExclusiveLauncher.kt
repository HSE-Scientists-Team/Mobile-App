package org.matchlab.academic_net.ui.navigation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Runs one piece of work at a time and drops the requests that come while it runs.
 *
 * A tap that has to ask something first (a button "Написать" must create the conversation before
 * the chat can open) cannot be stopped by the screen it starts from: nothing changes there until
 * the answer arrives, so N taps would send N requests and open N screens. With a launcher the
 * first tap does the work and the following ones, until it ends, do nothing.
 *
 * The work ends normally, by an exception or by the cancellation of the scope; the launcher is
 * free again in all three cases. Not thread-safe: call it from the main thread, like the taps.
 */
internal class ExclusiveLauncher {
    private var running = false

    /**
     * Starts [block] in [scope] unless the previous one is still running; returns whether it did.
     */
    fun launchIn(scope: CoroutineScope, block: suspend () -> Unit): Boolean {
        if (running) return false
        running = true
        // The completion handler, not a `finally` in the block: a scope that is already cancelled
        // never starts the block, and the launcher must not stay busy because of it.
        scope.launch { block() }.invokeOnCompletion { running = false }
        return true
    }
}
