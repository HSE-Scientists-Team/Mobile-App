package org.matchlab.academic_net.feature.auth.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.feature.auth.domain.AuthState

/**
 * The authorization is not lost when the user comes back to the app. What a user meets after the app is started
 * again, as the screens of this module and the repository under them play it: the saved session is there at once
 * and without the network, the sign-in screen is for a user who is signed out only, and what it starts with is the
 * address of the user, never a password.
 */
class SessionContinuityTest : AuthViewModelTest() {
    /** The user signs in on the sign-in screen, as they do the first time. */
    private fun AuthTestHarness.signInByForm(email: String = TrustedEmail, password: String = TestPassword) {
        val viewModel = signInViewModel(initialEmail = email)
        viewModel.onPasswordChange(password)
        viewModel.onSignInClick()
        assertIs<AuthState.SignedIn>(authState)
    }

    private val AuthTestHarness.signedInSession
        get() = assertIs<AuthState.SignedIn>(authState).session

    // The session survives a restart

    @Test
    fun aSavedSessionIsThereAtOnceAfterARestartWithoutTheNetwork() = runTest {
        harness.createAccount()
        harness.signInByForm()
        val before = harness.signedInSession

        val restarted = harness.restart()
        restarted.source.offline = true
        // Until the saved session is read the app shows its splash, and decides nothing.
        assertIs<AuthState.Restoring>(restarted.authState)
        restarted.repository.restoreSession()

        assertEquals(before, restarted.signedInSession)
        // Nothing was asked of the user and nothing was sent to sign them in.
        assertEquals(0, restarted.source.signIns)
        assertEquals(0, restarted.source.registers)
    }

    @Test
    fun aSavedSessionStaysWhenTheServerAnswersWithAFailureInsteadOfARefusal() = runTest {
        harness.createAccount()
        harness.signInByForm()
        val before = harness.signedInSession

        val restarted = harness.restart()
        restarted.source.failure = RemoteFailure.Http(503, "down")
        restarted.repository.restoreSession()

        assertEquals(before, restarted.signedInSession)
    }

    @Test
    fun aSessionTheServerStillAcceptsIsKeptAfterARestart() = runTest {
        harness.createAccount()
        harness.signInByForm()
        val before = harness.signedInSession

        val restarted = harness.restart()
        restarted.repository.restoreSession()

        assertEquals(before, restarted.signedInSession)
    }

    @Test
    fun aRestartOfARestartKeepsTheSessionToo() = runTest {
        harness.createAccount()
        harness.signInByForm()
        val before = harness.signedInSession

        val second = harness.restart()
        second.source.offline = true
        second.repository.restoreSession()
        val third = second.restart()
        third.source.offline = true
        third.repository.restoreSession()

        assertEquals(before, third.signedInSession)
    }

    // The sign-in screen: for a user who is signed out

    @Test
    fun aUserWhoHasNeverSignedInGetsAnEmptySignInScreen() = runTest {
        harness.repository.restoreSession()

        val signedOut = assertIs<AuthState.SignedOut>(harness.authState)
        val start = signedOut.signInStart()
        assertEquals(SignInStart(email = "", sessionExpired = false), start)

        val viewModel = harness.signInViewModel(initialEmail = start.email)
        assertEquals(SignInUiState(), viewModel.uiState.value)
    }

    @Test
    fun afterTheServerRefusedTheSessionTheSignInScreenSaysSoAndOnlyThePasswordIsLeftToType() = runTest {
        harness.createAccount()
        harness.signInByForm()
        val ended = harness.signedInSession
        harness.revokeOnTheServer(ended.accessToken)

        val restarted = harness.restart()
        restarted.repository.restoreSession()

        val signedOut = assertIs<AuthState.SignedOut>(restarted.authState)
        assertTrue(signedOut.sessionExpired)
        val start = signedOut.signInStart()
        assertEquals(SignInStart(email = TrustedEmail, sessionExpired = true), start)
        assertEquals(SignInBanner.SessionEnded, signInBanner(start.sessionExpired, emailConfirmed = false))

        val viewModel = restarted.signInViewModel(initialEmail = start.email)
        assertEquals(TrustedEmail, viewModel.uiState.value.email)
        assertEquals("", viewModel.uiState.value.password)
        viewModel.onPasswordChange(TestPassword)
        viewModel.onSignInClick()
        val renewed = restarted.signedInSession
        assertEquals(TrustedEmail, renewed.email)
        // The session is a new one: the refused token is not brought back.
        assertNotEquals(ended.accessToken, renewed.accessToken)
    }

