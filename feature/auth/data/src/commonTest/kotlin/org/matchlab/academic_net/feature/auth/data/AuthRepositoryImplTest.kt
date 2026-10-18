package org.matchlab.academic_net.feature.auth.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.feature.auth.domain.AccessToken
import org.matchlab.academic_net.feature.auth.domain.AuthState
import org.matchlab.academic_net.feature.auth.domain.ConfirmationOutcome
import org.matchlab.academic_net.feature.auth.domain.Credentials
import org.matchlab.academic_net.feature.auth.domain.Password
import org.matchlab.academic_net.feature.auth.domain.PendingRegistration
import org.matchlab.academic_net.feature.auth.domain.RegistrationOutcome
import org.matchlab.academic_net.feature.auth.domain.Session
import org.matchlab.academic_net.feature.auth.domain.SignInOutcome
import org.matchlab.academic_net.securestore.SecureStore

/** The signed-out state after the user of [HunterAddress] was in. */
private val OutAfterHunter = AuthState.SignedOut(lastEmail = HunterAddress)

/** The signed-out state after the server refused the session of [SavedSession]. */
private val ExpiredAfterSaved = AuthState.SignedOut(sessionExpired = true, lastEmail = SavedSession.email)

/** The entries one [SessionStorage.clear] removes: the session and the three of the previous format. */
private const val EntriesOfASession = 4

@OptIn(ExperimentalCoroutinesApi::class)
class AuthRepositoryImplTest : AuthRepositoryTestBase() {
    // ---- restoring the session ----

    @Test
    fun startsRestoring() = runTest {
        assertEquals(AuthState.Restoring, repository().authState.value)
    }

    @Test
    fun emptyStoreRestoresToSignedOutWithoutAskingTheServer() = runTest {
        val repository = repository()

        repository.restoreSession()

        assertEquals(AuthState.SignedOut(), repository.authState.value)
        assertEquals(emptyList(), source.checked)
    }

    @Test
    fun savedSessionRestoresToSignedInAndTheServerConfirmsIt() = runTest {
        SessionStorage(store).save(SavedSession)
        val repository = repository()

        repository.restoreSession()

        assertEquals(AuthState.SignedIn(SavedSession), repository.authState.value)
        assertEquals(listOf(SavedSession.accessToken), source.checked)
        assertEquals(SavedSession, SessionStorage(store).load())
    }

    @Test
    fun savedSessionIsSignedInWithoutWaitingForTheServer() = runTest {
        SessionStorage(store).save(SavedSession)
        val answer = CompletableDeferred<SessionCheck>()
        source.onCheck = { answer.await() }
        val repository = repository()

        val restoring = launch { repository.restoreSession() }
        runCurrent()

        // The server has not answered yet, and the user is in.
        assertEquals(AuthState.SignedIn(SavedSession), repository.authState.value)
        assertTrue(restoring.isActive)
        assertEquals(listOf(SavedSession.accessToken), source.checked)

        answer.complete(SessionCheck.Valid(SavedSession.userId))
        restoring.join()
        assertEquals(AuthState.SignedIn(SavedSession), repository.authState.value)
    }

    @Test
    fun rejectionThatComesLateEndsTheSessionThatWasAlreadySignedIn() = runTest {
        SessionStorage(store).save(SavedSession)
        val answer = CompletableDeferred<SessionCheck>()
        source.onCheck = { answer.await() }
        val repository = repository()
        val restoring = launch { repository.restoreSession() }
        runCurrent()
        assertEquals(AuthState.SignedIn(SavedSession), repository.authState.value)

        answer.complete(SessionCheck.Rejected)
        restoring.join()

        assertEquals(ExpiredAfterSaved, repository.authState.value)
    }

    @Test
    fun sessionTheServerRejectsIsExpiredAndErased() = runTest {
        SessionStorage(store).save(SavedSession)
        source.onCheck = { SessionCheck.Rejected }
        val repository = repository()

        repository.restoreSession()

        assertEquals(ExpiredAfterSaved, repository.authState.value)
        assertNull(SessionStorage(store).load())
        assertNull(store.read("session.v1"))
        // The token is dead, so there is nothing to revoke; and the server was asked once.
        assertEquals(emptyList(), source.revoked)
        assertEquals(1, source.checked.size)
    }

    @Test
    fun sessionOfAnotherUserIsExpiredAndErased() = runTest {
        SessionStorage(store).save(SavedSession)
        source.onCheck = { SessionCheck.Valid(UserId("somebody-else")) }
        val repository = repository()

        repository.restoreSession()

        assertEquals(ExpiredAfterSaved, repository.authState.value)
        assertNull(SessionStorage(store).load())
    }

    @Test
    fun aServerThatDoesNotAnswerLeavesTheSessionAlone() = runTest {
        SessionStorage(store).save(SavedSession)
        val failures = listOf(
            RemoteFailure.NoConnection(),
            RemoteFailure.Timeout(),
            RemoteFailure.Http(404),
            RemoteFailure.Http(500),
            RemoteFailure.Http(503),
            RemoteFailure.Malformed(),
            IllegalStateException("a bug in the data source"),
        )
        for (failure in failures) {
            source.onCheck = { throw failure }
            val repository = repository()

            repository.restoreSession()

            assertEquals(AuthState.SignedIn(SavedSession), repository.authState.value, "failure $failure")
            assertEquals(SavedSession, SessionStorage(store).load())
        }
    }

    @Test
    fun restoreCancelledWhileCheckingKeepsTheSession() = runTest {
        SessionStorage(store).save(SavedSession)
        source.onCheck = { awaitCancellation() }
        val repository = repository()
        val restoring = launch { repository.restoreSession() }
        runCurrent()

        restoring.cancel()
        restoring.join()

        assertTrue(restoring.isCancelled)
        assertEquals(AuthState.SignedIn(SavedSession), repository.authState.value)
        assertEquals(SavedSession, SessionStorage(store).load())
    }

