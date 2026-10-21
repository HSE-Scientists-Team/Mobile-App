package org.matchlab.academic_net.feature.auth.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.feature.auth.domain.AccessToken
import org.matchlab.academic_net.feature.auth.domain.AuthState
import org.matchlab.academic_net.feature.auth.domain.Credentials
import org.matchlab.academic_net.feature.auth.domain.Password
import org.matchlab.academic_net.feature.auth.domain.SignInOutcome
import org.matchlab.academic_net.securestore.SecureStore

private const val SessionEntry = "session.v1"
private val EntriesOfThePreviousFormat = listOf("session.user_id", "session.email", "session.access_token")

/** What a failure of the server or of the network looks like to the repository, in every shape. */
private fun failuresOfTheServer(): List<Throwable> = listOf(
    RemoteFailure.NoConnection(),
    RemoteFailure.Timeout(),
    RemoteFailure.Malformed(),
    RemoteFailure.Http(404),
    RemoteFailure.Http(429),
    RemoteFailure.Http(500),
    RemoteFailure.Http(503),
    IllegalStateException("a bug in the data source"),
)

/**
 * "The sign-in is not lost when the app is opened again." One test class for everything that
 * decides it: what is saved, what a launch does with it when the network, the server or the store
 * misbehave, and when a session really ends.
 *
 * Every "launch" below is a new [AuthRepositoryImpl] over the same disk ([store]) and its own server.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionContinuityTest : AuthRepositoryTestBase() {
    /** A launch of the app: a new repository over the disk, talking to [server]. */
    private suspend fun TestScope.launchApp(
        server: AuthDataSource = FakeAuthDataSource(),
        secureStore: SecureStore = store,
    ) = repository(server = server, secureStore = secureStore).also { it.restoreSession() }

    private fun serverWhere(check: suspend (AccessToken) -> SessionCheck) =
        FakeAuthDataSource().apply { onCheck = check }

    /**
     * The answer of a server that does not answer, as far as the code that waits for 5 seconds can tell. It
     * comes after [NeverMillis] of the test's clock, which a test runs through at once, so that code which
     * forgets to stop waiting makes its test fail instead of hang (a call that really never returns would
     * hang a `NonCancellable` block for good).
     */
    private suspend fun answerAfterNeverMillis(): SessionCheck {
        delay(NeverMillis)
        return SessionCheck.Valid(SavedSession.userId)
    }

    // ---- the app is opened again after a sign-in ----

    @Test
    fun nextLaunchIsSignedInBeforeTheServerIsAskedAnything() = runTest {
        val session = signedOutRepository().signInAsHunter()
        val server = FakeAuthDataSource()
        val answer = CompletableDeferred<SessionCheck>()
        lateinit var launched: AuthRepositoryImpl
        var stateWhenAsked: AuthState? = null
        server.onCheck = {
            stateWhenAsked = launched.authState.value
            answer.await()
        }
        launched = repository(server = server)

        val restoring = launch { launched.restoreSession() }
        runCurrent()

        assertEquals(AuthState.SignedIn(session), launched.authState.value)
        // The one thing the server was asked is whether it still accepts the token, and by then the user was in.
        assertEquals(AuthState.SignedIn(session), stateWhenAsked)
        assertEquals(listOf(session.accessToken), server.checked)
        assertEquals(emptyList(), server.signedIn)
        assertEquals(emptyList(), server.registered)
        assertEquals(emptyList(), server.confirmed)
        assertEquals(emptyList(), server.revoked)

        answer.complete(SessionCheck.Valid(session.userId))
        restoring.join()
        assertEquals(AuthState.SignedIn(session), launched.authState.value)
    }

    @Test
    fun everyLaunchAfterTheSignInFindsTheSession() = runTest {
        val session = signedOutRepository().signInAsHunter()

        repeat(3) {
            val launched = launchApp()

            assertEquals(AuthState.SignedIn(session), launched.authState.value)
        }

        assertEquals(session, SessionStorage(store).load())
    }

    @Test
    fun nextLaunchAfterTheLinkWasConfirmedIsSignedInToo() = runTest {
        val repository = signedOutRepository()
        repository.register(Hunter)
        repository.confirmEmail(HunterAddress, "abc")
        val session = repository.session()

        assertEquals(AuthState.SignedIn(session), launchApp().authState.value)
    }

    @Test
    fun nextLaunchAfterANewSignInHasTheNewSessionOnly() = runTest {
        val first = signedOutRepository()
        first.signInAsHunter()
        first.signOut()
        val second = first.signInAsHunter()

        assertEquals(AuthState.SignedIn(second), launchApp().authState.value)
    }

    // ---- no network, a broken server ----

    @Test
    fun launchWithoutTheNetworkKeepsTheSessionWhateverTheFailure() = runTest {
        val session = signedOutRepository().signInAsHunter()
        val record = store.read(SessionEntry)
        assertNotNull(record)

        for (failure in failuresOfTheServer()) {
            val launched = launchApp(serverWhere { throw failure })

            assertEquals(AuthState.SignedIn(session), launched.authState.value, "failure $failure")
            // The record on the disk is the one that was there.
            assertEquals(record, store.read(SessionEntry), "failure $failure")
        }
    }

    @Test
    fun launchesWithoutTheNetworkInARowDoNotWearTheSessionOut() = runTest {
        val session = signedOutRepository().signInAsHunter()

        repeat(5) {
            val launched = launchApp(serverWhere { throw RemoteFailure.NoConnection() })
            assertEquals(AuthState.SignedIn(session), launched.authState.value)
        }

        // The network is back, and the server accepts the token: still the same session.
        val online = launchApp(serverWhere { SessionCheck.Valid(session.userId) })
        assertEquals(AuthState.SignedIn(session), online.authState.value)
        assertEquals(session, SessionStorage(store).load())
    }

    @Test
    fun serverThatDoesNotAnswerForALongTimeKeepsTheUserIn() = runTest {
        val session = signedOutRepository().signInAsHunter()
        val launched = repository(server = serverWhere { awaitCancellation() })

        val restoring = launch { launched.restoreSession() }
        advanceTimeBy(60_000)
        runCurrent()

        // A minute without an answer, and the user has been in all the time.
        assertEquals(AuthState.SignedIn(session), launched.authState.value)
        assertTrue(restoring.isActive)
        restoring.cancel()
    }

    @Test
    fun launchWhereTheServerAcceptsTheTokenChangesNothing() = runTest {
        val session = signedOutRepository().signInAsHunter()
        val record = store.read(SessionEntry)

        val launched = launchApp(serverWhere { SessionCheck.Valid(session.userId) })

        assertEquals(AuthState.SignedIn(session), launched.authState.value)
        assertEquals(record, store.read(SessionEntry))
    }

    // ---- the server says no ----

    @Test
    fun launchWhereTheServerRefusesTheTokenEndsTheSessionAndErasesIt() = runTest {
        signedOutRepository().signInAsHunter()

        val launched = launchApp(serverWhere { SessionCheck.Rejected })

        assertEquals(AuthState.SignedOut(sessionExpired = true, lastEmail = HunterAddress), launched.authState.value)
        assertNull(store.read(SessionEntry))
        for (key in EntriesOfThePreviousFormat) assertNull(store.read(key), key)
        assertNull(SessionStorage(store).load())
        // The address stays, to type only the password.
        assertEquals(HunterAddress, SessionStorage(store).loadLastEmail())
    }

    @Test
    fun launchAfterAnEndedSessionAsksNobodyAndKnowsTheAddress() = runTest {
        signedOutRepository().signInAsHunter()
        launchApp(serverWhere { SessionCheck.Rejected })
        val server = FakeAuthDataSource()

        val launched = launchApp(server)

        // "Сеанс завершён" was said once, in the process where it happened.
        assertEquals(AuthState.SignedOut(lastEmail = HunterAddress), launched.authState.value)
        assertEquals(emptyList(), server.checked)
    }

    @Test
    fun launchWhereTheTokenIsAnotherUsersEndsTheSession() = runTest {
        signedOutRepository().signInAsHunter()

        val launched = launchApp(serverWhere { SessionCheck.Valid(UserId("somebody-else")) })

        assertEquals(AuthState.SignedOut(sessionExpired = true, lastEmail = HunterAddress), launched.authState.value)
        assertNull(SessionStorage(store).load())
    }

    // ---- a store that cannot be read for the moment ----

    @Test
    fun storeThatCannotBeReadAtStartDoesNotLoseTheSession() = runTest {
        val session = signedOutRepository().signInAsHunter()
        val record = store.read(SessionEntry)
        val unreadableCounts = listOf(1, 2, 3, 4, 5, 6, Int.MAX_VALUE)

        for (unreadableReads in unreadableCounts) {
            val locked = UnreadableStore(store, unreadableReads)
            val server = FakeAuthDataSource()

            val launched = launchApp(server, secureStore = locked)

            // The session cannot be seen for the moment, and it is not gone.
            assertIs<AuthState.SignedOut>(launched.authState.value)
            assertEquals(0, locked.removes, "unreadable reads $unreadableReads")
            assertEquals(0, locked.wipes, "unreadable reads $unreadableReads")
            assertEquals(emptyList(), server.checked)
            assertEquals(record, store.read(SessionEntry))
        }

        // The keystore is back: the next launch is signed in.
        assertEquals(AuthState.SignedIn(session), launchApp().authState.value)
    }

    @Test
    fun storeThatCannotBeReadDoesNotStopTheUserFromSigningInAgain() = runTest {
        signedOutRepository().signInAsHunter()
        val locked = UnreadableStore(store)
        val launched = launchApp(secureStore = locked)

        // The user signs in by hand; the session is written over the one that could not be read.
        assertEquals(SignInOutcome.SignedIn, launched.signIn(Hunter))

        assertEquals(launched.session(), SessionStorage(store).load())
    }

    @Test
    fun launchWhereOnlyTheSessionEntryCannotBeReadKeepsTheAddress() = runTest {
        signedOutRepository().signInAsHunter()
        // The first read of a launch is the session; only that one fails.
        val launched = launchApp(secureStore = OneUnreadableReadStore(store, nth = 1))

        assertEquals(AuthState.SignedOut(lastEmail = HunterAddress), launched.authState.value)
        assertNotNull(store.read(SessionEntry))
    }

    @Test
    fun sessionOfThePreviousFormatSurvivesTheUpdate() = runTest {
        store.writePreviousFormat(SavedSession)

        val launched = launchApp()

        assertEquals(AuthState.SignedIn(SavedSession), launched.authState.value)
        assertEquals("v1\nuser-me\nstudent@hse.ru\nsecret", store.read(SessionEntry))
        for (key in EntriesOfThePreviousFormat) assertNull(store.read(key), key)
        assertEquals(AuthState.SignedIn(SavedSession), launchApp().authState.value)
    }

    @Test
    fun signOutEndsASessionOfThePreviousFormatForGood() = runTest {
        store.writePreviousFormat(SavedSession)
        val launched = launchApp()

        launched.signOut()

        assertEquals(AuthState.SignedOut(lastEmail = SavedSession.email), launchApp().authState.value)
        for (key in EntriesOfThePreviousFormat + SessionEntry) assertNull(store.read(key), key)
    }

    @Test
    fun sessionOfThePreviousFormatThatTheServerRefusesIsErased() = runTest {
        store.writePreviousFormat(SavedSession)

        val launched = launchApp(serverWhere { SessionCheck.Rejected })

        val expired = AuthState.SignedOut(sessionExpired = true, lastEmail = SavedSession.email)
        assertEquals(expired, launched.authState.value)
        for (key in EntriesOfThePreviousFormat + SessionEntry) assertNull(store.read(key), key)
        assertEquals(SavedSession.email, SessionStorage(store).loadLastEmail())
    }

    // ---- a 401 is a question, not a sign-out ----

    @Test
    fun tokenTheServerStillAcceptsKeepsTheSession() = runTest {
        val repository = signedOutRepository()
        val session = repository.signInAsHunter()
        source.onCheck = { SessionCheck.Valid(session.userId) }

        repository.expireSession(session.accessToken)

        assertEquals(AuthState.SignedIn(session), repository.authState.value)
        assertEquals(listOf(session.accessToken), source.checked)
        assertEquals(session, SessionStorage(store).load())
    }

    @Test
    fun failureOfTheQuestionKeepsTheSession() = runTest {
        val repository = signedOutRepository()
        val session = repository.signInAsHunter()

        for (failure in failuresOfTheServer()) {
            source.onCheck = { throw failure }

            repository.expireSession(session.accessToken)

            assertEquals(AuthState.SignedIn(session), repository.authState.value, "failure $failure")
            assertEquals(session, SessionStorage(store).load())
        }
    }

    @Test
    fun noAnswerWithinFiveSecondsKeepsTheSession() = runTest {
        val repository = signedOutRepository()
        val session = repository.signInAsHunter()
        source.onCheck = { answerAfterNeverMillis() }

        val expiring = launch { repository.expireSession(session.accessToken) }
        runCurrent()
        advanceTimeBy(4_999)
        runCurrent()
        assertTrue(expiring.isActive)
        advanceTimeBy(1)
        runCurrent()

        assertFalse(expiring.isActive)
        assertEquals(5_000, currentTime)
        assertEquals(AuthState.SignedIn(session), repository.authState.value)
        assertEquals(session, SessionStorage(store).load())
    }

    @Test
    fun waitForTheAnswerCanBeShortened() = runTest {
        val repository = repository(verifyTimeout = 1.seconds)
        repository.restoreSession()
        val session = repository.signInAsHunter()
        source.onCheck = { answerAfterNeverMillis() }

        repository.expireSession(session.accessToken)

        assertEquals(1_000, currentTime)
        assertEquals(AuthState.SignedIn(session), repository.authState.value)
    }

    @Test
    fun anAnswerInTimeIsUsed() = runTest {
        val repository = signedOutRepository()
        val session = repository.signInAsHunter()
        source.onCheck = {
            delay(4_000)
            SessionCheck.Rejected
        }

        repository.expireSession(session.accessToken)

        assertEquals(4_000, currentTime)
        assertEquals(AuthState.SignedOut(sessionExpired = true, lastEmail = HunterAddress), repository.authState.value)
    }

    @Test
    fun parallelRejectionsAskTheServerOnce() = runTest {
        val repository = signedOutRepository()
        val session = repository.signInAsHunter()
        val answer = CompletableDeferred<SessionCheck>()
        source.onCheck = { answer.await() }

        val rejections = List(5) { launch { repository.expireSession(session.accessToken) } }
        runCurrent()

        // Five calls got a 401 at once; one question is on its way.
        assertEquals(1, source.checked.size)
        assertTrue(rejections.all { it.isActive })

        answer.complete(SessionCheck.Rejected)
        advanceUntilIdle()

        assertEquals(1, source.checked.size)
        assertTrue(rejections.none { it.isActive })
        assertEquals(AuthState.SignedOut(sessionExpired = true, lastEmail = HunterAddress), repository.authState.value)
    }

    @Test
    fun parallelRejectionsShareAnAcceptingAnswer() = runTest {
        val repository = signedOutRepository()
        val session = repository.signInAsHunter()
        val answer = CompletableDeferred<SessionCheck>()
        source.onCheck = { answer.await() }

        val rejections = List(5) { launch { repository.expireSession(session.accessToken) } }
        runCurrent()
        answer.complete(SessionCheck.Valid(session.userId))
        advanceUntilIdle()

        assertEquals(1, source.checked.size)
        assertTrue(rejections.none { it.isActive })
        assertEquals(AuthState.SignedIn(session), repository.authState.value)
    }

    @Test
    fun parallelRejectionsShareAFailureToo() = runTest {
        val repository = signedOutRepository()
        val session = repository.signInAsHunter()
        val answer = CompletableDeferred<SessionCheck>()
        source.onCheck = { answer.await() }

        val rejections = List(5) { launch { repository.expireSession(session.accessToken) } }
        runCurrent()
        answer.completeExceptionally(RemoteFailure.NoConnection())
        advanceUntilIdle()

        assertEquals(1, source.checked.size)
        assertTrue(rejections.none { it.isActive })
        assertEquals(AuthState.SignedIn(session), repository.authState.value)
    }

    @Test
    fun parallelRejectionsAllWaitForTheTimeoutOfTheOneQuestion() = runTest {
        val repository = signedOutRepository()
        val session = repository.signInAsHunter()
        source.onCheck = { answerAfterNeverMillis() }

        val rejections = List(3) { launch { repository.expireSession(session.accessToken) } }
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()

        assertEquals(1, source.checked.size)
        assertTrue(rejections.none { it.isActive })
        assertEquals(5_000, currentTime)
        assertEquals(AuthState.SignedIn(session), repository.authState.value)
    }

    @Test
    fun rejectionsOneAfterAnotherAskAgain() = runTest {
        val repository = signedOutRepository()
        val session = repository.signInAsHunter()
        source.onCheck = { SessionCheck.Valid(session.userId) }

        repository.expireSession(session.accessToken)
        repository.expireSession(session.accessToken)

        assertEquals(2, source.checked.size)
        assertEquals(AuthState.SignedIn(session), repository.authState.value)

        // The server changes its mind: the next 401 finds the token refused.
        source.onCheck = { SessionCheck.Rejected }
        repository.expireSession(session.accessToken)

        assertEquals(3, source.checked.size)
        assertEquals(AuthState.SignedOut(sessionExpired = true, lastEmail = HunterAddress), repository.authState.value)
    }

    @Test
    fun signOutWhileTheQuestionIsOnItsWayWins() = runTest {
        val repository = signedOutRepository()
        val session = repository.signInAsHunter()
        val answer = CompletableDeferred<SessionCheck>()
        source.onCheck = { answer.await() }
        val expiring = launch { repository.expireSession(session.accessToken) }
        runCurrent()

        repository.signOut()
        answer.complete(SessionCheck.Rejected)
        expiring.join()

        // The user went out by hand: it is not an expired session.
        assertEquals(AuthState.SignedOut(lastEmail = HunterAddress), repository.authState.value)
    }

    @Test
    fun newSignInWhileTheQuestionIsOnItsWayIsNotSpoiled() = runTest {
        val repository = signedOutRepository()
        val old = repository.signInAsHunter()
        val answer = CompletableDeferred<SessionCheck>()
        source.onCheck = { answer.await() }
        val expiring = launch { repository.expireSession(old.accessToken) }
        runCurrent()

        repository.signOut()
        val current = repository.signInAsHunter()
        answer.complete(SessionCheck.Rejected)
        expiring.join()

        assertEquals(AuthState.SignedIn(current), repository.authState.value)
        assertEquals(current, SessionStorage(store).load())
    }

    @Test
    fun questionAboutAnOldTokenIsNotMixedUpWithTheQuestionAboutTheNewOne() = runTest {
        val repository = signedOutRepository()
        val old = repository.signInAsHunter()
        val oldAnswer = CompletableDeferred<SessionCheck>()
        val currentAnswer = CompletableDeferred<SessionCheck>()
        source.onCheck = { token -> if (token == old.accessToken) oldAnswer.await() else currentAnswer.await() }
        val oldQuestion = launch { repository.expireSession(old.accessToken) }
        runCurrent()
        // The user goes out and in again while the first question is still on its way.
        repository.signOut()
        val current = repository.signInAsHunter()
        val newQuestion = launch { repository.expireSession(current.accessToken) }
        runCurrent()

        // Two tokens, two questions.
        assertEquals(listOf(old.accessToken, current.accessToken), source.checked)

        oldAnswer.complete(SessionCheck.Rejected)
        oldQuestion.join()
        // The old session is gone already: its late refusal changes nothing, and the new question goes on.
        assertEquals(AuthState.SignedIn(current), repository.authState.value)
        assertTrue(newQuestion.isActive)

        currentAnswer.complete(SessionCheck.Valid(current.userId))
        newQuestion.join()
        assertEquals(AuthState.SignedIn(current), repository.authState.value)
    }

    @Test
    fun theSessionStaysUntilTheUserSignsOutOrTheServerRefuses() = runTest {
        val repository = signedOutRepository()
        val session = repository.signInAsHunter()
        source.onCheck = { SessionCheck.Valid(session.userId) }

        // Nothing but those two things ends it: time, 401s the server does not back, failures.
        time += (1_000 * 60 * 60).seconds
        repeat(10) { repository.expireSession(session.accessToken) }
        source.onCheck = { throw RemoteFailure.Timeout() }
        repeat(10) { repository.expireSession(session.accessToken) }

        assertEquals(AuthState.SignedIn(session), repository.authState.value)
        assertEquals(AuthState.SignedIn(session), launchApp().authState.value)
    }

    // ---- the sign-in is written to the disk ----

    @Test
    fun busyStoreIsTriedAgainAndTheSessionIsSaved() = runTest {
        var failures = 2
        val busy = object : SecureStore by store {
            override fun write(key: String, value: String) {
                if (key == SessionEntry && failures > 0) {
                    failures--
                    error("the keystore is busy")
                }
                store.write(key, value)
            }
        }
        val repository = signedOutRepository(secureStore = busy)

        assertEquals(SignInOutcome.SignedIn, repository.signIn(Hunter))

        assertEquals(repository.session(), SessionStorage(store).load())
        // Two pauses of 100 ms between three tries.
        assertEquals(200, currentTime)
    }

    @Test
    fun storeThatNeverWritesStillSignsInAfterThreeTries() = runTest {
        var writes = 0
        val broken = object : SecureStore by store {
            override fun write(key: String, value: String) {
                if (key == SessionEntry) {
                    writes++
                    error("the disk is full")
                }
                store.write(key, value)
            }
        }
        val repository = signedOutRepository(secureStore = broken)

        assertEquals(SignInOutcome.SignedIn, repository.signIn(Hunter))

        assertEquals(3, writes)
        assertIs<AuthState.SignedIn>(repository.authState.value)
        assertNull(SessionStorage(store).load())
        // The address is written all the same.
        assertEquals(HunterAddress, SessionStorage(store).loadLastEmail())
    }

    // ---- the address for the sign-in screen ----

    @Test
    fun signInSavesTheAddress() = runTest {
        val repository = signedOutRepository()

        repository.signInAsHunter()

        assertEquals(HunterAddress, SessionStorage(store).loadLastEmail())
        assertEquals(HunterAddress, store.read("session.last_email"))
    }

    @Test
    fun registrationSavesTheAddressButDoesNotChangeTheState() = runTest {
        val repository = signedOutRepository()

        repository.register(Hunter)

        assertEquals(HunterAddress, SessionStorage(store).loadLastEmail())
        assertEquals(AuthState.SignedOut(), repository.authState.value)
        // The process is killed before the link is opened: the sign-in screen of the next launch has the address.
        assertEquals(AuthState.SignedOut(lastEmail = HunterAddress), launchApp().authState.value)
    }

    @Test
    fun refusedOrFailedRegistrationSavesNothing() = runTest {
        val repository = signedOutRepository()

        source.onRegister = { RegisterReply.DomainNotAllowed }
        repository.register(Hunter)
        source.onRegister = { RegisterReply.AlreadyRegistered }
        repository.register(Hunter)
        source.onRegister = { throw RemoteFailure.Http(503) }
        runCatching { repository.register(Hunter) }

        assertNull(SessionStorage(store).loadLastEmail())
    }

    @Test
    fun signOutKeepsTheAddressForTheSignInScreen() = runTest {
        val repository = signedOutRepository()
        repository.signInAsHunter()

        repository.signOut()

        assertEquals(AuthState.SignedOut(sessionExpired = false, lastEmail = HunterAddress), repository.authState.value)
        assertEquals(HunterAddress, SessionStorage(store).loadLastEmail())
        assertNull(SessionStorage(store).load())
        // And so does the next launch.
        assertEquals(AuthState.SignedOut(lastEmail = HunterAddress), launchApp().authState.value)
    }

    @Test
    fun endedSessionKeepsTheAddressForTheSignInScreen() = runTest {
        val repository = signedOutRepository()
        val session = repository.signInAsHunter()
        source.onCheck = { SessionCheck.Rejected }

        repository.expireSession(session.accessToken)

        assertEquals(AuthState.SignedOut(sessionExpired = true, lastEmail = HunterAddress), repository.authState.value)
        assertEquals(HunterAddress, SessionStorage(store).loadLastEmail())
    }

    @Test
    fun launchWithoutASessionShowsTheLastAddress() = runTest {
        SessionStorage(store).saveLastEmail("someone@hse.ru")

        val launched = launchApp()

        assertEquals(AuthState.SignedOut(lastEmail = "someone@hse.ru"), launched.authState.value)
    }

    @Test
    fun firstLaunchKnowsNoAddress() = runTest {
        assertEquals(AuthState.SignedOut(sessionExpired = false, lastEmail = null), launchApp().authState.value)
    }

    @Test
    fun signingInAsAnotherAccountReplacesTheAddress() = runTest {
        val repository = signedOutRepository()
        repository.signInAsHunter()
        repository.signOut()
        val other = Credentials("other@hse.ru", Password("another-pass-1"))

        assertEquals(SignInOutcome.SignedIn, repository.signIn(other))
        repository.signOut()

        assertEquals(AuthState.SignedOut(lastEmail = "other@hse.ru"), repository.authState.value)
        assertEquals("other@hse.ru", SessionStorage(store).loadLastEmail())
    }

    @Test
    fun refusedSignInLeavesTheAddressAlone() = runTest {
        val repository = signedOutRepository()
        repository.signInAsHunter()
        repository.signOut()
        source.onSignIn = { SignInReply.Unauthorized }

        repository.signIn(Credentials("other@hse.ru", Password("another-pass-1")))

        assertEquals(HunterAddress, SessionStorage(store).loadLastEmail())
        assertEquals(AuthState.SignedOut(lastEmail = HunterAddress), repository.authState.value)
    }

    @Test
    fun addressThatCannotBeSavedDoesNotStopTheSignIn() = runTest {
        val noAddress = object : SecureStore by store {
            override fun write(key: String, value: String) {
                if (key == "session.last_email") error("cannot write")
                store.write(key, value)
            }
        }
        val repository = signedOutRepository(secureStore = noAddress)

        assertEquals(SignInOutcome.SignedIn, repository.signIn(Hunter))

        assertEquals(repository.session(), SessionStorage(store).load())
        assertNull(SessionStorage(store).loadLastEmail())
    }

    @Test
    fun sessionOfAnEarlierVersionWithoutAnAddressLeavesItWhenItEnds() = runTest {
        store.writePreviousFormat(SavedSession)
        val launched = launchApp()
        assertNull(SessionStorage(store).loadLastEmail())

        launched.signOut()

        assertEquals(AuthState.SignedOut(lastEmail = SavedSession.email), launched.authState.value)
        assertEquals(SavedSession.email, SessionStorage(store).loadLastEmail())
    }

    // ---- the password is never saved ----

    @Test
    fun passwordIsNeverWrittenToTheStore() = runTest {
        val recording = RecordingStore(store)
        val repository = signedOutRepository(secureStore = recording)

        // Every way a password travels through the repository.
        repository.register(Hunter)
        repository.resendLetter(HunterAddress)
        repository.confirmEmail(HunterAddress, "abc")
        repository.signOut()
        repository.register(Hunter)
        repository.signInPending(HunterAddress)
        repository.signOut()
        repository.signIn(Hunter)
        val session = repository.session()
        source.onCheck = { SessionCheck.Rejected }
        repository.expireSession(session.accessToken)

        assertTrue(recording.writes.isNotEmpty())
        for ((key, value) in recording.writes) {
            assertFalse(HunterPassword in key, key)
            assertFalse(HunterPassword in value, "value of $key")
        }
        // Only the session and the address are ever written.
        assertEquals(setOf(SessionEntry, "session.last_email"), recording.writes.map { it.first }.toSet())
        for (key in EntriesOfThePreviousFormat + SessionEntry + "session.last_email") {
            store.read(key)?.let { assertFalse(HunterPassword in it, key) }
        }
    }

    @Test
    fun passwordIsNotInTheSavedSessionAtAll() = runTest {
        val repository = signedOutRepository()
        repository.signInAsHunter()

        assertEquals("v1\nuser-me\nstudent@hse.ru\ntoken-1", store.read(SessionEntry))
    }

    @Test
    fun passwordIsNotInTheTextOfTheStatesOfAnyLaunch() = runTest {
        val repository = signedOutRepository()
        val states = mutableListOf<AuthState>()
        states += repository.authState.value
        repository.register(Hunter)
        states += repository.authState.value
        repository.signInPending(HunterAddress)
        states += repository.authState.value
        states += launchApp().authState.value
        repository.signOut()
        states += repository.authState.value

        for (state in states) {
            assertFalse(HunterPassword in state.toString(), state.toString())
            assertFalse(HunterAddress in state.toString(), state.toString())
        }
    }

    @Test
    fun laterLaunchKnowsNothingOfTheRegistrationsOfAnEarlierOne() = runTest {
        val first = signedOutRepository()
        first.register(Hunter)

        // The password lived in the memory of the first process only.
        val second = launchApp()

        assertNull(second.pendingRegistration(HunterAddress))
        assertNull(second.signInPending(HunterAddress))
        assertNull(second.resendLetter(HunterAddress))
    }
}
