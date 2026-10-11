package org.matchlab.academic_net.feature.settings.ui

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.matchlab.academic_net.feature.settings.data.SettingsRepositoryImpl
import org.matchlab.academic_net.feature.settings.data.mock.MockSettingsDataSource
import org.matchlab.academic_net.feature.settings.domain.PrivacyOption
import org.matchlab.academic_net.feature.settings.domain.PrivacySettings
import org.matchlab.academic_net.feature.settings.domain.SettingsRepository
import org.matchlab.academic_net.feature.settings.domain.usecase.GetPrivacySettingsUseCase
import org.matchlab.academic_net.feature.settings.domain.usecase.SetPrivacyOptionUseCase

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /**
     * The mock server behind a spy: counts and can fail the requests and can hold the answer of
     * one option. A held answer is the state of the server at the moment it was applied, as a
     * slow network would deliver it.
     */
    private class SpyRepository(initial: PrivacySettings = PrivacySettings(false, false)) : SettingsRepository {
        private val server = SettingsRepositoryImpl(MockSettingsDataSource(latencyMillis = 0, initial = initial))
        var loads = 0
        var failingLoad = false
        var failingSave = false
        val saved = mutableListOf<Pair<PrivacyOption, Boolean>>()
        val gates = mutableMapOf<PrivacyOption, CompletableDeferred<Unit>>()

        /** Holds the answer of [option] until [release]. */
        fun hold(option: PrivacyOption) {
            gates[option] = CompletableDeferred()
        }

        fun release(option: PrivacyOption) {
            gates.getValue(option).complete(Unit)
        }

        override suspend fun getPrivacySettings(): PrivacySettings {
            loads++
            if (failingLoad) error("no network")
            return server.getPrivacySettings()
        }

        override suspend fun setPrivacyOption(option: PrivacyOption, enabled: Boolean): PrivacySettings {
            saved += option to enabled
            if (failingSave) error("no network")
            val answer = server.setPrivacyOption(option, enabled)
            gates[option]?.await()
            return answer
        }
    }

    private fun viewModel(repository: SettingsRepository) = SettingsViewModel(
        getPrivacySettings = GetPrivacySettingsUseCase(repository),
        setPrivacyOption = SetPrivacyOptionUseCase(repository),
    )

    private fun SettingsViewModel.content() = assertIs<SettingsUiState.Content>(uiState.value)

    @Test
    fun loadsTheSettingsWhenCreated() {
        val repository = SpyRepository(PrivacySettings(showParticipatedProjects = true, showContacts = false))

        val viewModel = viewModel(repository)

        val content = viewModel.content()
        assertEquals(PrivacySettings(showParticipatedProjects = true, showContacts = false), content.privacy)
        assertEquals(emptySet(), content.pending)
        assertNull(content.failedToSave)
    }

    @Test
    fun bothSwitchesAreOffByDefault() {
        val content = viewModel(SpyRepository()).content()

        assertEquals(PrivacySettings(showParticipatedProjects = false, showContacts = false), content.privacy)
    }

    @Test
    fun failedLoadIsAnErrorAndRetryLoadsAgain() {
        val repository = SpyRepository().apply { failingLoad = true }
        val viewModel = viewModel(repository)
        assertEquals(SettingsUiState.Error, viewModel.uiState.value)

        repository.failingLoad = false
        viewModel.onRetry()

        assertIs<SettingsUiState.Content>(viewModel.uiState.value)
        assertEquals(2, repository.loads)
    }

    @Test
    fun retryOnLoadedScreenAsksNothing() {
        val repository = SpyRepository()
        val viewModel = viewModel(repository)

        viewModel.onRetry()

        assertEquals(1, repository.loads)
    }

    @Test
    fun switchMovesAtOnceAndTheRowWaitsForTheServer() {
        val repository = SpyRepository().apply { hold(PrivacyOption.Contacts) }
        val viewModel = viewModel(repository)

        viewModel.onOptionChange(PrivacyOption.Contacts, true)

        val waiting = viewModel.content()
        assertEquals(true, waiting.privacy.showContacts)
        assertEquals(setOf(PrivacyOption.Contacts), waiting.pending)

        repository.release(PrivacyOption.Contacts)

        val saved = viewModel.content()
        assertEquals(true, saved.privacy.showContacts)
        assertEquals(emptySet(), saved.pending)
        assertEquals(listOf(PrivacyOption.Contacts to true), repository.saved)
    }

    @Test
    fun secondTapOnARowThatIsBeingSavedIsIgnored() {
        val repository = SpyRepository().apply { hold(PrivacyOption.Contacts) }
        val viewModel = viewModel(repository)

        viewModel.onOptionChange(PrivacyOption.Contacts, true)
        viewModel.onOptionChange(PrivacyOption.Contacts, false)

        assertEquals(1, repository.saved.size)
        assertEquals(true, viewModel.content().privacy.showContacts)
    }

    @Test
    fun settingTheValueItAlreadyHasAsksNothing() {
        val repository = SpyRepository()
        val viewModel = viewModel(repository)

        viewModel.onOptionChange(PrivacyOption.Contacts, false)

        assertEquals(emptyList(), repository.saved)
    }

    @Test
    fun failedSaveGoesBackAndIsReportedOnce() {
        val repository = SpyRepository()
        val viewModel = viewModel(repository)
        repository.failingSave = true

        viewModel.onOptionChange(PrivacyOption.ParticipatedProjects, true)

        val failed = viewModel.content()
        assertEquals(false, failed.privacy.showParticipatedProjects)
        assertEquals(emptySet(), failed.pending)
        assertEquals(PrivacyOption.ParticipatedProjects, failed.failedToSave)

        viewModel.onSaveFailureShown()

        assertNull(viewModel.content().failedToSave)
    }

    @Test
    fun rowIsFreeAgainAfterAFailureAndCanBeSavedLater() {
        val repository = SpyRepository()
        val viewModel = viewModel(repository)
        repository.failingSave = true
        viewModel.onOptionChange(PrivacyOption.Contacts, true)
        repository.failingSave = false

        viewModel.onOptionChange(PrivacyOption.Contacts, true)

        val content = viewModel.content()
        assertEquals(true, content.privacy.showContacts)
        assertNull(content.failedToSave)
    }

    @Test
    fun failureOfOneSwitchDoesNotTouchTheOther() {
        val repository = SpyRepository().apply { hold(PrivacyOption.Contacts) }
        val viewModel = viewModel(repository)
        viewModel.onOptionChange(PrivacyOption.Contacts, true)
        repository.failingSave = true

        viewModel.onOptionChange(PrivacyOption.ParticipatedProjects, true)

        val content = viewModel.content()
        assertEquals(false, content.privacy.showParticipatedProjects)
        assertEquals(true, content.privacy.showContacts)
        assertEquals(setOf(PrivacyOption.Contacts), content.pending)
    }

    @Test
    fun lateAnswerOfOneSwitchDoesNotBringBackTheOther() {
        val repository = SpyRepository().apply {
            hold(PrivacyOption.ParticipatedProjects)
            hold(PrivacyOption.Contacts)
        }
        val viewModel = viewModel(repository)

        // The answer to the first request is the state before the second switch was flipped.
        viewModel.onOptionChange(PrivacyOption.ParticipatedProjects, true)
        viewModel.onOptionChange(PrivacyOption.Contacts, true)
        repository.release(PrivacyOption.Contacts)
        repository.release(PrivacyOption.ParticipatedProjects)

        val content = viewModel.content()
        assertEquals(PrivacySettings(showParticipatedProjects = true, showContacts = true), content.privacy)
        assertEquals(emptySet(), content.pending)
    }

    @Test
    fun changeBeforeTheSettingsAreLoadedIsIgnored() {
        val repository = SpyRepository().apply { failingLoad = true }
        val viewModel = viewModel(repository)

        viewModel.onOptionChange(PrivacyOption.Contacts, true)

        assertEquals(SettingsUiState.Error, viewModel.uiState.value)
        assertEquals(emptyList(), repository.saved)
    }
}
