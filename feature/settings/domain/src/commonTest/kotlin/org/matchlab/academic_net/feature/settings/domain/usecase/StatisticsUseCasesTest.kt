package org.matchlab.academic_net.feature.settings.domain.usecase

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.feature.settings.domain.StatisticsSettings

/**
 * The two use cases of the switch "usage statistics" of the device: one watches it, one changes it. The setting is local, so
 * watching has nothing of its own to add, and a change that cannot be saved is a failed [Result], never a thrown error;
 * a cancelled caller is not a failure.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsUseCasesTest {
    /** The setting of the device: a switch with a current value that remembers what it was asked to save. */
    private class FakeStatisticsSettings(initial: Boolean = true) : StatisticsSettings {
        val current = MutableStateFlow(initial)
        override val enabled: Flow<Boolean> = current

        val saved = mutableListOf<Boolean>()
        var failure: Throwable? = null
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun setEnabled(enabled: Boolean) {
            saved += enabled
            gate?.await()
            failure?.let { throw it }
            current.value = enabled
        }
    }

    private val settings = FakeStatisticsSettings()

    // ---- watching ----

    @Test
    fun watchingGivesTheValueOfTheSettingAsItIs() = runTest {
        assertEquals(false, ObserveStatisticsUseCase(FakeStatisticsSettings(initial = false))().first())
        assertEquals(true, ObserveStatisticsUseCase(FakeStatisticsSettings(initial = true))().first())
    }

    @Test
    fun watchingChangesNothingOnTheWay() = runTest {
        val source = object : StatisticsSettings {
            override val enabled: Flow<Boolean> = flowOf(true, false, false, true)

            override suspend fun setEnabled(enabled: Boolean) = Unit
        }

        assertEquals(listOf(true, false, false, true), ObserveStatisticsUseCase(source)().toList())
    }

    @Test
    fun watchingShowsTheCurrentValueFirstAndThenEveryChange() = runTest {
        val seen = mutableListOf<Boolean>()
        backgroundScope.launch { ObserveStatisticsUseCase(settings)().collect { seen += it } }
        runCurrent()
        assertEquals(listOf(true), seen)

        SetStatisticsUseCase(settings)(false)
        runCurrent()
        SetStatisticsUseCase(settings)(true)
        runCurrent()

        assertEquals(listOf(true, false, true), seen)
    }

    @Test
    fun watchingSavesNothing() = runTest {
        ObserveStatisticsUseCase(settings)().first()

        assertEquals(emptyList(), settings.saved)
    }

    @Test
    fun aFlowThatFailsOnItsOwnAccountIsNotHiddenByTheUseCase() = runTest {
        val cause = IllegalStateException("the preference is gone")
        val source = object : StatisticsSettings {
            override val enabled: Flow<Boolean> = flow { throw cause }

            override suspend fun setEnabled(enabled: Boolean) = Unit
        }

        val thrown = assertFailsWith<IllegalStateException> { ObserveStatisticsUseCase(source)().toList() }

        assertSame(cause, thrown)
    }

    // ---- changing ----

    @Test
    fun savingSwitchesTheSettingAndSucceeds() = runTest {
        val on = SetStatisticsUseCase(settings)(true)
        val off = SetStatisticsUseCase(settings)(false)

        assertTrue(on.isSuccess && off.isSuccess)
        assertEquals(Unit, off.getOrThrow())
        assertEquals(listOf(true, false), settings.saved)
        assertEquals(false, settings.current.value)
    }

    @Test
    fun aFailedSaveIsAFailedResultWithItsCauseAndTheSettingStaysAsItWas() = runTest {
        val cause = IllegalStateException("the keystore cannot write")
        settings.failure = cause

        val result = SetStatisticsUseCase(settings)(false)

        assertTrue(result.isFailure)
        assertSame(cause, result.exceptionOrNull())
        assertEquals(listOf(false), settings.saved)
        assertEquals(true, settings.current.value)
    }

    @Test
    fun everyKindOfFailureIsAResultNotAThrownError() = runTest {
        listOf<Throwable>(IllegalStateException("a"), RuntimeException("b"), Exception("c"), Error("d")).forEach { cause ->
            settings.failure = cause

            val failure = SetStatisticsUseCase(settings)(true).exceptionOrNull()

            assertSame(cause, failure)
        }
    }

    @Test
    fun theSettingIsAskedAgainAfterAFailure() = runTest {
        settings.failure = IllegalStateException("the keystore cannot write")
        SetStatisticsUseCase(settings)(false)

        settings.failure = null
        val result = SetStatisticsUseCase(settings)(false)

        assertTrue(result.isSuccess)
        assertEquals(listOf(false, false), settings.saved)
        assertEquals(false, settings.current.value)
    }

    @Test
    fun aCancellationIsNeverTurnedIntoAFailure() = runTest {
        settings.failure = CancellationException("screen left")

        assertFailsWith<CancellationException> { SetStatisticsUseCase(settings)(true) }
    }

    @Test
    fun aCancelledCallerGetsNoResultAtAll() = runTest {
        val gate = CompletableDeferred<Unit>()
        settings.gate = gate
        var result: Result<Unit>? = null

        val saving = launch { result = SetStatisticsUseCase(settings)(false) }
        runCurrent()
        saving.cancel()
        saving.join()

        assertTrue(saving.isCancelled)
        assertNull(result, "a cancelled save must not come back as a result the screen would act on")
    }

    @Test
    fun theResultOfASlowSaveComesWhenItEnds() = runTest {
        val gate = CompletableDeferred<Unit>()
        settings.gate = gate
        var result: Result<Unit>? = null

        val saving = launch { result = SetStatisticsUseCase(settings)(false) }
        runCurrent()
        assertNull(result)

        gate.complete(Unit)
        runCurrent()

        assertTrue(saving.isCompleted)
        assertTrue(result?.isSuccess == true, "the result of the save: $result")
    }
}
