package org.matchlab.academic_net.feature.auth.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.feature.auth.data.mock.MockAuthDataSource
import org.matchlab.academic_net.feature.auth.domain.AccessToken
import org.matchlab.academic_net.feature.auth.domain.AuthState
import org.matchlab.academic_net.feature.auth.domain.CodeRequestOutcome
import org.matchlab.academic_net.feature.auth.domain.CodeVerificationOutcome
import org.matchlab.academic_net.feature.auth.domain.Session
import org.matchlab.academic_net.securestore.InMemorySecureStore
import org.matchlab.academic_net.securestore.SecureStore

private val Saved = Session(UserId("user-me"), "student@hse.ru", AccessToken("secret"))

/** A server whose revoke call can be made to fail; everything else goes to a [MockAuthDataSource]. */
private class RevokeSpy(
    private val delegate: AuthDataSource,
    var failRevoke: Boolean = false,
) : AuthDataSource by delegate {
    val revoked = mutableListOf<AccessToken>()

    override suspend fun revoke(token: AccessToken) {
        revoked += token
        if (failRevoke) error("no network")
        delegate.revoke(token)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AuthRepositoryImplTest {
    private val time = TestTimeSource()
    private val store = InMemorySecureStore()

    private fun TestScope.repository(
        source: AuthDataSource = MockAuthDataSource(latencyMillis = 0, timeSource = time),
        secureStore: SecureStore = store,
        io: CoroutineDispatcher = UnconfinedTestDispatcher(testScheduler),
    ) = AuthRepositoryImpl(source, SessionStorage(secureStore), io, time)

    @Test
    fun startsRestoring() = runTest {
        assertEquals(AuthState.Restoring, repository().authState.value)
    }

    @Test
    fun emptyStoreRestoresToSignedOut() = runTest {
        val repository = repository()

        repository.restoreSession()

        assertEquals(AuthState.SignedOut, repository.authState.value)
    }

    @Test
    fun savedSessionRestoresToSignedIn() = runTest {
        SessionStorage(store).save(Saved)
        val repository = repository()

        repository.restoreSession()

        assertEquals(AuthState.SignedIn(Saved), repository.authState.value)
    }

    @Test
    fun partialRecordRestoresToSignedOutAndIsErased() = runTest {
        store.write("session.user_id", "user-me")
        val repository = repository()

        repository.restoreSession()

        assertEquals(AuthState.SignedOut, repository.authState.value)
        assertNull(store.read("session.user_id"))
    }

    @Test
    fun unreadableStoreRestoresToSignedOut() = runTest {
        val broken = object : SecureStore {
            override fun read(key: String): String? = error("keystore is locked")

            override fun write(key: String, value: String) = Unit

            override fun remove(key: String) = Unit

            override fun wipe() = Unit
        }
        val repository = repository(secureStore = broken)

        repository.restoreSession()

        assertEquals(AuthState.SignedOut, repository.authState.value)
    }

    @Test
    fun restoreOnlyRunsOnce() = runTest {
        SessionStorage(store).save(Saved)
        val repository = repository()
        repository.restoreSession()
        repository.signOut()

        repository.restoreSession()

        assertEquals(AuthState.SignedOut, repository.authState.value)
    }

    @Test
    fun verifiedCodeSavesTheSessionAndSignsIn() = runTest {
        val repository = repository()
        repository.restoreSession()
        repository.requestCode("student@hse.ru")

        val outcome = repository.verifyCode("student@hse.ru", "123456")

        val session = assertIs<CodeVerificationOutcome.Verified>(outcome).session
        assertEquals(AuthState.SignedIn(session), repository.authState.value)
        assertEquals(session, SessionStorage(store).load())
    }

    @Test
    fun signedInSessionIsRestoredByAnotherRepositoryOnTheSameStore() = runTest {
        val first = repository()
        first.restoreSession()
        first.requestCode("student@hse.ru")
        val session = assertIs<CodeVerificationOutcome.Verified>(first.verifyCode("student@hse.ru", "123456")).session

        val second = repository()
        second.restoreSession()

        assertEquals(AuthState.SignedIn(session), second.authState.value)
    }

    @Test
    fun wrongCodeKeepsTheUserSignedOut() = runTest {
        val repository = repository()
        repository.restoreSession()
        repository.requestCode("student@hse.ru")

        val outcome = repository.verifyCode("student@hse.ru", "000000")

        assertEquals(CodeVerificationOutcome.WrongCode(4), outcome)
        assertEquals(AuthState.SignedOut, repository.authState.value)
        assertNull(SessionStorage(store).load())
    }

    @Test
    fun failedWriteStillSignsInForTheRestOfTheProcess() = runTest {
        val readOnly = object : SecureStore by store {
            override fun write(key: String, value: String) = error("disk is full")
        }
        val repository = repository(secureStore = readOnly)
        repository.restoreSession()
        repository.requestCode("student@hse.ru")

        val outcome = repository.verifyCode("student@hse.ru", "123456")

        val session = assertIs<CodeVerificationOutcome.Verified>(outcome).session
        assertEquals(AuthState.SignedIn(session), repository.authState.value)
        assertNull(SessionStorage(store).load())
    }

    @Test
    fun lastChallengeCountsDownWithTime() = runTest {
        val repository = repository()
        assertNull(repository.lastChallenge("student@hse.ru"))

        val sent = assertIs<CodeRequestOutcome.Sent>(repository.requestCode("student@hse.ru"))
        assertEquals(60.seconds, sent.challenge.resendAfter)
        assertEquals(60.seconds, repository.lastChallenge("student@hse.ru")?.resendAfter)

        time += 45.seconds
        assertEquals(15.seconds, repository.lastChallenge("student@hse.ru")?.resendAfter)

        time += 100.seconds
        assertEquals(0.seconds, repository.lastChallenge("student@hse.ru")?.resendAfter)
    }

    @Test
    fun lastChallengeIsPerAddress() = runTest {
        val repository = repository()
        repository.requestCode("a@hse.ru")

        assertNull(repository.lastChallenge("b@hse.ru"))
    }

    @Test
    fun refusedRequestsLeaveNoChallenge() = runTest {
        val repository = repository()

        assertEquals(CodeRequestOutcome.UntrustedDomain, repository.requestCode("student@example.com"))

        assertNull(repository.lastChallenge("student@example.com"))
    }

    @Test
    fun challengeIsForgottenAfterSignIn() = runTest {
        val repository = repository()
        repository.requestCode("student@hse.ru")
        repository.verifyCode("student@hse.ru", "123456")

        assertNull(repository.lastChallenge("student@hse.ru"))
    }

    @Test
    fun signOutErasesTheSessionAndRevokesTheToken() = runTest {
        val spy = RevokeSpy(MockAuthDataSource(latencyMillis = 0, timeSource = time))
        val repository = repository(source = spy)
        repository.restoreSession()
        repository.requestCode("student@hse.ru")
        val session = assertIs<CodeVerificationOutcome.Verified>(repository.verifyCode("student@hse.ru", "123456")).session

        repository.signOut()

        assertEquals(AuthState.SignedOut, repository.authState.value)
        assertNull(SessionStorage(store).load())
        assertEquals(listOf(session.accessToken), spy.revoked)
    }

    @Test
    fun signOutErasesTheStoreEvenIfRevokeFails() = runTest {
        val spy = RevokeSpy(MockAuthDataSource(latencyMillis = 0, timeSource = time), failRevoke = true)
        val repository = repository(source = spy)
        repository.restoreSession()
        repository.requestCode("student@hse.ru")
        repository.verifyCode("student@hse.ru", "123456")

        repository.signOut()

        assertEquals(AuthState.SignedOut, repository.authState.value)
        assertNull(SessionStorage(store).load())
        assertTrue(spy.revoked.isNotEmpty())
    }

    /** A store whose [remove] fails [failingRemoves] times, then works; counts the calls. */
    private class FlakyStore(
        private val inner: SecureStore,
        var failingRemoves: Int = Int.MAX_VALUE,
        var failingWipe: Boolean = false,
    ) : SecureStore by inner {
        var wipes = 0
        var reads = 0

        override fun read(key: String): String? {
            reads++
            return inner.read(key)
        }

        override fun remove(key: String) {
            if (failingRemoves > 0) {
                failingRemoves--
                error("cannot remove")
            }
            inner.remove(key)
        }

        override fun wipe() {
            wipes++
            if (failingWipe) error("cannot wipe")
            inner.wipe()
        }
    }

    @Test
    fun signOutIsOutEvenIfTheStoreCannotBeCleared() = runTest {
        SessionStorage(store).save(Saved)
        val repository = repository(secureStore = FlakyStore(store, failingWipe = true))
        repository.restoreSession()

        repository.signOut()

        assertEquals(AuthState.SignedOut, repository.authState.value)
    }

    @Test
    fun signOutRetriesTheEraseAndDoesNotWipeWhenARetrySucceeds() = runTest {
        SessionStorage(store).save(Saved)
        // The first clear fails on all of its three entries, the second one succeeds.
        val flaky = FlakyStore(store, failingRemoves = 3)
        val repository = repository(secureStore = flaky)
        repository.restoreSession()

        repository.signOut()

        assertEquals(0, flaky.wipes)
        assertNull(SessionStorage(store).load())
        assertNull(store.read("session.access_token"))
    }

    @Test
    fun signOutGivesUpEraseAfterThreeAttemptsAndWipesTheStore() = runTest {
        SessionStorage(store).save(Saved)
        val flaky = FlakyStore(store)
        val repository = repository(secureStore = flaky)
        repository.restoreSession()
        val removesBefore = flaky.failingRemoves

        repository.signOut()

        // Three attempts at three entries each, then exactly one wipe.
        assertEquals(removesBefore - 9, flaky.failingRemoves)
        assertEquals(1, flaky.wipes)
        assertEquals(AuthState.SignedOut, repository.authState.value)
    }

    @Test
    fun anotherRepositoryOnTheSameStoreIsSignedOutWhenRemoveFailedAndWipeCleared() = runTest {
        SessionStorage(store).save(Saved)
        val first = repository(secureStore = FlakyStore(store))
        first.restoreSession()

        first.signOut()

        val second = repository(secureStore = store)
        second.restoreSession()
        assertEquals(AuthState.SignedOut, second.authState.value)
    }

    @Test
    fun sessionStaysOnTheDiskOnlyWhenEvenWipeFails() = runTest {
        // The honest limit: with a store that cannot erase at all, the next process finds the session.
        SessionStorage(store).save(Saved)
        val repository = repository(secureStore = FlakyStore(store, failingWipe = true))
        repository.restoreSession()

        repository.signOut()

        assertEquals(AuthState.SignedOut, repository.authState.value)
        assertEquals(Saved, SessionStorage(store).load())
    }

    @Test
    fun failedEraseKeepsTheProcessFromRestoringTheSessionAgain() = runTest {
        SessionStorage(store).save(Saved)
        val flaky = FlakyStore(store, failingWipe = true)
        val repository = repository(secureStore = flaky)
        // Signed out before the restore got its turn: the record is still readable on the disk.
        repository.signOut()
        val readsBefore = flaky.reads

        repository.restoreSession()

        assertEquals(AuthState.SignedOut, repository.authState.value)
        assertEquals(readsBefore, flaky.reads)
    }

    @Test
    fun signOutWhenSignedOutIsHarmless() = runTest {
        val spy = RevokeSpy(MockAuthDataSource(latencyMillis = 0, timeSource = time))
        val repository = repository(source = spy)
        repository.restoreSession()

        repository.signOut()

        assertEquals(AuthState.SignedOut, repository.authState.value)
        assertTrue(spy.revoked.isEmpty())
    }
}