    @Test
    fun restoreOnlyRunsOnce() = runTest {
        SessionStorage(store).save(SavedSession)
        val repository = repository()
        repository.restoreSession()
        assertEquals(1, source.checked.size)

        // A second call neither reads the store nor asks the server again.
        repository.restoreSession()
        assertEquals(1, source.checked.size)

        repository.signOut()
        repository.restoreSession()
        assertEquals(AuthState.SignedOut(lastEmail = SavedSession.email), repository.authState.value)
        assertEquals(1, source.checked.size)
    }

    @Test
    fun partialRecordOfThePreviousFormatRestoresToSignedOutAndIsLeftAlone() = runTest {
        // Nothing tells a record cut by a crash from one that cannot be read now: it is not erased.
        store.write("session.user_id", "user-me")
        val repository = repository()

        repository.restoreSession()

        assertEquals(AuthState.SignedOut(), repository.authState.value)
        assertEquals("user-me", store.read("session.user_id"))
        assertEquals(emptyList(), source.checked)
    }

    @Test
    fun brokenRecordRestoresToSignedOutAndIsErased() = runTest {
        store.write("session.v1", "v2\nuser-me\nstudent@hse.ru\nsecret")
        val repository = repository()

        repository.restoreSession()

        assertEquals(AuthState.SignedOut(), repository.authState.value)
        assertNull(store.read("session.v1"))
        assertEquals(emptyList(), source.checked)
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

        assertEquals(AuthState.SignedOut(), repository.authState.value)
    }

    @Test
    fun signInThatFinishesWhileRestoringWinsOverTheSavedSession() = runTest {
        SessionStorage(store).save(SavedSession)
        val io = HoldFirstDispatcher(StandardTestDispatcher(testScheduler))
        val repository = repository(io = io)
        // The restore is waiting for the store: its first block is held back.
        val restoring = launch { repository.restoreSession() }
        runCurrent()
        assertEquals(AuthState.Restoring, repository.authState.value)

        val session = repository.signInAsHunter()
        io.release()
        restoring.join()

        assertEquals(AuthState.SignedIn(session), repository.authState.value)
        // The restored session was not the session of this process, so it is not checked.
        assertEquals(emptyList(), source.checked)
    }

    // ---- registration ----

    @Test
    fun registrationSendsTheCredentialsAndRemembersThem() = runTest {
        val repository = signedOutRepository()

        val outcome = repository.register(Hunter)

        assertEquals(
            RegistrationOutcome.LetterSent(PendingRegistration(HunterAddress, 60.seconds, hasPassword = true)),
            outcome,
        )
        assertEquals(listOf(Hunter), source.registered)
        assertEquals(
            PendingRegistration(HunterAddress, 60.seconds, hasPassword = true),
            repository.pendingRegistration(HunterAddress),
        )
        // Registering is not signing in: there is no account until the link is opened.
        assertEquals(AuthState.SignedOut(), repository.authState.value)
    }

    @Test
    fun nothingIsRememberedUntilTheLetterIsSent() = runTest {
        val repository = signedOutRepository()

        assertNull(repository.pendingRegistration(HunterAddress))
    }

    @Test
    fun pauseBeforeTheNextLetterCountsDownWithTime() = runTest {
        val repository = signedOutRepository()
        repository.register(Hunter)

        time += 45.seconds
        assertEquals(15.seconds, repository.pendingRegistration(HunterAddress)?.resendAfter)

        time += 15.seconds
        assertEquals(Duration.ZERO, repository.pendingRegistration(HunterAddress)?.resendAfter)

        time += 100.seconds
        assertEquals(Duration.ZERO, repository.pendingRegistration(HunterAddress)?.resendAfter)
        assertTrue(repository.pendingRegistration(HunterAddress)?.hasPassword == true)
    }

    @Test
    fun pauseIsSixtySecondsUnlessTheRepositoryIsToldOtherwise() = runTest {
        val repository = signedOutRepository()
        val short = repository(resendInterval = 10.seconds)
        short.restoreSession()

        val standard = assertIs<RegistrationOutcome.LetterSent>(repository.register(Hunter))
        val custom = assertIs<RegistrationOutcome.LetterSent>(short.register(Hunter))

        assertEquals(60.seconds, standard.pending.resendAfter)
        assertEquals(10.seconds, custom.pending.resendAfter)
        time += 4.seconds
        assertEquals(6.seconds, short.pendingRegistration(HunterAddress)?.resendAfter)
    }

    @Test
    fun registrationsAreRememberedPerAddress() = runTest {
        val repository = signedOutRepository()
        repository.register(Hunter)

        time += 20.seconds
        repository.register(Credentials("other@hse.ru", Password("another-pass-1")))

        assertEquals(40.seconds, repository.pendingRegistration(HunterAddress)?.resendAfter)
        assertEquals(60.seconds, repository.pendingRegistration("other@hse.ru")?.resendAfter)
        assertNull(repository.pendingRegistration("third@hse.ru"))
    }

    @Test
    fun registeringAgainReplacesThePasswordAndRestartsThePause() = runTest {
        val repository = signedOutRepository()
        repository.register(Hunter)
        time += 30.seconds

        repository.register(Credentials(HunterAddress, Password("second-password")))

        assertEquals(60.seconds, repository.pendingRegistration(HunterAddress)?.resendAfter)
        // The password that signs in is the new one.
        repository.signInPending(HunterAddress)
        assertEquals(Password("second-password"), source.signedIn.single().password)
    }

    @Test
    fun refusedRegistrationsAreNotRemembered() = runTest {
        val repository = signedOutRepository()

        source.onRegister = { RegisterReply.DomainNotAllowed }
        assertEquals(RegistrationOutcome.DomainNotAllowed, repository.register(Hunter))
        source.onRegister = { RegisterReply.AlreadyRegistered }
        assertEquals(RegistrationOutcome.AlreadyRegistered, repository.register(Hunter))

        assertNull(repository.pendingRegistration(HunterAddress))
        assertNull(repository.resendLetter(HunterAddress))
    }

