package org.matchlab.academic_net.feature.auth.ui

import androidx.lifecycle.SavedStateHandle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.feature.auth.data.SignInReply
import org.matchlab.academic_net.feature.auth.domain.AuthState
import org.matchlab.academic_net.feature.auth.domain.CredentialsError

class SignInViewModelTest : AuthViewModelTest() {
    private fun SignInViewModel.fill(email: String = TrustedEmail, password: String = TestPassword) {
        onEmailChange(email)
        onPasswordChange(password)
    }

    private val SignInViewModel.state get() = uiState.value

    // The form

    @Test
    fun startsWithTheAddressItWasGivenAndNoPassword() {
        val viewModel = harness.signInViewModel(initialEmail = TrustedEmail)

        assertEquals(SignInUiState(email = TrustedEmail), viewModel.state)
        assertEquals("", viewModel.state.password)
    }

    @Test
    fun startsEmptyWhenThereIsNoAddressToStartWith() {
        assertEquals(SignInUiState(), harness.signInViewModel().state)
    }

    @Test
    fun typingChangesTheTextAsTypedWithoutTrimmingAnything() {
        val viewModel = harness.signInViewModel()

        viewModel.fill(email = "  Anna@HSE.ru ", password = "  spaced out  ")

        assertEquals("  Anna@HSE.ru ", viewModel.state.email)
        assertEquals("  spaced out  ", viewModel.state.password)
    }

    // Checks of the form: nothing of an invalid form reaches the server

    @Test
    fun emptyFormIsTwoErrorsThatNeverReachTheServer() {
        val viewModel = harness.signInViewModel()

        viewModel.onSignInClick()

        assertEquals(setOf(CredentialsError.EmailEmpty, CredentialsError.PasswordEmpty), viewModel.state.errors)
        assertFalse(viewModel.state.isSubmitting)
        assertNull(viewModel.state.problem)
        assertEquals(0, harness.source.signIns)
    }

    @Test
    fun blankAddressCountsAsEmpty() {
        val viewModel = harness.signInViewModel()
        viewModel.fill(email = "   ")

        viewModel.onSignInClick()

        assertEquals(setOf(CredentialsError.EmailEmpty), viewModel.state.errors)
        assertEquals(0, harness.source.signIns)
    }

    @Test
    fun addressWithATypoNeverReachesTheServer() {
        val viewModel = harness.signInViewModel()
        viewModel.fill(email = "anna.hse.ru")

        viewModel.onSignInClick()

        assertEquals(setOf(CredentialsError.EmailMalformed), viewModel.state.errors)
        assertEquals(0, harness.source.signIns)
    }

    @Test
    fun emptyPasswordNeverReachesTheServer() {
        val viewModel = harness.signInViewModel()
        viewModel.fill(password = "")

        viewModel.onSignInClick()

        assertEquals(setOf(CredentialsError.PasswordEmpty), viewModel.state.errors)
        assertEquals(0, harness.source.signIns)
    }

    @Test
    fun aPasswordNoAccountCanHaveIsToldAsWrongAndNeverReachesTheServer() = runTest {
        // The schema holds a password of 8 to 72 bytes, for a registration and for a sign-in alike: "abc" is nobody's
        // password, so the user is told "wrong address or password" without a request (which could come back as a 400).
        val viewModel = harness.signInViewModel()
        viewModel.fill(password = "abc")

        viewModel.onSignInClick()

        assertEquals(0, harness.source.signIns)
        assertEquals(AuthProblem.WrongCredentials, viewModel.state.problem)
        assertTrue(viewModel.state.errors.isEmpty(), "it is the verdict on the pair, not a mistake of the field")
        assertFalse(viewModel.state.isSubmitting)
        assertEquals("abc", viewModel.state.password, "the form stays filled for another try")
        assertFalse(harness.authState is AuthState.SignedIn)
    }

    @Test
    fun aPasswordOfSeventyThreeBytesIsToldAsWrongWithoutARequestToo() = runTest {
        harness.createAccount()
        val viewModel = harness.signInViewModel()
        // 37 Cyrillic letters are 74 bytes.
        viewModel.fill(password = "ж".repeat(37))

        viewModel.onSignInClick()

        assertEquals(0, harness.source.signIns)
        assertEquals(AuthProblem.WrongCredentials, viewModel.state.problem)

        viewModel.onPasswordChange("x".repeat(73))
        viewModel.onSignInClick()
        assertEquals(0, harness.source.signIns)
        assertEquals(AuthProblem.WrongCredentials, viewModel.state.problem)
    }

