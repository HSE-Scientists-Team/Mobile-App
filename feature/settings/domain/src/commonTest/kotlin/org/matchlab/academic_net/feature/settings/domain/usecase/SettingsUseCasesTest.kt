package org.matchlab.academic_net.feature.settings.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.feature.settings.domain.FakeSettingsRepository
import org.matchlab.academic_net.feature.settings.domain.PrivacyOption
import org.matchlab.academic_net.feature.settings.domain.PrivacySettings

class SettingsUseCasesTest {
    private val repository = FakeSettingsRepository()

    @Test
    fun getReturnsTheSettings() = runTest {
        repository.settings = PrivacySettings(showParticipatedProjects = true, showContacts = false)

        val result = GetPrivacySettingsUseCase(repository)()

        assertEquals(PrivacySettings(true, false), result.getOrThrow())
    }

    @Test
    fun getFailureIsAFailedResult() = runTest {
        val error = IllegalStateException("no network")
        repository.failure = error

        val result = GetPrivacySettingsUseCase(repository)()

        assertSame(error, result.exceptionOrNull())
    }

    @Test
    fun setReturnsTheSettingsAfterTheChange() = runTest {
        val result = SetPrivacyOptionUseCase(repository)(PrivacyOption.Contacts, true)

        assertEquals(PrivacySettings(false, true), result.getOrThrow())
        assertEquals(listOf(PrivacyOption.Contacts to true), repository.changes)
    }

    @Test
    fun setFailureIsAFailedResult() = runTest {
        val error = IllegalStateException("no network")
        repository.failure = error

        val result = SetPrivacyOptionUseCase(repository)(PrivacyOption.Contacts, true)

        assertSame(error, result.exceptionOrNull())
    }

    @Test
    fun cancellationIsNotSwallowed() = runTest {
        repository.failure = CancellationException("cancelled")

        assertFailsWith<CancellationException> { GetPrivacySettingsUseCase(repository)() }
        assertFailsWith<CancellationException> { SetPrivacyOptionUseCase(repository)(PrivacyOption.Contacts, true) }
    }
}
