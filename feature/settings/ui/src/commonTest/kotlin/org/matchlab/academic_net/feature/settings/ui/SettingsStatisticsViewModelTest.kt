package org.matchlab.academic_net.feature.settings.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
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
 * "18 Настройки · Статистика": the switch of the device. It is on until the setting answers and follows it afterwards, moves
 * at once when it is flipped, takes no taps while the choice is being saved, goes back (and says so once) when saving
 * fails, and lives apart from the switch of the server: it works while that one loads or has failed to load, and a failure
 * of one never shows on the other.
 *
 * "Goes back" means: to what is in force. That is what the setting last reported, or, when it reported nothing, what the
 * switch showed before the tap. The difference matters for a refusal that could not be saved: the setting keeps it all the
 * same and reports it, so the switch stays off (with the failure told) instead of going back to on while the sending is off.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsStatisticsViewModelTest {
    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /**
     * The setting of the device behind a spy. It is silent until [answer] (a slow store: the flow has nothing to emit), then it
     * emits the stored choice and every change; a write is counted, can be held and can fail, and a write that went through
     * is published after the store took it, as the real setting does.
     */
    private class FakeStatisticsSettings : StatisticsSettings {
        private val stored = MutableStateFlow<Boolean?>(null)
        override val enabled: Flow<Boolean> = stored.filterNotNull()

        /** Every value the screen asked to save, in order, also the ones that failed. */
        val written = mutableListOf<Boolean>()
        var failure: Throwable? = null
        var gate: CompletableDeferred<Unit>? = null

        /**
         * Like the real setting, when set: a refusal that could not be saved is published all the same (the flow shows it)
         * before the failure is thrown, a consent that could not be saved is not.
         */
        var keepsARefusalWhenSavingFails = false

        /**
         * With [keepsARefusalWhenSavingFails]: whether the collector of the flow has taken the refusal before the caller
         * hears of the failure. On a device it has: the collector and the caller are resumed on the main thread, in the order
         * in which the store thread posted them, the refusal first. A test that wants the other order says `false`.
         */
        var reportsBeforeFailing = true

        /** While `false`, a save that works does not show in the flow: a setting that reports only what it is told. */
        var echoesASave = true

        /** The setting answers, or the choice changes behind the screen's back. */
        fun answer(value: Boolean) {
            stored.value = value
        }

        override suspend fun setEnabled(enabled: Boolean) {
            written += enabled
            gate?.await()
            failure?.let {
                if (!enabled && keepsARefusalWhenSavingFails) {
                    stored.value = false
                    // Lets the collector of the flow, which the new value has just resumed, run before the failure is thrown.
                    if (reportsBeforeFailing) yield()
                }
                throw it
            }
            if (echoesASave) stored.value = enabled
        }
    }

    /** The server setting, the other switch: counts the saves and can hold or fail a load and a save. */
    private class FakeServerSettings(var open: Boolean? = false) : SettingsRepository {
        var loadFailure: Throwable? = null
        var saveFailure: Throwable? = null
        var loadGate: CompletableDeferred<Unit>? = null
        var saveGate: CompletableDeferred<Unit>? = null
        val saved = mutableListOf<Boolean>()

        override suspend fun getOpenToCollaboration(): Boolean? {
            loadGate?.await()
            loadFailure?.let { throw it }
            return open
        }

        override suspend fun setOpenToCollaboration(open: Boolean): Boolean {
            saved += open
            saveGate?.await()
            saveFailure?.let { throw it }
            this.open = open
            return open
        }
    }

    private val device = FakeStatisticsSettings()
    private val server = FakeServerSettings()

    private fun viewModel() = SettingsViewModel(
        getOpenToCollaboration = GetOpenToCollaborationUseCase(server),
        setOpenToCollaboration = SetOpenToCollaborationUseCase(server),
        observeStatistics = ObserveStatisticsUseCase(device),
        setStatistics = SetStatisticsUseCase(device),
    )

    private fun SettingsViewModel.content() = assertIs<SettingsUiState.Content>(uiState.value)

    // ---- what the switch shows ----

    @Test
    fun theSwitchIsOnUntilTheSettingAnswers() {
        val viewModel = viewModel()

        assertTrue(viewModel.statistics.value)
        assertFalse(viewModel.statisticsFailedToSave.value)
        assertEquals(emptyList(), device.written)
    }

    @Test
    fun theSwitchFollowsTheSettingOnceItAnswersAndAfterThat() {
        val viewModel = viewModel()

        device.answer(false)
        assertFalse(viewModel.statistics.value)
        device.answer(true)
        assertTrue(viewModel.statistics.value)
        device.answer(false)
        assertFalse(viewModel.statistics.value)

        assertEquals(emptyList(), device.written, "following the setting saves nothing")
    }

    @Test
    fun aChoiceThatIsStoredWhenTheScreenOpensIsShownAtOnce() {
        device.answer(false)

        val viewModel = viewModel()

        assertFalse(viewModel.statistics.value)
        assertEquals(emptyList(), device.written)
    }

    // ---- flipping the switch ----

    @Test
    fun theSwitchMovesAtOnceAndStaysWhenTheChoiceIsSaved() {
        device.answer(true)
        val gate = CompletableDeferred<Unit>()
        device.gate = gate
        val viewModel = viewModel()

        viewModel.onStatisticsToggle(false)

        assertFalse(viewModel.statistics.value, "the switch moves before the save ends")
        assertEquals(listOf(false), device.written)
        assertFalse(viewModel.statisticsFailedToSave.value)

        gate.complete(Unit)

        assertFalse(viewModel.statistics.value)
        assertFalse(viewModel.statisticsFailedToSave.value)
        assertEquals(listOf(false), device.written)
    }

    @Test
    fun theSwitchCanBeFlippedOffAndOnAndOffAgain() {
        val viewModel = viewModel()

        viewModel.onStatisticsToggle(false)
        assertFalse(viewModel.statistics.value)
        viewModel.onStatisticsToggle(true)
        assertTrue(viewModel.statistics.value)
        viewModel.onStatisticsToggle(false)
        assertFalse(viewModel.statistics.value)

        assertEquals(listOf(false, true, false), device.written)
        assertFalse(viewModel.statisticsFailedToSave.value)
    }

    @Test
    fun aSecondTapWhileTheChoiceIsBeingSavedIsIgnored() {
        val gate = CompletableDeferred<Unit>()
        device.gate = gate
        val viewModel = viewModel()

        viewModel.onStatisticsToggle(false)
        viewModel.onStatisticsToggle(true)
        viewModel.onStatisticsToggle(false)
        viewModel.onStatisticsToggle(true)
        assertFalse(viewModel.statistics.value, "the taps during the save do not move the switch")
        gate.complete(Unit)

        assertEquals(listOf(false), device.written, "a later tap could overtake the first save")
        assertFalse(viewModel.statistics.value)
    }

    @Test
    fun theSwitchTakesTapsAgainWhenTheSaveEnded() {
        val gate = CompletableDeferred<Unit>()
        device.gate = gate
        val viewModel = viewModel()
        viewModel.onStatisticsToggle(false)
        viewModel.onStatisticsToggle(true)
        gate.complete(Unit)

        device.gate = null
        viewModel.onStatisticsToggle(true)

        assertEquals(listOf(false, true), device.written)
        assertTrue(viewModel.statistics.value)
    }

    @Test
    fun aTapOnTheValueTheSwitchAlreadyHasSavesNothing() {
        val viewModel = viewModel()

        viewModel.onStatisticsToggle(true)
        assertEquals(emptyList(), device.written, "on is the value before the setting answers")

        device.answer(false)
        viewModel.onStatisticsToggle(false)

        assertEquals(emptyList(), device.written)
        assertFalse(viewModel.statistics.value)
        assertFalse(viewModel.statisticsFailedToSave.value)
    }

    // ---- a failed save ----

    @Test
    fun aFailedSaveMovesTheSwitchBackAndTellsOnce() {
        device.answer(true)
        device.failure = IllegalStateException("the store cannot write")
        val viewModel = viewModel()

        viewModel.onStatisticsToggle(false)

        assertTrue(viewModel.statistics.value, "the switch went back")
        assertTrue(viewModel.statisticsFailedToSave.value)
        assertEquals(listOf(false), device.written)

        viewModel.onStatisticsSaveFailureShown()

        assertFalse(viewModel.statisticsFailedToSave.value)
        assertTrue(viewModel.statistics.value)
    }

    @Test
    fun theSwitchMovesAtOnceEvenIfTheSaveWillFailAndGoesBackWhenItDoes() {
        val gate = CompletableDeferred<Unit>()
        device.gate = gate
        device.failure = IllegalStateException("the store cannot write")
        val viewModel = viewModel()

        viewModel.onStatisticsToggle(false)
        assertFalse(viewModel.statistics.value)
        assertFalse(viewModel.statisticsFailedToSave.value)
        gate.complete(Unit)

        assertTrue(viewModel.statistics.value)
        assertTrue(viewModel.statisticsFailedToSave.value)
    }

    @Test
    fun theSwitchGoesBackToTheValueItHadNotToTheDefault() {
        device.answer(false)
        device.failure = IllegalStateException("the store cannot write")
        val viewModel = viewModel()
        assertFalse(viewModel.statistics.value)

        viewModel.onStatisticsToggle(true)

        assertFalse(viewModel.statistics.value, "off was chosen earlier, a failed save does not turn it on")
        assertTrue(viewModel.statisticsFailedToSave.value)
    }

    @Test
    fun theSwitchWorksAgainAfterAFailureAndANewTapClearsTheOldFailure() {
        device.failure = IllegalStateException("the store cannot write")
        val viewModel = viewModel()
        viewModel.onStatisticsToggle(false)
        assertTrue(viewModel.statisticsFailedToSave.value)

        device.failure = null
        viewModel.onStatisticsToggle(false)

        assertFalse(viewModel.statistics.value)
        assertFalse(viewModel.statisticsFailedToSave.value, "a new tap clears the old failure")
        assertEquals(listOf(false, false), device.written)
    }

    @Test
    fun aFailedSaveFreesTheSwitchForTheNextTapEvenWhenTheFailureWasNotShown() {
        device.failure = IllegalStateException("the store cannot write")
        val viewModel = viewModel()
        viewModel.onStatisticsToggle(false)

        viewModel.onStatisticsToggle(false)

        assertEquals(listOf(false, false), device.written, "the second tap was not swallowed by a stuck guard")
    }

    @Test
    fun tellingThatNothingFailedChangesNothing() {
        val viewModel = viewModel()

        viewModel.onStatisticsSaveFailureShown()

        assertFalse(viewModel.statisticsFailedToSave.value)
        assertTrue(viewModel.statistics.value)
    }

    // ---- a refusal that could not be saved is in force all the same ----

    @Test
    fun aRefusalThatCouldNotBeSavedStaysOffAndTheFailureIsToldOnce() {
        device.answer(true)
        device.failure = IllegalStateException("the store cannot write")
        device.keepsARefusalWhenSavingFails = true
        val viewModel = viewModel()

        viewModel.onStatisticsToggle(false)

        assertFalse(viewModel.statistics.value, "the setting reported the refusal, so it is in force and the switch stays off")
        assertTrue(viewModel.statisticsFailedToSave.value)
        assertEquals(listOf(false), device.written)

        viewModel.onStatisticsSaveFailureShown()

        assertFalse(viewModel.statisticsFailedToSave.value)
        assertFalse(viewModel.statistics.value)
    }

    @Test
    fun aRefusalThatCouldNotBeSavedStaysOffAlsoWhenTheFlowReportsItOnlyAfterTheFailure() {
        device.answer(true)
        device.failure = IllegalStateException("the store cannot write")
        device.keepsARefusalWhenSavingFails = true
        device.reportsBeforeFailing = false
        val viewModel = viewModel()

        viewModel.onStatisticsToggle(false)

        // The failure came first and put the switch back to what it had (on); the report of the refusal follows at once and
        // settles it: the switch ends off, as the sending is.
        assertFalse(viewModel.statistics.value)
        assertTrue(viewModel.statisticsFailedToSave.value)
    }

    @Test
    fun aRefusalThatCouldNotBeSavedStaysOffAlsoWhenTheSettingHadNotAnsweredBefore() {
        device.failure = IllegalStateException("the store cannot write")
        device.keepsARefusalWhenSavingFails = true
        val viewModel = viewModel()
        assertTrue(viewModel.statistics.value, "on until the setting answers")

        viewModel.onStatisticsToggle(false)

        assertFalse(viewModel.statistics.value)
        assertTrue(viewModel.statisticsFailedToSave.value)
    }

    @Test
    fun aRefusalThatCouldNotBeSavedStaysOffWhenTheSaveWasHeldAndThenFailed() {
        device.answer(true)
        val gate = CompletableDeferred<Unit>()
        device.gate = gate
        device.failure = IllegalStateException("the store cannot write")
        device.keepsARefusalWhenSavingFails = true
        val viewModel = viewModel()

        viewModel.onStatisticsToggle(false)
        assertFalse(viewModel.statistics.value, "moves at once")
        assertFalse(viewModel.statisticsFailedToSave.value)
        gate.complete(Unit)

        assertFalse(viewModel.statistics.value, "and does not come back on when the save fails")
        assertTrue(viewModel.statisticsFailedToSave.value)
    }

    @Test
    fun aConsentThatCouldNotBeSavedGoesBackToOffWhateverTheSettingKeepsOfRefusals() {
        device.answer(false)
        device.failure = IllegalStateException("the store cannot write")
        device.keepsARefusalWhenSavingFails = true
        val viewModel = viewModel()

        viewModel.onStatisticsToggle(true)

        assertFalse(viewModel.statistics.value, "a consent that was not saved must not switch the sending on")
        assertTrue(viewModel.statisticsFailedToSave.value)
    }

    @Test
    fun theSwitchWorksAgainAfterARefusalThatCouldNotBeSaved() {
        device.answer(true)
        device.failure = IllegalStateException("the store cannot write")
        device.keepsARefusalWhenSavingFails = true
        val viewModel = viewModel()
        viewModel.onStatisticsToggle(false)
        assertTrue(viewModel.statisticsFailedToSave.value)

        device.failure = null
        viewModel.onStatisticsToggle(true)

        assertTrue(viewModel.statistics.value)
        assertFalse(viewModel.statisticsFailedToSave.value, "a new tap clears the old failure")
        assertEquals(listOf(false, true), device.written)
    }

    @Test
    fun aTapOnTheValueTheSwitchHasAfterARefusalThatCouldNotBeSavedSavesNothing() {
        device.answer(true)
        device.failure = IllegalStateException("the store cannot write")
        device.keepsARefusalWhenSavingFails = true
        val viewModel = viewModel()
        viewModel.onStatisticsToggle(false)

        viewModel.onStatisticsToggle(false)

        assertEquals(listOf(false), device.written, "the switch shows off, so off is not asked for again")
    }

    @Test
    fun aFailedSaveGoesBackToWhatTheSettingReportedMeanwhileNotToWhatTheSwitchHad() {
        device.answer(false)
        val gate = CompletableDeferred<Unit>()
        device.gate = gate
        device.failure = IllegalStateException("the store cannot write")
        val viewModel = viewModel()

        viewModel.onStatisticsToggle(true)
        assertTrue(viewModel.statistics.value, "moves at once")
        // A yes is reported meanwhile (made somewhere else), and then the save that was on its way fails.
        device.answer(true)
        gate.complete(Unit)

        assertTrue(viewModel.statistics.value, "what is in force is the yes the setting reported, not the no the switch had")
        assertTrue(viewModel.statisticsFailedToSave.value)
    }

    @Test
    fun aFailedSaveOfASilentSettingGoesBackToWhatTheSwitchHad() {
        // The flow never reports anything (a save that works is not echoed), so there is only the switch to go back to.
        device.echoesASave = false
        val viewModel = viewModel()
        viewModel.onStatisticsToggle(false)
        assertFalse(viewModel.statistics.value)
        device.failure = IllegalStateException("the store cannot write")

        viewModel.onStatisticsToggle(true)

        assertFalse(viewModel.statistics.value, "back to the value before the tap, which is all there is")
        assertTrue(viewModel.statisticsFailedToSave.value)
    }

    @Test
    fun aFailedSaveWhenTheSettingNeverAnsweredGoesBackToTheDefault() {
        device.failure = IllegalStateException("the store cannot write")
        val viewModel = viewModel()

        viewModel.onStatisticsToggle(false)

        assertTrue(viewModel.statistics.value, "no answer, no refusal kept: back to the default, on")
        assertTrue(viewModel.statisticsFailedToSave.value)
    }

    @Test
    fun aRefusalThatCouldNotBeSavedDoesNotTouchTheOtherSwitch() {
        server.open = true
        device.answer(true)
        device.failure = IllegalStateException("the store cannot write")
        device.keepsARefusalWhenSavingFails = true
        val viewModel = viewModel()
        val before = viewModel.content()

        viewModel.onStatisticsToggle(false)

        assertEquals(before, viewModel.content())
        assertFalse(viewModel.content().failedToSave)
        assertEquals(emptyList(), server.saved)
    }

    // ---- apart from the switch of the server ----

    @Test
    fun theSwitchWorksWhileTheServerSettingIsLoading() {
        val gate = CompletableDeferred<Unit>()
        server.loadGate = gate
        val viewModel = viewModel()
        assertEquals(SettingsUiState.Loading, viewModel.uiState.value)

        viewModel.onStatisticsToggle(false)

        assertFalse(viewModel.statistics.value)
        assertEquals(listOf(false), device.written)
        assertEquals(SettingsUiState.Loading, viewModel.uiState.value)

        gate.complete(Unit)

        assertEquals(SettingsUiState.Content(openToCollaboration = false), viewModel.uiState.value)
        assertFalse(viewModel.statistics.value, "the answer of the server does not touch the switch of the device")
    }

    @Test
    fun aFailureOfTheSaveWhileTheServerSettingIsLoadingIsToldAndLeavesTheLoadingAlone() {
        val gate = CompletableDeferred<Unit>()
        server.loadGate = gate
        device.failure = IllegalStateException("the store cannot write")
        val viewModel = viewModel()

        viewModel.onStatisticsToggle(false)

        assertTrue(viewModel.statistics.value)
        assertTrue(viewModel.statisticsFailedToSave.value)
        assertEquals(SettingsUiState.Loading, viewModel.uiState.value)
        gate.complete(Unit)
        assertFalse(viewModel.content().failedToSave)
    }

    @Test
    fun theSwitchWorksAfterTheServerSettingFailedToLoadAndRetryLeavesItAlone() {
        val cause = IllegalStateException("no network")
        server.loadFailure = cause
        val viewModel = viewModel()
        assertEquals(SettingsUiState.Error(cause), viewModel.uiState.value)

        viewModel.onStatisticsToggle(false)

        assertFalse(viewModel.statistics.value)
        assertEquals(listOf(false), device.written)
        assertEquals(SettingsUiState.Error(cause), viewModel.uiState.value)

        server.loadFailure = null
        viewModel.onRetry()

        assertIs<SettingsUiState.Content>(viewModel.uiState.value)
        assertFalse(viewModel.statistics.value, "reloading the server setting does not reset the switch of the device")
    }

    @Test
    fun aFailedSaveAfterAFailedLoadIsToldAndKeepsTheErrorState() {
        val cause = IllegalStateException("no network")
        server.loadFailure = cause
        device.failure = IllegalStateException("the store cannot write")
        val viewModel = viewModel()

        viewModel.onStatisticsToggle(false)

        assertTrue(viewModel.statistics.value)
        assertTrue(viewModel.statisticsFailedToSave.value)
        assertEquals(SettingsUiState.Error(cause), viewModel.uiState.value)
    }

    @Test
    fun theSwitchWorksForAnAccountWithoutAProfileToo() {
        server.open = null
        val viewModel = viewModel()

        viewModel.onStatisticsToggle(false)

        assertFalse(viewModel.statistics.value)
        assertEquals(listOf(false), device.written)
        assertNull(viewModel.content().openToCollaboration)
    }

    @Test
    fun aFailedSaveOfTheStatisticsLeavesTheOtherSwitchAlone() {
        server.open = true
        device.failure = IllegalStateException("the store cannot write")
        val viewModel = viewModel()
        val before = viewModel.content()

        viewModel.onStatisticsToggle(false)

        assertTrue(viewModel.statisticsFailedToSave.value)
        assertEquals(before, viewModel.content())
        assertFalse(viewModel.content().failedToSave)
        assertFalse(viewModel.content().isSaving)
        assertEquals(true, viewModel.content().openToCollaboration)
        assertEquals(emptyList(), server.saved)
    }

    @Test
    fun aFailedSaveOfTheOtherSwitchDoesNotTouchTheStatistics() {
        server.saveFailure = IllegalStateException("no network")
        val viewModel = viewModel()

        viewModel.onToggle(true)

        assertTrue(viewModel.content().failedToSave)
        assertFalse(viewModel.statisticsFailedToSave.value)
        assertTrue(viewModel.statistics.value)
        assertEquals(emptyList(), device.written)
    }

    @Test
    fun eachFailureIsClearedByItsOwnCallAndTheOtherStaysUntilItIsToldToo() {
        server.saveFailure = IllegalStateException("no network")
        device.failure = IllegalStateException("the store cannot write")
        val viewModel = viewModel()
        viewModel.onToggle(true)
        viewModel.onStatisticsToggle(false)
        assertTrue(viewModel.content().failedToSave)
        assertTrue(viewModel.statisticsFailedToSave.value)

        viewModel.onSaveFailureShown()
        assertFalse(viewModel.content().failedToSave)
        assertTrue(viewModel.statisticsFailedToSave.value)

        viewModel.onStatisticsSaveFailureShown()
        assertFalse(viewModel.statisticsFailedToSave.value)
        assertFalse(viewModel.content().failedToSave)
    }

    @Test
    fun savingTheStatisticsDoesNotMakeTheOtherSwitchBusy() {
        val gate = CompletableDeferred<Unit>()
        device.gate = gate
        val viewModel = viewModel()

        viewModel.onStatisticsToggle(false)

        assertFalse(viewModel.content().isSaving, "the other switch is not dimmed by this save")
        viewModel.onToggle(true)
        assertEquals(listOf(true), server.saved, "the other switch takes its tap while the statistics are being saved")
        assertEquals(true, viewModel.content().openToCollaboration)
        gate.complete(Unit)
        assertFalse(viewModel.statistics.value)
    }

    @Test
    fun savingTheOtherSwitchDoesNotBlockTheStatistics() {
        val gate = CompletableDeferred<Unit>()
        server.saveGate = gate
        val viewModel = viewModel()

        viewModel.onToggle(true)
        assertTrue(viewModel.content().isSaving)
        viewModel.onStatisticsToggle(false)

        assertEquals(listOf(false), device.written, "the statistics take their tap while the other switch is being saved")
        assertFalse(viewModel.statistics.value)
        assertTrue(viewModel.content().isSaving, "the save of the statistics does not end the other one")
        gate.complete(Unit)
        assertEquals(true, viewModel.content().openToCollaboration)
        assertFalse(viewModel.content().isSaving)
        assertFalse(viewModel.statistics.value)
    }
}
