package org.matchlab.academic_net.feature.settings.domain.usecase

import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.feature.settings.domain.ProfileMissingException
import org.matchlab.academic_net.feature.settings.domain.SettingsRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

/** The two use cases of the one switch there is: they wrap a failure in a [Result] and never swallow a cancellation. */
class SettingsUseCasesTest {
    /** A repository that answers as the test says and remembers what it was asked to save. */
    private class FakeSettingsRepository : SettingsRepository {
        var open: Boolean? = false
        var failure: Throwable? = null
        val saved = mutableListOf<Boolean>()

        override suspend fun getOpenToCollaboration(): Boolean? {
            failure?.let { throw it }
            return open
        }

        override suspend fun setOpenToCollaboration(open: Boolean): Boolean {
            failure?.let { throw it }
            saved += open
            return open
        }
    }

    private val repository = FakeSettingsRepository()

    @Test
    fun theSwitchIsOnOrOffAsTheServerHasIt() = runTest {
        repository.open = true
        assertEquals(true, GetOpenToCollaborationUseCase(repository)().getOrThrow())

        repository.open = false
        assertEquals(false, GetOpenToCollaborationUseCase(repository)().getOrThrow())
    }

    @Test
    fun aUserWithoutAProfileHasNoSwitchAndThatIsNotAFailure() = runTest {
        repository.open = null

        val result = GetOpenToCollaborationUseCase(repository)()

        assertNull(result.getOrThrow())
    }

    @Test
    fun aFailedLoadIsAFailedResultWithItsCause() = runTest {
        val cause = IllegalStateException("no network")
        repository.failure = cause

        assertSame(cause, GetOpenToCollaborationUseCase(repository)().exceptionOrNull())
    }

    @Test
    fun savingReturnsTheValueTheServerHasAfterwards() = runTest {
        val result = SetOpenToCollaborationUseCase(repository)(true)

        assertEquals(true, result.getOrThrow())
        assertEquals(listOf(true), repository.saved)
    }

    @Test
    fun aFailedSaveIsAFailedResultAndAMissingProfileIsItsOwnFailure() = runTest {
        repository.failure = ProfileMissingException()

        val failure = SetOpenToCollaborationUseCase(repository)(true).exceptionOrNull()

        assertIs<ProfileMissingException>(failure)
        assertEquals(emptyList(), repository.saved)
    }

    @Test
    fun aCancellationIsNeverTurnedIntoAFailure() = runTest {
        repository.failure = CancellationException("screen left")

        assertFailsWith<CancellationException> { GetOpenToCollaborationUseCase(repository)() }
        assertFailsWith<CancellationException> { SetOpenToCollaborationUseCase(repository)(true) }
    }
}
