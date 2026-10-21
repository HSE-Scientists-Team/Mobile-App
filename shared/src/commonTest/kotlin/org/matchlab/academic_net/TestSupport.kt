package org.matchlab.academic_net

import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.withContext

/**
 * A dispatcher that keeps what is given to it until [release]; from then on it runs everything at once, in the caller. It
 * stands for an IO thread that is busy: the test sees the app in the moment between "the read was asked for" and "the read
 * is done".
 */
internal class HeldDispatcher : CoroutineDispatcher() {
    private val held = ArrayDeque<Runnable>()
    private var released = false

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        if (released) block.run() else held.addLast(block)
    }

    /** Runs what was held and everything that comes later. */
    fun release() {
        released = true
        while (held.isNotEmpty()) held.removeFirst().run()
    }
}

/**
 * Waits until [condition] holds, in real time. The clock of [kotlinx.coroutines.test.runTest] skips delays, but the app
 * keeps the choice of the statistics on the real IO dispatcher, so a test of the whole graph has to wait for a thread.
 * [condition] is read on the thread of the test, where the coroutines of the test run, and the coroutines that are ready are
 * run between the checks.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal suspend fun TestScope.eventually(timeout: Duration = 10.seconds, condition: () -> Boolean) {
    val deadline = TimeSource.Monotonic.markNow() + timeout
    while (true) {
        runCurrent()
        if (condition()) return
        check(deadline.hasNotPassedNow()) { "the condition did not hold within $timeout" }
        withContext(Dispatchers.Default) { delay(5) }
    }
}

/** Lets [duration] of real time pass, running the coroutines of the test that are ready: to check that something does not happen. */
@OptIn(ExperimentalCoroutinesApi::class)
internal suspend fun TestScope.waitInRealTime(duration: Duration) {
    runCurrent()
    withContext(Dispatchers.Default) { delay(duration) }
    runCurrent()
}