    @Test
    fun aPasswordOfAllowedLengthGoesToTheServerWhichDecides() = runTest {
        // 8 bytes: the least the schema holds; 4 Cyrillic letters are 8 bytes as well; 72 bytes: the most.
        for (password in listOf("12345678", "жжжж", "p".repeat(72))) {
            val harness = AuthTestHarness()
            harness.createAccount(password = password)
            val viewModel = harness.signInViewModel()
            viewModel.fill(password = password)

            viewModel.onSignInClick()

            assertEquals(1, harness.source.signIns, password)
            assertIs<AuthState.SignedIn>(harness.authState, password)
        }
    }

    @Test
    fun aWrongPasswordOfAllowedLengthIsAskedOfTheServerAndRefusedByIt() = runTest {
        harness.createAccount()
        val viewModel = harness.signInViewModel()
        viewModel.fill(password = "12345678")

        viewModel.onSignInClick()

        assertEquals(1, harness.source.signIns, "the length is fine, so only the server can tell it is wrong")
        assertEquals(AuthProblem.WrongCredentials, viewModel.state.problem)
    }

    @Test
    fun aPasswordTooShortIsTheSameAnswerOnlineAndOffline() = runTest {
        harness.source.offline = true
        val viewModel = harness.signInViewModel()
        viewModel.fill(password = "abc")

        viewModel.onSignInClick()

        assertEquals(AuthProblem.WrongCredentials, viewModel.state.problem, "no request, so no network to fail")
    }

    @Test
    fun errorsAreNotKeptForTheNextAttempt() {
        val viewModel = harness.signInViewModel()
        viewModel.onSignInClick()
        assertTrue(viewModel.state.errors.isNotEmpty())
        viewModel.fill()
        harness.source.hold()

        viewModel.onSignInClick()

        assertTrue(viewModel.state.errors.isEmpty())
        assertTrue(viewModel.state.isSubmitting)
        harness.source.release()
    }

    @Test
    fun typingInAFieldTakesAwayItsOwnErrorsAndKeepsTheOthers() {
        val viewModel = harness.signInViewModel()
        viewModel.fill(email = "nonsense", password = "")
        viewModel.onSignInClick()
        assertEquals(setOf(CredentialsError.EmailMalformed, CredentialsError.PasswordEmpty), viewModel.state.errors)

        viewModel.onEmailChange(TrustedEmail)
        assertEquals(setOf(CredentialsError.PasswordEmpty), viewModel.state.errors)

        viewModel.onPasswordChange(TestPassword)
        assertTrue(viewModel.state.errors.isEmpty())
    }

    // What the server answers

    @Test
    fun signsInAndKeepsTheFormBusyUntilTheScreenIsGone() = runTest {
        harness.createAccount()
        val viewModel = harness.signInViewModel()
        viewModel.fill()

        viewModel.onSignInClick()

        val signedIn = assertIs<AuthState.SignedIn>(harness.authState)
        assertEquals(TrustedEmail, signedIn.session.email)
        // The root swaps the screens by the auth state; until it does the form takes no more requests.
        assertTrue(viewModel.state.isSubmitting)
        assertNull(viewModel.state.problem)
        assertTrue(viewModel.state.errors.isEmpty())
    }

    @Test
    fun addressIsNormalizedBeforeItGoesToTheServer() = runTest {
        harness.createAccount(email = TrustedEmail)
        val viewModel = harness.signInViewModel()
        viewModel.fill(email = "  Anna@HSE.ru ")

        viewModel.onSignInClick()

        assertEquals(TrustedEmail, harness.source.signInAttempts.single().email)
        assertIs<AuthState.SignedIn>(harness.authState)
    }

    @Test
    fun passwordGoesToTheServerExactlyAsTyped() = runTest {
        val spaced = "  pa ss  "
        harness.createAccount(password = spaced)
        val viewModel = harness.signInViewModel()
        viewModel.fill(password = spaced)

        viewModel.onSignInClick()

        assertEquals(spaced, harness.source.signInAttempts.single().password.value)
        assertIs<AuthState.SignedIn>(harness.authState)
    }

    @Test
    fun wrongPasswordIsToldAndTheFormStaysFilledForAnotherTry() = runTest {
        harness.createAccount()
        val viewModel = harness.signInViewModel()
        viewModel.fill(password = OtherPassword)

        viewModel.onSignInClick()

        assertEquals(AuthProblem.WrongCredentials, viewModel.state.problem)
        assertFalse(viewModel.state.isSubmitting)
        assertEquals(TrustedEmail, viewModel.state.email)
        assertEquals(OtherPassword, viewModel.state.password)
        assertFalse(harness.authState is AuthState.SignedIn)

        viewModel.onPasswordChange(TestPassword)
        viewModel.onSignInClick()
        assertIs<AuthState.SignedIn>(harness.authState)
    }