    @Test
    fun refusedRegistrationKeepsTheEarlierOne() = runTest {
        val repository = signedOutRepository()
        repository.register(Hunter)
        time += 10.seconds

        // The address was confirmed from the letter meanwhile: the server says it has an account.
        source.onRegister = { RegisterReply.AlreadyRegistered }
        val again = Credentials(HunterAddress, Password("another-pass-1"))
        assertEquals(RegistrationOutcome.AlreadyRegistered, repository.register(again))

        assertEquals(50.seconds, repository.pendingRegistration(HunterAddress)?.resendAfter)
        repository.signInPending(HunterAddress)
        assertEquals(Hunter.password, source.signedIn.single().password)
    }

    @Test
    fun failedRegistrationIsThrownAndNotRemembered() = runTest {
        val repository = signedOutRepository()
        val failure = RemoteFailure.NoConnection()
        source.onRegister = { throw failure }

        assertEquals(failure, assertFailsWith<RemoteFailure.NoConnection> { repository.register(Hunter) })

        assertNull(repository.pendingRegistration(HunterAddress))
    }

    @Test
    fun registrationIsRememberedEvenIfTheCallerIsCancelledAsTheServerAnswers() = runTest {
        val repository = signedOutRepository()
        lateinit var caller: Job
        source.onRegister = {
            caller.cancel()
            RegisterReply.LetterSent
        }

        caller = launch { repository.register(Hunter) }
        caller.join()

        assertEquals(HunterAddress, repository.pendingRegistration(HunterAddress)?.email)
        // The address for the next launch is saved as well, not only the registration in memory.
        assertEquals(HunterAddress, SessionStorage(store).loadLastEmail())
    }

    // ---- sending the letter again ----

    @Test
    fun resendWithoutAPasswordIsNullAndAsksNobody() = runTest {
        val repository = signedOutRepository()

        assertNull(repository.resendLetter(HunterAddress))

        assertEquals(emptyList(), source.registered)
    }

    @Test
    fun resendRegistersAgainWithTheRememberedPassword() = runTest {
        val repository = signedOutRepository()
        repository.register(Hunter)
        time += 61.seconds

        val outcome = repository.resendLetter(HunterAddress)

        assertEquals(
            RegistrationOutcome.LetterSent(PendingRegistration(HunterAddress, 60.seconds, hasPassword = true)),
            outcome,
        )
        assertEquals(listOf(Hunter, Hunter), source.registered)
        assertEquals(60.seconds, repository.pendingRegistration(HunterAddress)?.resendAfter)
    }

    @Test
    fun resendOfAnAddressConfirmedMeanwhileIsAlreadyRegisteredAndKeepsThePassword() = runTest {
        val repository = signedOutRepository()
        repository.register(Hunter)
        source.onRegister = { RegisterReply.AlreadyRegistered }

        assertEquals(RegistrationOutcome.AlreadyRegistered, repository.resendLetter(HunterAddress))

        // "I've confirmed" can now sign in with the remembered password.
        source.onRegister = { RegisterReply.LetterSent }
        assertEquals(SignInOutcome.SignedIn, repository.signInPending(HunterAddress))
        assertTrue(repository.authState.value is AuthState.SignedIn)
    }

    @Test
    fun resendRefusedByTheDomainIsDomainNotAllowed() = runTest {
        val repository = signedOutRepository()
        repository.register(Hunter)
        source.onRegister = { RegisterReply.DomainNotAllowed }

        assertEquals(RegistrationOutcome.DomainNotAllowed, repository.resendLetter(HunterAddress))
    }

    @Test
    fun failedResendIsThrownAndKeepsTheRegistration() = runTest {
        val repository = signedOutRepository()
        repository.register(Hunter)
        time += 10.seconds
        val failure = RemoteFailure.Http(503)
        source.onRegister = { throw failure }

        assertEquals(failure, assertFailsWith<RemoteFailure.Http> { repository.resendLetter(HunterAddress) })

        assertEquals(50.seconds, repository.pendingRegistration(HunterAddress)?.resendAfter)
    }

    // ---- confirming the address ----

    @Test
    fun confirmationSignsInWithTheRememberedPassword() = runTest {
        val repository = signedOutRepository()
        repository.register(Hunter)

        val outcome = repository.confirmEmail(HunterAddress, "https://matchlab.space/confirm?token=abc")

        assertEquals(ConfirmationOutcome.Confirmed(signedIn = true), outcome)
        assertEquals(listOf("https://matchlab.space/confirm?token=abc"), source.confirmed)
        assertEquals(listOf(Hunter), source.signedIn)
        val session = repository.session()
        assertEquals(HunterAddress, session.email)
        assertEquals(session, SessionStorage(store).load())
        // The password is gone with the registration.
        assertNull(repository.pendingRegistration(HunterAddress))
        assertNull(repository.resendLetter(HunterAddress))
    }

    @Test
    fun alreadyConfirmedAddressSignsInToo() = runTest {
        val repository = signedOutRepository()
        repository.register(Hunter)
        source.onConfirm = { ConfirmReply.AlreadyConfirmed }

        assertEquals(ConfirmationOutcome.Confirmed(signedIn = true), repository.confirmEmail(HunterAddress, "abc"))

        assertIs<AuthState.SignedIn>(repository.authState.value)
    }

    @Test
    fun confirmationWithoutAPasswordDoesNotSignIn() = runTest {
        val repository = signedOutRepository()

        assertEquals(ConfirmationOutcome.Confirmed(signedIn = false), repository.confirmEmail(HunterAddress, "abc"))

        assertEquals(emptyList(), source.signedIn)
        assertEquals(AuthState.SignedOut(), repository.authState.value)
    }

