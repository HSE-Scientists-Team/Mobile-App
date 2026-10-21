package org.matchlab.academic_net.feature.settings.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.matchlab.academic_net.feature.settings.domain.ProfileMissingException
import org.matchlab.academic_net.feature.settings.domain.SettingsRepository
import org.matchlab.academic_net.feature.settings.domain.StatisticsSettings
import org.matchlab.academic_net.feature.settings.domain.usecase.GetOpenToCollaborationUseCase
import org.matchlab.academic_net.feature.settings.domain.usecase.ObserveStatisticsUseCase
import org.matchlab.academic_net.feature.settings.domain.usecase.SetOpenToCollaborationUseCase
import org.matchlab.academic_net.feature.settings.domain.usecase.SetStatisticsUseCase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * "18 Настройки": the one switch loads once, moves at once when it is flipped, takes no taps while it is saved, goes back when
 * saving fails, and is off and unavailable for an account without a profile.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** The server behind a spy: counts the loads, can fail them, and can hold the answer of a save. */
    private class FakeSettings(var open: Boolean? = false) : SettingsRepository {
        var loads = 0
        var loadFailure: Throwable? = null
        var saveFailure: Throwable? = null

        /** What the server answers to a save, when it is not what was asked for. */
        var answer: Boolean? = null
        var gate: CompletableDeferred<Unit>? = null
        val saved = mutableListOf<Boolean>()

        override suspend fun getOpenToCollaboration(): Boolean? {
            loads++
            loadFailure?.let { throw it }
            return open
        }

        override suspend fun setOpenToCollaboration(open: Boolean): Boolean {
            saved += open
            gate?.await()
            saveFailure?.let { throw it }
            this.open = answer ?: open
            return this.open!!
        }
    }

    private val repository = FakeSettings()

    /**
     * The switch of the statistics is not under test here (it has its own tests): an always-on stand-in that accepts every
     * write, so these tests only exercise the other switch.
     */
    private val statisticsSettings = object : StatisticsSettings {
        override val enabled: Flow<Boolean> = flowOf(true)

        override suspend fun setEnabled(enabled: Boolean) = Unit
    }

    private fun viewModel() = SettingsViewModel(
        getOpenToCollaboration = GetOpenToCollaborationUseCase(repository),
        setOpenToCollaboration = SetOpenToCollaborationUseCase(repository),
        observeStatistics = ObserveStatisticsUseCase(statisticsSettings),
        setStatistics = SetStatisticsUseCase(statisticsSettings),
    )

    private fun SettingsViewModel.content() = assertIs<SettingsUiState.Content>(uiState.value)

    // ---- loading ----

    @Test
    fun theSwitchIsLoadedWhenTheScreenIsCreated() {
        repository.open = true

        val content = viewModel().content()

        assertEquals(true, content.openToCollaboration)
        assertFalse(content.isSaving)
        assertFalse(content.failedToSave)
        assertEquals(1, repository.loads)
    }

    @Test
    fun theScreenIsLoadingWhileTheRequestRuns() {
        val gate = CompletableDeferred<Unit>()
        val slow = object : SettingsRepository by repository {
            override suspend fun getOpenToCollaboration(): Boolean? {
                gate.await()
                return false
            }
        }

        val viewModel = SettingsViewModel(
            getOpenToCollaboration = GetOpenToCollaborationUseCase(slow),
            setOpenToCollaboration = SetOpenToCollaborationUseCase(slow),
            observeStatistics = ObserveStatisticsUseCase(statisticsSettings),
            setStatistics = SetStatisticsUseCase(statisticsSettings),
        )
        assertEquals(SettingsUiState.Loading, viewModel.uiState.value)
        gate.complete(Unit)

        assertIs<SettingsUiState.Content>(viewModel.uiState.value)
    }

    @Test
    fun anAccountWithoutAProfileHasASwitchThatIsOffAndNull() {
        repository.open = null

        assertNull(viewModel().content().openToCollaboration)
    }

    @Test
    fun aFailedLoadIsAnErrorAndRetryLoadsAgain() {
        val cause = IllegalStateException("no network")
        repository.loadFailure = cause
        val viewModel = viewModel()
        assertEquals(SettingsUiState.Error(cause), viewModel.uiState.value)

        repository.loadFailure = null
        viewModel.onRetry()

        assertIs<SettingsUiState.Content>(viewModel.uiState.value)
        assertEquals(2, repository.loads)
    }

    @Test
    fun retryOnALoadedScreenAsksNothing() {
        val viewModel = viewModel()

        viewModel.onRetry()

        assertEquals(1, repository.loads)
    }

    // ---- flipping the switch ----

    @Test
    fun theSwitchMovesAtOnceAndTheServersAnswerIsFinal() {
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        val viewModel = viewModel()

        viewModel.onToggle(true)

        val saving = viewModel.content()
        assertEquals(true, saving.openToCollaboration)
        assertTrue(saving.isSaving)

        gate.complete(Unit)

        val saved = viewModel.content()
        assertEquals(true, saved.openToCollaboration)
        assertFalse(saved.isSaving)
        assertEquals(listOf(true), repository.saved)
    }

    @Test
    fun theSwitchTakesNoTapsWhileItIsBeingSaved() {
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        val viewModel = viewModel()

        viewModel.onToggle(true)
        viewModel.onToggle(false)
        viewModel.onToggle(true)
        gate.complete(Unit)

        assertEquals(listOf(true), repository.saved, "the later taps could overtake the first save")
        assertEquals(true, viewModel.content().openToCollaboration)
    }

    @Test
    fun aTapOnTheValueTheSwitchAlreadyHasSavesNothing() {
        repository.open = true
        val viewModel = viewModel()

        viewModel.onToggle(true)

        assertEquals(emptyList(), repository.saved)
    }

    @Test
    fun theSwitchCanBeFlippedBackAfterTheFirstSaveEnded() {
        val viewModel = viewModel()

        viewModel.onToggle(true)
        viewModel.onToggle(false)

        assertEquals(listOf(true, false), repository.saved)
        assertEquals(false, viewModel.content().openToCollaboration)
    }

    @Test
    fun whatTheServerHasWinsOverWhatWasAskedFor() {
        repository.answer = false
        val viewModel = viewModel()

        viewModel.onToggle(true)

        assertEquals(false, viewModel.content().openToCollaboration)
        assertFalse(viewModel.content().failedToSave)
    }

    @Test
    fun aFailedSaveMovesTheSwitchBackAndTellsOnce() {
        repository.saveFailure = IllegalStateException("no network")
        val viewModel = viewModel()

        viewModel.onToggle(true)

        val failed = viewModel.content()
        assertEquals(false, failed.openToCollaboration)
        assertFalse(failed.isSaving)
        assertTrue(failed.failedToSave)

        viewModel.onSaveFailureShown()

        assertFalse(viewModel.content().failedToSave)
        assertEquals(false, viewModel.content().openToCollaboration)
    }

    @Test
    fun theSwitchWorksAgainAfterAFailure() {
        repository.saveFailure = IllegalStateException("no network")
        val viewModel = viewModel()
        viewModel.onToggle(true)

        repository.saveFailure = null
        viewModel.onToggle(true)

        assertEquals(true, viewModel.content().openToCollaboration)
        assertFalse(viewModel.content().failedToSave, "a new tap clears the old failure")
        assertEquals(listOf(true, true), repository.saved)
    }

    @Test
    fun anAccountWithoutAProfileCannotFlipTheSwitch() {
        repository.open = null
        val viewModel = viewModel()

        viewModel.onToggle(true)

        assertEquals(emptyList(), repository.saved)
        assertNull(viewModel.content().openToCollaboration)
    }

    @Test
    fun aProfileThatDisappearedLeavesTheSwitchOffAndUnavailableWithoutAFailureMessage() {
        repository.saveFailure = ProfileMissingException()
        val viewModel = viewModel()

        viewModel.onToggle(true)

        val content = viewModel.content()
        assertNull(content.openToCollaboration)
        assertFalse(content.isSaving)
        assertFalse(content.failedToSave, "the screen says what to do instead")
    }

    @Test
    fun tapsBeforeTheSettingsAreLoadedOrAfterAFailedLoadDoNothing() {
        repository.loadFailure = IllegalStateException("no network")
        val viewModel = viewModel()

        viewModel.onToggle(true)
        viewModel.onSaveFailureShown()

        assertEquals(emptyList(), repository.saved)
        assertIs<SettingsUiState.Error>(viewModel.uiState.value)
    }
}