    @Test
    fun theSessionThatEndedWhileTheAppRanLeavesTheAddressForTheSignInScreenToo() = runTest {
        harness.createAccount()
        harness.signInByForm()
        val ended = harness.signedInSession
        harness.revokeOnTheServer(ended.accessToken)

        // A 401 on a call of the session: the repository asks the server about the token and the server refuses it.
        harness.repository.expireSession(ended.accessToken)

        val signedOut = assertIs<AuthState.SignedOut>(harness.authState)
        assertEquals(SignInStart(email = TrustedEmail, sessionExpired = true), signedOut.signInStart())
    }

    @Test
    fun aTokenTheServerStillAcceptsDoesNotEndTheSessionOnASingleRefusalOfACall() = runTest {
        harness.createAccount()
        harness.signInByForm()
        val session = harness.signedInSession

        harness.repository.expireSession(session.accessToken)

        assertEquals(session, harness.signedInSession)
    }

    @Test
    fun afterASignOutTheSignInScreenHasTheAddressAndNoBanner() = runTest {
        harness.createAccount()
        harness.signInByForm()

        harness.repository.signOut()

        val signedOut = assertIs<AuthState.SignedOut>(harness.authState)
        val start = signedOut.signInStart()
        assertEquals(SignInStart(email = TrustedEmail, sessionExpired = false), start)
        assertNull(signInBanner(start.sessionExpired, emailConfirmed = false))
        val viewModel = harness.signInViewModel(initialEmail = start.email)
        assertEquals(TrustedEmail, viewModel.uiState.value.email)
        assertEquals("", viewModel.uiState.value.password)
    }

    @Test
    fun theAddressOfASignedOutUserSurvivesARestartAndTheSessionDoesNot() = runTest {
        harness.createAccount()
        harness.signInByForm()
        harness.repository.signOut()

        val restarted = harness.restart()
        restarted.source.offline = true
        restarted.repository.restoreSession()

        val signedOut = assertIs<AuthState.SignedOut>(restarted.authState)
        assertEquals(SignInStart(email = TrustedEmail, sessionExpired = false), signedOut.signInStart())
        assertEquals(0, restarted.source.signIns)
    }

    @Test
    fun aUserWhoRegisteredButDidNotConfirmFindsTheirAddressOnTheSignInScreenAfterARestart() = runTest {
        // The process was killed after the letter was sent: the password is lost, the address is not.
        harness.registerAndWait()

        val restarted = harness.restart()
        restarted.repository.restoreSession()

        val signedOut = assertIs<AuthState.SignedOut>(restarted.authState)
        assertEquals(SignInStart(email = TrustedEmail, sessionExpired = false), signedOut.signInStart())
        assertNull(restarted.getPendingRegistration(TrustedEmail))
    }

    @Test
    fun afterARestartTheConfirmationScreenSendsTheUserToSignInInsteadOfSigningInWithAPasswordItDoesNotHave() = runTest {
        harness.registerAndWait()
        harness.openLinkInBrowser()

        val restarted = harness.restart()
        restarted.repository.restoreSession()
        val viewModel = restarted.confirmViewModel(TrustedEmail)
        viewModel.onSignedInClick()

        assertEquals(ConfirmExit.SignIn(emailConfirmed = false), viewModel.uiState.value.exit)
        assertEquals(0, restarted.source.signIns)
        assertFalse(restarted.authState is AuthState.SignedIn)
    }

    // The password is nowhere

    @Test
    fun noPasswordIsEverWrittenToTheDeviceByRegisteringConfirmingAndSigningIn() = runTest {
        val recording = RecordingStore()
        val app = AuthTestHarness(store = recording)

        val register = app.registerViewModel()
        register.onEmailChange(TrustedEmail)
        register.onPasswordChange(TestPassword)
        register.onRepeatedPasswordChange(TestPassword)
        register.onRegisterClick()
        val confirm = app.confirmViewModel(TrustedEmail)
        confirm.onLinkChange("https://matchlab.space/confirm?token=123456")
        confirm.onConfirmLinkClick()
        assertIs<AuthState.SignedIn>(app.authState)
        app.repository.signOut()
        app.signInByForm()

        assertTrue(recording.writes.isNotEmpty(), "the session and the address are written")
        recording.writes.forEach { (key, value) ->
            assertFalse(TestPassword in key, "the password is in the key $key")
            assertFalse(TestPassword in value, "the password is in the value of $key")
            // The link of the letter is not kept either.
            assertFalse("matchlab.space" in value, "the link of the letter is in the value of $key")
        }
    }
}