    @Test
    fun alreadyConfirmedAddressWithoutAPasswordIsConfirmedNotSignedIn() = runTest {
        val repository = signedOutRepository()
        source.onConfirm = { ConfirmReply.AlreadyConfirmed }

        assertEquals(ConfirmationOutcome.Confirmed(signedIn = false), repository.confirmEmail(HunterAddress, "abc"))

        assertEquals(emptyList(), source.signedIn)
    }

    @Test
    fun confirmationSignsInWithThePasswordOfThatAddressOnly() = runTest {
        val repository = signedOutRepository()
        repository.register(Hunter)

        val outcome = repository.confirmEmail("other@hse.ru", "abc")

        assertEquals(ConfirmationOutcome.Confirmed(signedIn = false), outcome)
        assertEquals(emptyList(), source.signedIn)
        assertEquals(AuthState.SignedOut(), repository.authState.value)
    }

    @Test
    fun linkProblemsAreOutcomesAndDoNotSignIn() = runTest {
        val repository = signedOutRepository()
        repository.register(Hunter)

        source.onConfirm = { ConfirmReply.InvalidLink }
        assertEquals(ConfirmationOutcome.InvalidLink, repository.confirmEmail(HunterAddress, "abc"))
        source.onConfirm = { ConfirmReply.Expired }
        assertEquals(ConfirmationOutcome.LinkExpired, repository.confirmEmail(HunterAddress, "abc"))
        source.onConfirm = { ConfirmReply.DomainNotAllowed }
        assertEquals(ConfirmationOutcome.DomainNotAllowed, repository.confirmEmail(HunterAddress, "abc"))

        assertEquals(emptyList(), source.signedIn)
        assertEquals(AuthState.SignedOut(), repository.authState.value)
        // The registration is still there, for the next try with the right link.
        assertEquals(HunterAddress, repository.pendingRegistration(HunterAddress)?.email)
    }

    @Test
    fun failedConfirmationIsThrownAndDoesNotSignIn() = runTest {
        val repository = signedOutRepository()
        repository.register(Hunter)
        val failure = RemoteFailure.Timeout()
        source.onConfirm = { throw failure }

        assertEquals(failure, assertFailsWith<RemoteFailure.Timeout> { repository.confirmEmail(HunterAddress, "abc") })

        assertEquals(emptyList(), source.signedIn)
    }

    @Test
    fun refusedSignInAfterConfirmationDoesNotUndoTheConfirmation() = runTest {
        val repository = signedOutRepository()
        repository.register(Hunter)

        for (reply in listOf(SignInReply.Unauthorized, SignInReply.Forbidden)) {
            source.onSignIn = { reply }

            val outcome = repository.confirmEmail(HunterAddress, "abc")

            assertEquals(ConfirmationOutcome.Confirmed(signedIn = false), outcome)
        }

        assertEquals(AuthState.SignedOut(), repository.authState.value)
        // The user can still sign in by "I've confirmed": the password is remembered.
        assertEquals(HunterAddress, repository.pendingRegistration(HunterAddress)?.email)
    }

    @Test
    fun failedSignInAfterConfirmationDoesNotUndoTheConfirmationEither() = runTest {
        val repository = signedOutRepository()
        repository.register(Hunter)
        source.onSignIn = { throw RemoteFailure.NoConnection() }

        assertEquals(ConfirmationOutcome.Confirmed(signedIn = false), repository.confirmEmail(HunterAddress, "abc"))

        assertEquals(AuthState.SignedOut(), repository.authState.value)
        source.onSignIn = { SignInReply.Success(SavedSession) }
        assertEquals(SignInOutcome.SignedIn, repository.signInPending(HunterAddress))
    }

    @Test
    fun cancellationDuringTheSignInAfterConfirmationIsNotSwallowed() = runTest {
        val repository = signedOutRepository()
        repository.register(Hunter)
        source.onSignIn = { awaitCancellation() }
        var failure: Throwable? = null
        val confirming = launch {
            try {
                repository.confirmEmail(HunterAddress, "abc")
            } catch (cancelled: CancellationException) {
                failure = cancelled
                throw cancelled
            }
        }
        runCurrent()

        confirming.cancel()
        confirming.join()

        assertIs<CancellationException>(failure)
        assertEquals(AuthState.SignedOut(), repository.authState.value)
    }

    // ---- signing in ----

    @Test
    fun signInSavesTheSessionAndSignsIn() = runTest {
        val repository = signedOutRepository()

        val outcome = repository.signIn(Hunter)

        assertEquals(SignInOutcome.SignedIn, outcome)
        assertEquals(listOf(Hunter), source.signedIn)
        val session = repository.session()
        assertEquals(Session(UserId("user-me"), HunterAddress, AccessToken("token-1")), session)
        assertEquals(session, SessionStorage(store).load())
    }

    @Test
    fun signedInSessionIsRestoredByAnotherRepositoryOnTheSameStore() = runTest {
        val first = signedOutRepository()
        val session = first.signInAsHunter()

        val second = repository()
        second.restoreSession()

        assertEquals(AuthState.SignedIn(session), second.authState.value)
    }

    @Test
    fun wrongCredentialsKeepTheUserSignedOut() = runTest {
        val repository = signedOutRepository()
        source.onSignIn = { SignInReply.Unauthorized }

        assertEquals(SignInOutcome.WrongCredentials, repository.signIn(Hunter))

        assertEquals(AuthState.SignedOut(), repository.authState.value)
        assertNull(SessionStorage(store).load())
    }

    @Test
    fun forbiddenAccountKeepsTheUserSignedOut() = runTest {
        val repository = signedOutRepository()
        source.onSignIn = { SignInReply.Forbidden }

        assertEquals(SignInOutcome.AccountUnavailable, repository.signIn(Hunter))

        assertEquals(AuthState.SignedOut(), repository.authState.value)
        assertNull(SessionStorage(store).load())
    }

    @Test
    fun failedSignInIsThrownAndChangesNothing() = runTest {
        val repository = signedOutRepository()
        val failure = RemoteFailure.Http(503)
        source.onSignIn = { throw failure }

        assertEquals(failure, assertFailsWith<RemoteFailure.Http> { repository.signIn(Hunter) })

        assertEquals(AuthState.SignedOut(), repository.authState.value)
        assertNull(SessionStorage(store).load())
    }

