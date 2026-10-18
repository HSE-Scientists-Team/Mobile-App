package org.matchlab.academic_net.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class ExclusiveLauncherTest {
    @Test
    fun theFirstRequestRuns() = runTest {
        val launcher = ExclusiveLauncher()
        var runs = 0

        assertTrue(launcher.launchIn(this) { runs++ })
        runCurrent()

        assertEquals(1, runs)
    }

    @Test
    fun requestsWhileTheWorkRunsAreDropped() = runTest {
        val launcher = ExclusiveLauncher()
        val gate = CompletableDeferred<Unit>()
        var runs = 0

        assertTrue(launcher.launchIn(this) { runs++; gate.await() })
        runCurrent()
        repeat(4) { assertFalse(launcher.launchIn(this) { runs++ }) }
        runCurrent()

        assertEquals(1, runs)
        gate.complete(Unit)
    }

    @Test
    fun theLauncherIsFreeAgainWhenTheWorkEnds() = runTest {
        val launcher = ExclusiveLauncher()
        val gate = CompletableDeferred<Unit>()
        var runs = 0
        launcher.launchIn(this) { runs++; gate.await() }
        runCurrent()

        gate.complete(Unit)
        runCurrent()

        assertTrue(launcher.launchIn(this) { runs++ })
        runCurrent()
        assertEquals(2, runs)
    }

    @Test
    fun aFailedWorkFreesTheLauncher() = runTest {
        val handled = mutableListOf<Throwable>()
        val scope = CoroutineScope(
            SupervisorJob() + UnconfinedTestDispatcher(testScheduler) + CoroutineExceptionHandler { _, e -> handled += e },
        )
        val launcher = ExclusiveLauncher()

        launcher.launchIn(scope) { error("no network") }
        runCurrent()

        assertEquals(1, handled.size)
        var ran = false
        assertTrue(launcher.launchIn(scope) { ran = true })
        runCurrent()
        assertTrue(ran)
        scope.cancel()
    }

    @Test
    fun aCancelledWorkFreesTheLauncher() = runTest {
        // The screen that started the work leaves the composition, and its scope is cancelled.
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val launcher = ExclusiveLauncher()
        val neverAnswered = CompletableDeferred<Unit>()
        launcher.launchIn(scope) { neverAnswered.await() }
        runCurrent()
        assertFalse(launcher.launchIn(scope) { })

        scope.cancel()
        runCurrent()

        val next = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        var ran = false
        assertTrue(launcher.launchIn(next) { ran = true })
        runCurrent()
        assertTrue(ran)
        next.cancel()
    }

    @Test
    fun aScopeThatIsAlreadyCancelledDoesNotKeepTheLauncherBusy() = runTest {
        val dead = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        dead.cancel()
        val launcher = ExclusiveLauncher()
        var ran = false

        // The work never starts in a dead scope, and nothing is left to end it.
        launcher.launchIn(dead) { ran = true }
        runCurrent()

        assertFalse(ran)
        val alive = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        assertTrue(launcher.launchIn(alive) { ran = true })
        runCurrent()
        assertTrue(ran)
        alive.cancel()
    }

    @Test
    fun workThatEndsAtOnceLeavesTheLauncherFree() = runTest {
        // With a dispatcher that runs the block inside `launch`, the work is over before the
        // completion handler is attached.
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val launcher = ExclusiveLauncher()
        var runs = 0

        assertTrue(launcher.launchIn(scope) { runs++ })
        assertTrue(launcher.launchIn(scope) { runs++ })

        assertEquals(2, runs)
        scope.cancel()
    }

    @Test
    fun launchersDoNotShareTheirState() = runTest {
        val first = ExclusiveLauncher()
        val second = ExclusiveLauncher()
        val gate = CompletableDeferred<Unit>()
        first.launchIn(this) { gate.await() }
        runCurrent()

        assertTrue(second.launchIn(this) { })
        gate.complete(Unit)
    }
}