    @Test
    fun addressWithoutAnAccountIsToldAsWrongCredentialsToo() {
        // The server does not say which of the two is wrong, and neither does the screen.
        val viewModel = harness.signInViewModel()
        viewModel.fill()

        viewModel.onSignInClick()

        assertEquals(AuthProblem.WrongCredentials, viewModel.state.problem)
    }

    @Test
    fun refusedAccountIsToldApartFromWrongCredentials() {
        harness.source.signInReply = SignInReply.Forbidden
        val viewModel = harness.signInViewModel()
        viewModel.fill()

        viewModel.onSignInClick()

        assertEquals(AuthProblem.AccountUnavailable, viewModel.state.problem)
        assertFalse(viewModel.state.isSubmitting)
    }

    @Test
    fun lostConnectionIsToldAndTheNextTryWorksWhenItIsBack() = runTest {
        harness.createAccount()
        harness.source.offline = true
        val viewModel = harness.signInViewModel()
        viewModel.fill()

        viewModel.onSignInClick()

        val problem = assertIs<AuthProblem.CallFailed>(viewModel.state.problem)
        assertIs<RemoteFailure.NoConnection>(problem.cause)
        assertFalse(viewModel.state.isSubmitting)
        assertEquals(TestPassword, viewModel.state.password)

        harness.source.offline = false
        viewModel.onSignInClick()
        assertIs<AuthState.SignedIn>(harness.authState)
    }

    @Test
    fun everyKindOfFailedCallKeepsItsCauseForTheText() {
        val causes = listOf(
            RemoteFailure.Timeout(),
            RemoteFailure.Http(503, "database is down"),
            RemoteFailure.Http(500, null),
            RemoteFailure.Malformed(),
        )
        causes.forEach { cause ->
            harness.source.failure = cause
            val viewModel = harness.signInViewModel()
            viewModel.fill()

            viewModel.onSignInClick()

            val problem = assertIs<AuthProblem.CallFailed>(viewModel.state.problem)
            assertSame(cause, problem.cause)
            assertFalse(viewModel.state.isSubmitting)
        }
    }

    @Test
    fun typingTakesTheProblemAway() {
        val viewModel = harness.signInViewModel()
        viewModel.fill()
        viewModel.onSignInClick()
        assertEquals(AuthProblem.WrongCredentials, viewModel.state.problem)

        viewModel.onPasswordChange(OtherPassword)
        assertNull(viewModel.state.problem)

        viewModel.onSignInClick()
        assertEquals(AuthProblem.WrongCredentials, viewModel.state.problem)

        viewModel.onEmailChange("someone.else@hse.ru")
        assertNull(viewModel.state.problem)
    }

    // One request at a time

    @Test
    fun aSecondTapWhileTheRequestIsOnItsWayAsksTheServerOnce() = runTest {
        harness.createAccount()
        val viewModel = harness.signInViewModel()
        viewModel.fill()
        harness.source.hold()

        viewModel.onSignInClick()
        assertTrue(viewModel.state.isSubmitting)
        viewModel.onSignInClick()
        viewModel.onSignInClick()
        assertEquals(1, harness.source.signIns)

        harness.source.release()
        assertIs<AuthState.SignedIn>(harness.authState)
        assertEquals(1, harness.source.signIns)
    }

    @Test
    fun aTapAfterTheSignInDoesNotAskForASecondSession() = runTest {
        harness.createAccount()
        val viewModel = harness.signInViewModel()
        viewModel.fill()
        viewModel.onSignInClick()
        assertIs<AuthState.SignedIn>(harness.authState)

        // The root has not taken the screen away yet: the button and the "Done" key of the keyboard still work.
        viewModel.onSignInClick()
        viewModel.onSignInClick()

        assertEquals(1, harness.source.signIns)
    }

    @Test
    fun theFormIsFreeAgainAfterAFailureSoTheNextTapAsksAgain() {
        val viewModel = harness.signInViewModel()
        viewModel.fill()

        viewModel.onSignInClick()
        viewModel.onSignInClick()

        assertEquals(2, harness.source.signIns)
    }

    @Test
    fun editingWhileTheRequestIsOnItsWayDoesNotChangeTheRequest() = runTest {
        harness.createAccount()
        val viewModel = harness.signInViewModel()
        viewModel.fill()
        harness.source.hold()
        viewModel.onSignInClick()

        viewModel.onPasswordChange(OtherPassword)
        harness.source.release()

        assertEquals(TestPassword, harness.source.signInAttempts.single().password.value)
        assertIs<AuthState.SignedIn>(harness.authState)
    }

    // The address that comes from outside