    @Test
    fun signInIsCompletedEvenIfTheCallerIsCancelledAsTheServerAnswers() = runTest {
        val repository = signedOutRepository()
        val session = Session(UserId("user-me"), HunterAddress, AccessToken("token-late"))
        lateinit var caller: Job
        source.onSignIn = {
            // The caller goes away at the very moment the answer arrives.
            caller.cancel()
            SignInReply.Success(session)
        }

        caller = launch { repository.signIn(Hunter) }
        caller.join()

        assertEquals(AuthState.SignedIn(session), repository.authState.value)
        assertEquals(session, SessionStorage(store).load())
    }

    @Test
    fun failedWriteStillSignsInForTheRestOfTheProcess() = runTest {
        val readOnly = object : SecureStore by store {
            override fun write(key: String, value: String) = error("disk is full")
        }
        val repository = signedOutRepository(secureStore = readOnly)

        assertEquals(SignInOutcome.SignedIn, repository.signIn(Hunter))

        assertIs<AuthState.SignedIn>(repository.authState.value)
        assertNull(SessionStorage(store).load())
    }

    @Test
    fun signInForgetsTheRegistrations() = runTest {
        val repository = signedOutRepository()
        repository.register(Hunter)
        repository.register(Credentials("other@hse.ru", Password("another-pass-1")))

        // Signing in as somebody else ends every registration that was waiting.
        repository.signIn(Credentials("third@hse.ru", Password("third-pass-123")))

        assertNull(repository.pendingRegistration(HunterAddress))
        assertNull(repository.pendingRegistration("other@hse.ru"))
        assertNull(repository.resendLetter(HunterAddress))
        assertNull(repository.signInPending("other@hse.ru"))
    }

    @Test
    fun refusedSignInKeepsTheRegistrations() = runTest {
        val repository = signedOutRepository()
        repository.register(Hunter)
        source.onSignIn = { SignInReply.Unauthorized }

        repository.signIn(Credentials("third@hse.ru", Password("third-pass-123")))

        assertEquals(HunterAddress, repository.pendingRegistration(HunterAddress)?.email)
    }

    // ---- "I've confirmed" ----

    @Test
    fun signInPendingWithoutAPasswordIsNullAndAsksNobody() = runTest {
        val repository = signedOutRepository()

        assertNull(repository.signInPending(HunterAddress))

        assertEquals(emptyList(), source.signedIn)
    }

    @Test
    fun signInPendingSignsInWithTheRememberedPassword() = runTest {
        val repository = signedOutRepository()
        repository.register(Hunter)

        assertEquals(SignInOutcome.SignedIn, repository.signInPending(HunterAddress))

        assertEquals(listOf(Hunter), source.signedIn)
        assertIs<AuthState.SignedIn>(repository.authState.value)
        assertNull(repository.pendingRegistration(HunterAddress))
    }

    @Test
    fun signInPendingBeforeTheLinkWasOpenedIsNotConfirmedYet() = runTest {
        val repository = signedOutRepository()
        repository.register(Hunter)
        source.onSignIn = { SignInReply.Unauthorized }

        assertEquals(SignInOutcome.NotConfirmedYet, repository.signInPending(HunterAddress))

        assertEquals(AuthState.SignedOut(), repository.authState.value)
        // The password stays for the next try, once the link is opened.
        assertEquals(HunterAddress, repository.pendingRegistration(HunterAddress)?.email)
        source.onSignIn = { SignInReply.Success(SavedSession) }
        assertEquals(SignInOutcome.SignedIn, repository.signInPending(HunterAddress))
    }

    @Test
    fun signInPendingOfAnUnavailableAccountIsAccountUnavailable() = runTest {
        val repository = signedOutRepository()
        repository.register(Hunter)
        source.onSignIn = { SignInReply.Forbidden }

        assertEquals(SignInOutcome.AccountUnavailable, repository.signInPending(HunterAddress))
    }

    @Test
    fun failedSignInPendingIsThrown() = runTest {
        val repository = signedOutRepository()
        repository.register(Hunter)
        val failure = RemoteFailure.NoConnection()
        source.onSignIn = { throw failure }

        assertEquals(failure, assertFailsWith<RemoteFailure.NoConnection> { repository.signInPending(HunterAddress) })
        assertEquals(HunterAddress, repository.pendingRegistration(HunterAddress)?.email)
    }

    // ---- the server stops accepting the session ----

    @Test
    fun refusedTokenOfTheCurrentSessionEndsIt() = runTest {
        val repository = signedOutRepository()
        val session = repository.signInAsHunter()
        source.onCheck = { SessionCheck.Rejected }

        repository.expireSession(session.accessToken)

        assertEquals(AuthState.SignedOut(sessionExpired = true, lastEmail = HunterAddress), repository.authState.value)
        assertEquals(listOf(session.accessToken), source.checked)
        assertNull(SessionStorage(store).load())
        assertNull(store.read("session.v1"))
        // The server said that the token is dead: it is not asked to revoke it.
        assertEquals(emptyList(), source.revoked)
    }

    @Test
    fun refusedTokenOfAnotherSessionChangesNothingAndAsksNobody() = runTest {
        val repository = signedOutRepository()
        val session = repository.signInAsHunter()
        source.onCheck = { SessionCheck.Rejected }

        repository.expireSession(AccessToken("token-of-an-old-session"))

        assertEquals(AuthState.SignedIn(session), repository.authState.value)
        assertEquals(session, SessionStorage(store).load())
        assertEquals(emptyList(), source.checked)
    }

    @Test
    fun lateRejectionOfAnOldSessionDoesNotEndTheNewOne() = runTest {
        val repository = signedOutRepository()
        val old = repository.signInAsHunter()
        repository.signOut()
        val current = repository.signInAsHunter()
        source.onCheck = { SessionCheck.Rejected }

        repository.expireSession(old.accessToken)

        assertEquals(AuthState.SignedIn(current), repository.authState.value)
        assertEquals(current, SessionStorage(store).load())
        assertEquals(emptyList(), source.checked)
    }

    @Test
    fun rejectionWhileSignedOutChangesNothingAndAsksNobody() = runTest {
        val repository = signedOutRepository()

        repository.expireSession(SavedSession.accessToken)

        // Not "session expired": nobody was signed in.
        assertEquals(AuthState.SignedOut(), repository.authState.value)
        assertEquals(emptyList(), source.checked)
    }

    @Test
    fun rejectionWhileRestoringChangesNothingAndAsksNobody() = runTest {
        val repository = repository()

        repository.expireSession(SavedSession.accessToken)

        assertEquals(AuthState.Restoring, repository.authState.value)
        assertEquals(emptyList(), source.checked)
    }

    @Test
    fun rejectionAfterAnEndedSessionChangesNothingAndAsksNobody() = runTest {
        val repository = signedOutRepository()
        val session = repository.signInAsHunter()
        source.onCheck = { SessionCheck.Rejected }
        repository.expireSession(session.accessToken)

        repository.expireSession(session.accessToken)

        assertEquals(AuthState.SignedOut(sessionExpired = true, lastEmail = HunterAddress), repository.authState.value)
        assertEquals(1, source.checked.size)
    }

    @Test
    fun tokenThatTheServerNamesForAnotherUserEndsTheSession() = runTest {
        val repository = signedOutRepository()
        val session = repository.signInAsHunter()
        source.onCheck = { SessionCheck.Valid(UserId("somebody-else")) }

        repository.expireSession(session.accessToken)

        assertEquals(AuthState.SignedOut(sessionExpired = true, lastEmail = HunterAddress), repository.authState.value)
    }

    @Test
    fun endingTheSessionForgetsTheRegistrations() = runTest {
        val repository = signedOutRepository()
        val session = repository.signInAsHunter()
        // A late answer to a registration reaches the repository after the sign-in.
        repository.register(Credentials("other@hse.ru", Password("another-pass-1")))
        assertEquals("other@hse.ru", repository.pendingRegistration("other@hse.ru")?.email)
        source.onCheck = { SessionCheck.Rejected }

        repository.expireSession(session.accessToken)

        assertNull(repository.pendingRegistration("other@hse.ru"))
    }

    @Test
    fun endingTheSessionRetriesTheEraseAndWipesTheStoreLikeASignOut() = runTest {
        SessionStorage(store).save(SavedSession)
        val flaky = FlakyStore(store)
        val repository = repository(secureStore = flaky)
        source.onCheck = { SessionCheck.Rejected }
        repository.restoreSession()

        // The restore found the refusal and ended the session: three attempts, then one wipe.
        assertEquals(3 * EntriesOfASession, flaky.removes)
        assertEquals(1, flaky.wipes)
        assertEquals(ExpiredAfterSaved, repository.authState.value)
        assertNull(SessionStorage(store).load())
    }

    @Test
    fun endingTheSessionIsCompletedEvenIfTheCallerIsCancelled() = runTest {
        SessionStorage(store).save(SavedSession)
        lateinit var caller: Job
        var armed = false
        val cancelling = object : SecureStore by store {
            override fun remove(key: String) {
                if (armed) caller.cancel()
                store.remove(key)
            }
        }
        val repository = signedOutRepository(secureStore = cancelling)
        source.onCheck = { SessionCheck.Rejected }
        armed = true

        caller = launch { repository.expireSession(SavedSession.accessToken) }
        caller.join()

        assertEquals(ExpiredAfterSaved, repository.authState.value)
        assertNull(SessionStorage(store).load())
    }

    @Test
    fun parallelRefusalsEraseTheSessionOnce() = runTest {
        SessionStorage(store).save(SavedSession)
        val counting = FlakyStore(store, failingRemoves = 0)
        // A dispatcher that really switches, so that the calls interleave.
        val repository = repository(secureStore = counting, io = StandardTestDispatcher(testScheduler))
        val restoring = launch { repository.restoreSession() }
        advanceUntilIdle()
        restoring.join()
        source.onCheck = { SessionCheck.Rejected }

        val rejections = List(3) { launch { repository.expireSession(SavedSession.accessToken) } }
        advanceUntilIdle()
        rejections.forEach { it.join() }

        // One erase of every entry, not three.
        assertEquals(EntriesOfASession, counting.removes)
        assertEquals(ExpiredAfterSaved, repository.authState.value)
    }

    @Test
    fun lateRefusalDuringANewSignInDoesNotSpoilTheNewSession() = runTest {
        val repository = repository(io = StandardTestDispatcher(testScheduler))
        val restoring = launch { repository.restoreSession() }
        advanceUntilIdle()
        restoring.join()
        val signingIn = launch { repository.signIn(Hunter) }
        advanceUntilIdle()
        signingIn.join()
        val old = repository.session()
        repository.signOut()
        source.onCheck = { SessionCheck.Rejected }

        // The user signs in again while a late 401 of the first session is being handled.
        val newSignIn = launch { repository.signIn(Hunter) }
        val lateRejection = launch { repository.expireSession(old.accessToken) }
        advanceUntilIdle()
        newSignIn.join()
        lateRejection.join()

        val current = repository.session()
        assertEquals(AccessToken("token-2"), current.accessToken)
        assertEquals(current, SessionStorage(store).load())
    }

    // ---- signing out ----

    @Test
    fun signOutErasesTheSessionAndRevokesTheToken() = runTest {
        val repository = signedOutRepository()
        val session = repository.signInAsHunter()

        repository.signOut()

        assertEquals(OutAfterHunter, repository.authState.value)
        assertNull(SessionStorage(store).load())
        assertEquals(listOf(session.accessToken), source.revoked)
    }