    @Test
    fun anAddressSavedByAnEarlierIncarnationWinsOverTheGivenOne() {
        val saved = SavedStateHandle(mapOf("email" to "typed@hse.ru"))

        val viewModel = harness.signInViewModel(initialEmail = TrustedEmail, savedState = saved)

        assertEquals("typed@hse.ru", viewModel.state.email)
    }

    @Test
    fun aNewAddressFromOutsideReplacesTheTextAndIsSaved() {
        val saved = SavedStateHandle()
        val viewModel = harness.signInViewModel(initialEmail = "first@hse.ru", savedState = saved)

        viewModel.onEmailSuggested("second@hse.ru")

        assertEquals("second@hse.ru", viewModel.state.email)
        assertEquals("second@hse.ru", saved.get<String>("email"))
    }

    @Test
    fun theSameAddressGivenAgainLeavesWhatTheUserTypedAlone() {
        // The route gives the address again each time the screen is composed again.
        val viewModel = harness.signInViewModel(initialEmail = "first@hse.ru")
        viewModel.onEmailChange("typed@hse.ru")

        viewModel.onEmailSuggested("first@hse.ru")
        viewModel.onEmailSuggested("first@hse.ru")

        assertEquals("typed@hse.ru", viewModel.state.email)
    }

    @Test
    fun anAddressThatWasGivenAndTypedOverIsNotBroughtBackByTheSameAddress() {
        val viewModel = harness.signInViewModel(initialEmail = "first@hse.ru")
        viewModel.onEmailSuggested("second@hse.ru")
        viewModel.onEmailChange("typed@hse.ru")

        viewModel.onEmailSuggested("second@hse.ru")

        assertEquals("typed@hse.ru", viewModel.state.email)
    }

    @Test
    fun anEmptyAddressFromOutsideIsNothingToSuggest() {
        val viewModel = harness.signInViewModel(initialEmail = "first@hse.ru")
        viewModel.onEmailChange("typed@hse.ru")

        viewModel.onEmailSuggested("")
        viewModel.onEmailSuggested("   ")

        assertEquals("typed@hse.ru", viewModel.state.email)
    }

    @Test
    fun aNewAddressFromOutsideTakesAwayTheProblemOfTheOldOne() {
        val viewModel = harness.signInViewModel(initialEmail = "first@hse.ru")
        viewModel.fill(email = "first@hse.ru")
        viewModel.onSignInClick()
        assertEquals(AuthProblem.WrongCredentials, viewModel.state.problem)

        viewModel.onEmailSuggested("second@hse.ru")

        assertNull(viewModel.state.problem)
    }

    // What is saved

    @Test
    fun onlyTheAddressIsEverSaved() {
        val saved = SavedStateHandle()
        val viewModel = harness.signInViewModel(savedState = saved)

        viewModel.onEmailChange(TrustedEmail)
        viewModel.onPasswordChange(TestPassword)
        viewModel.onSignInClick()
        viewModel.onPasswordChange(OtherPassword)

        assertEquals(setOf("email"), saved.keys())
        assertEquals(TrustedEmail, saved.get<String>("email"))
        saved.keys().forEach { key ->
            val value = saved.get<Any?>(key).toString()
            assertFalse(TestPassword in value, "the password is in the saved state under $key")
            assertFalse(OtherPassword in value, "the password is in the saved state under $key")
        }
    }

    @Test
    fun afterTheScreenIsMadeAgainTheAddressIsBackAndThePasswordIsNot() {
        val saved = SavedStateHandle()
        harness.signInViewModel(initialEmail = "given@hse.ru", savedState = saved).fill(password = TestPassword)

        // The process was killed and the screen comes back with the same saved state.
        val restored = harness.signInViewModel(initialEmail = "given@hse.ru", savedState = saved)

        assertEquals(TrustedEmail, restored.state.email)
        assertEquals("", restored.state.password)
    }

    // What the state tells

    @Test
    fun stateTextHidesTheAddressAndThePassword() {
        val viewModel = harness.signInViewModel()
        viewModel.fill()
        viewModel.onSignInClick()

        val text = viewModel.state.toString()

        assertFalse(TrustedEmail in text, text)
        assertFalse("anna" in text.lowercase(), text)
        assertFalse(TestPassword in text, text)
        assertTrue("hasEmail=true" in text, text)
        assertTrue("hasPassword=true" in text, text)
    }

    @Test
    fun stateTextOfAFailedCallNamesTheKindOnlyNotTheServersWords() {
        harness.source.failure = RemoteFailure.Http(503, "secret words of the server")
        val viewModel = harness.signInViewModel()
        viewModel.fill()

        viewModel.onSignInClick()

        val text = viewModel.state.toString()
        assertFalse("secret words" in text, text)
        assertTrue("CallFailed(Http)" in text, text)
    }
}