    @Test
    fun signOutErasesTheStoreEvenIfRevokeFails() = runTest {
        val repository = signedOutRepository()
        repository.signInAsHunter()
        source.onRevoke = { throw RemoteFailure.NoConnection() }

        repository.signOut()

        assertEquals(OutAfterHunter, repository.authState.value)
        assertNull(SessionStorage(store).load())
        assertTrue(source.revoked.isNotEmpty())
    }

    @Test
    fun signOutDoesNotWaitForAHangingServerLongerThanTheTimeout() = runTest {
        val repository = signedOutRepository()
        val session = repository.signInAsHunter()
        source.onRevoke = { delay(NeverMillis) }

        val signingOut = launch { repository.signOut() }
        runCurrent()

        // The user is out at once; the revoke is still going on.
        assertEquals(OutAfterHunter, repository.authState.value)
        assertNull(SessionStorage(store).load())
        assertTrue(signingOut.isActive)
        assertEquals(listOf(session.accessToken), source.revoked)

        advanceTimeBy(4_999)
        runCurrent()
        assertTrue(signingOut.isActive)
        advanceTimeBy(1)
        runCurrent()
        assertFalse(signingOut.isActive)
        assertEquals(5_000, currentTime)
    }

    @Test
    fun revokeTimeoutCanBeChanged() = runTest {
        val repository = repository(revokeTimeout = 2.seconds)
        repository.restoreSession()
        repository.signInAsHunter()
        source.onRevoke = { delay(NeverMillis) }

        repository.signOut()

        assertEquals(2_000, currentTime)
    }

    @Test
    fun aSlowButTimelyRevokeIsWaitedFor() = runTest {
        val repository = signedOutRepository()
        repository.signInAsHunter()
        var finished = false
        source.onRevoke = {
            delay(4_000)
            finished = true
        }

        repository.signOut()

        assertTrue(finished)
        assertEquals(4_000, currentTime)
    }

    @Test
    fun signOutIsCompletedEvenIfTheCallerIsCancelled() = runTest {
        SessionStorage(store).save(SavedSession)
        lateinit var caller: Job
        var armed = false
        val cancelling = object : SecureStore by store {
            override fun remove(key: String) {
                if (armed) caller.cancel()
                store.remove(key)
            }
        }
        val repository = signedOutRepository(secureStore = cancelling)
        armed = true

        caller = launch { repository.signOut() }
        caller.join()

        assertEquals(AuthState.SignedOut(lastEmail = SavedSession.email), repository.authState.value)
        assertNull(SessionStorage(store).load())
        assertEquals(listOf(SavedSession.accessToken), source.revoked)
    }

    @Test
    fun signOutForgetsTheRegistrations() = runTest {
        val repository = signedOutRepository()
        repository.register(Hunter)

        repository.signOut()

        assertNull(repository.pendingRegistration(HunterAddress))
        assertNull(repository.signInPending(HunterAddress))
    }

    @Test
    fun signOutRevokesOnlyTheTokenOfTheCurrentSession() = runTest {
        val repository = signedOutRepository()
        repository.signInAsHunter()
        repository.signOut()
        val second = repository.signInAsHunter()

        repository.signOut()

        assertEquals(listOf(AccessToken("token-1"), second.accessToken), source.revoked)
    }

    @Test
    fun signOutWhenSignedOutIsHarmless() = runTest {
        val repository = signedOutRepository()

        repository.signOut()

        assertEquals(AuthState.SignedOut(), repository.authState.value)
        assertTrue(source.revoked.isEmpty())
    }

    @Test
    fun signOutWhenSignedOutKeepsTheLastAddress() = runTest {
        val repository = signedOutRepository()
        repository.signInAsHunter()
        repository.signOut()

        repository.signOut()

        assertEquals(OutAfterHunter, repository.authState.value)
        assertEquals(1, source.revoked.size)
    }

    @Test
    fun signOutAfterAnEndedSessionDoesNotCallTheServer() = runTest {
        val repository = signedOutRepository()
        val session = repository.signInAsHunter()
        source.onCheck = { SessionCheck.Rejected }
        repository.expireSession(session.accessToken)

        repository.signOut()

        assertTrue(source.revoked.isEmpty())
        // The user is out of an expired session by hand: the screen no longer says that it expired.
        assertEquals(OutAfterHunter, repository.authState.value)
    }

    @Test
    fun signOutIsOutEvenIfTheStoreCannotBeCleared() = runTest {
        SessionStorage(store).save(SavedSession)
        val repository = signedOutRepository(secureStore = FlakyStore(store, failingWipe = true))

        repository.signOut()

        assertEquals(AuthState.SignedOut(lastEmail = SavedSession.email), repository.authState.value)
    }

    @Test
    fun signOutRetriesTheEraseAndDoesNotWipeWhenARetrySucceeds() = runTest {
        SessionStorage(store).save(SavedSession)
        // The first clear fails on all of its entries, the second one succeeds.
        val flaky = FlakyStore(store, failingRemoves = EntriesOfASession)
        val repository = signedOutRepository(secureStore = flaky)

        repository.signOut()

        assertEquals(0, flaky.wipes)
        assertNull(SessionStorage(store).load())
        assertNull(store.read("session.v1"))
    }

    @Test
    fun signOutGivesUpEraseAfterThreeAttemptsAndWipesTheStore() = runTest {
        SessionStorage(store).save(SavedSession)
        val flaky = FlakyStore(store)
        val repository = signedOutRepository(secureStore = flaky)
        val removesBefore = flaky.failingRemoves

        repository.signOut()

        // Three attempts at every entry, then exactly one wipe.
        assertEquals(removesBefore - 3 * EntriesOfASession, flaky.failingRemoves)
        assertEquals(1, flaky.wipes)
        assertEquals(AuthState.SignedOut(lastEmail = SavedSession.email), repository.authState.value)
    }

    @Test
    fun anotherRepositoryOnTheSameStoreIsSignedOutWhenRemoveFailedAndWipeCleared() = runTest {
        SessionStorage(store).save(SavedSession)
        val first = signedOutRepository(secureStore = FlakyStore(store))

        first.signOut()

        val second = repository(secureStore = store)
        second.restoreSession()
        // The wipe took the session, and the address was written again after it.
        assertEquals(AuthState.SignedOut(lastEmail = SavedSession.email), second.authState.value)
    }

    @Test
    fun sessionStaysOnTheDiskOnlyWhenEvenWipeFails() = runTest {
        // The honest limit: with a store that cannot erase at all, the next process finds the session.
        SessionStorage(store).save(SavedSession)
        val repository = signedOutRepository(secureStore = FlakyStore(store, failingWipe = true))

        repository.signOut()

        assertEquals(AuthState.SignedOut(lastEmail = SavedSession.email), repository.authState.value)
        assertEquals(SavedSession, SessionStorage(store).load())
    }

    @Test
    fun failedEraseKeepsTheProcessFromRestoringTheSessionAgain() = runTest {
        SessionStorage(store).save(SavedSession)
        val flaky = FlakyStore(store, failingWipe = true)
        val repository = repository(secureStore = flaky)
        // Signed out before the restore got its turn: the record is still readable on the disk.
        repository.signOut()
        val readsBefore = flaky.reads

        repository.restoreSession()

        assertEquals(AuthState.SignedOut(), repository.authState.value)
        assertEquals(readsBefore, flaky.reads)
        assertEquals(emptyList(), source.checked)
    }

    @Test
    fun restoreThatWasUnderWayDoesNotReadARecordThatASignOutCouldNotErase() = runTest {
        SessionStorage(store).save(SavedSession)
        val flaky = FlakyStore(store, failingWipe = true)
        val io = HoldFirstDispatcher(StandardTestDispatcher(testScheduler))
        val repository = repository(secureStore = flaky, io = io)
        val restoring = launch { repository.restoreSession() }
        runCurrent()

        // The restore waits for the store; meanwhile a sign-out cannot erase the record from the disk.
        repository.signOut()
        val readsBefore = flaky.reads
        io.release()
        restoring.join()

        // It read the address and nothing else: the record of the session was left alone.
        assertEquals(readsBefore + 1, flaky.reads)
        assertEquals(AuthState.SignedOut(), repository.authState.value)
        assertEquals(emptyList(), source.checked)
    }

    // ---- secrets ----

    @Test
    fun passwordAndAddressNeverAppearInTheTextOfStatesAndOutcomes() = runTest {
        val repository = signedOutRepository()
        val things = mutableListOf<Any?>()

        things += repository.register(Hunter)
        things += repository.pendingRegistration(HunterAddress)
        things += repository.resendLetter(HunterAddress)
        things += repository.confirmEmail(HunterAddress, "abc")
        things += repository.authState.value
        things += repository.signInPending(HunterAddress)
        things += repository.signIn(Hunter)
        things += repository.authState.value
        repository.signOut()
        things += repository.authState.value
        things += Hunter

        for (thing in things) {
            val text = thing.toString()
            assertFalse(HunterPassword in text, text)
            assertFalse(HunterAddress in text, text)
            assertFalse("token-" in text, text)
        }
    }

    // ---- the whole flow on a played server ----

    @Test
    fun registrationConfirmationAndSignInOnAPlayedServer() = runTest {
        val played = PlayedAccountServer(time)
        val repository = signedOutRepository(server = played)

        val sent = assertIs<RegistrationOutcome.LetterSent>(repository.register(Hunter))
        assertEquals(PendingRegistration(HunterAddress, 60.seconds, hasPassword = true), sent.pending)
        // "I've confirmed" before the link was opened: the account does not exist yet.
        assertEquals(SignInOutcome.NotConfirmedYet, repository.signInPending(HunterAddress))
        assertEquals(AuthState.SignedOut(), repository.authState.value)
        // A link from another letter is not the right one.
        val foreignLink = "https://matchlab.space/confirm?token=1"
        assertEquals(ConfirmationOutcome.InvalidLink, repository.confirmEmail(HunterAddress, foreignLink))

        val confirmed = repository.confirmEmail(HunterAddress, "https://matchlab.space/confirm?token=123456")

        assertEquals(ConfirmationOutcome.Confirmed(signedIn = true), confirmed)
        val session = repository.session()
        assertEquals(UserId("user-me"), session.userId)
        assertEquals(HunterAddress, session.email)

        // The next launch restores the session, and the server accepts its token.
        val relaunched = signedOutRepository(server = played)
        assertEquals(AuthState.SignedIn(session), relaunched.authState.value)

        relaunched.signOut()
        assertEquals(OutAfterHunter, relaunched.authState.value)
        assertEquals(SessionCheck.Rejected, played.checkSession(session.accessToken))
    }

    @Test
    fun addressConfirmedElsewhereCanStillSignInOnAPlayedServer() = runTest {
        val played = PlayedAccountServer(time)
        val repository = signedOutRepository(server = played)
        repository.register(Hunter)
        // The user opened the link in a browser, not in the app.
        assertEquals(ConfirmReply.Confirmed, played.confirmEmail("123456"))

        assertEquals(RegistrationOutcome.AlreadyRegistered, repository.resendLetter(HunterAddress))
        assertEquals(SignInOutcome.SignedIn, repository.signInPending(HunterAddress))
    }

    @Test
    fun expiredLinkOnAPlayedServerAsksForANewLetter() = runTest {
        val played = PlayedAccountServer(time)
        val repository = signedOutRepository(server = played)
        repository.register(Hunter)
        time += (31 * 60).seconds

        assertEquals(ConfirmationOutcome.LinkExpired, repository.confirmEmail(HunterAddress, "123456"))

        assertIs<RegistrationOutcome.LetterSent>(repository.resendLetter(HunterAddress))
        assertEquals(ConfirmationOutcome.Confirmed(signedIn = true), repository.confirmEmail(HunterAddress, "123456"))
    }
}
