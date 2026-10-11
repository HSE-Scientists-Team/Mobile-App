package org.matchlab.academic_net.feature.auth.ui

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.matchlab.academic_net.feature.auth.domain.AuthState

@OptIn(ExperimentalCoroutinesApi::class)
class VerifyEmailViewModelTest {
    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val harness = AuthTestHarness()

    /** The user asked for a code on the sign-in screen and is on the code screen now. */
    private suspend fun openedAfterRequest(): VerifyEmailViewModel {
        harness.requestCode(TrustedEmail)
        return harness.verifyViewModel(TrustedEmail)
    }

    /** Lets [seconds] pass for the clock of the screen and of the server. */
    private fun TestScope.wait(seconds: Int) {
        harness.advance(seconds.seconds)
        passSeconds(seconds)
    }

    /** Lets the virtual time of the coroutines pass, including the tasks due exactly at its end. */
    private fun TestScope.passSeconds(seconds: Int) {
        advanceTimeBy(seconds * 1000L)
        runCurrent()
    }

    private fun VerifyEmailViewModel.type(code: String) = onCodeChange(code)

    @Test
    fun startsWithTheWaitTheServerAskedFor() = runTest {
        val viewModel = openedAfterRequest()

        val state = viewModel.uiState.value
        assertEquals(TrustedEmail, state.email)
        assertEquals("", state.code)
        assertEquals(60, state.resendInSeconds)
    }

    @Test
    fun countdownGoesDownEverySecondUntilResendIsAllowed() = runTest {
        val viewModel = openedAfterRequest()

        passSeconds(1)
        assertEquals(59, viewModel.uiState.value.resendInSeconds)

        passSeconds(58)
        assertEquals(1, viewModel.uiState.value.resendInSeconds)

        passSeconds(1)
        assertEquals(0, viewModel.uiState.value.resendInSeconds)

        // And stays there.
        passSeconds(5)
        assertEquals(0, viewModel.uiState.value.resendInSeconds)
    }

    @Test
    fun screenOpenedLaterShowsTheTimeThatIsReallyLeft() = runTest {
        harness.requestCode(TrustedEmail)
        harness.advance(45.seconds)

        val viewModel = harness.verifyViewModel(TrustedEmail)

        assertEquals(15, viewModel.uiState.value.resendInSeconds)
    }

    @Test
    fun noKnownCodeMeansNoWait() = runTest {
        val viewModel = harness.verifyViewModel(TrustedEmail)

        assertEquals(0, viewModel.uiState.value.resendInSeconds)
    }

    @Test
    fun inputKeepsOnlyDigitsAndSixOfThem() = runTest {
        val viewModel = openedAfterRequest()

        viewModel.type("12 a-3")
        assertEquals("123", viewModel.uiState.value.code)

        viewModel.type("1234567890")
        // Six digits are sent at once; the wrong code is cleared from the field then.
        assertEquals(1, harness.codeChecks)
    }

    @Test
    fun sixthDigitSendsTheCodeAndTheRightOneSignsIn() = runTest {
        val viewModel = openedAfterRequest()

        viewModel.type("12345")
        assertEquals(0, harness.codeChecks)
        viewModel.type("123456")

        assertEquals(1, harness.codeChecks)
        val state = viewModel.uiState.value
        // Verified: the screen is about to be replaced and takes nothing more.
        assertTrue(state.isVerifying)
        assertNull(state.error)
        val signedIn = assertIs<AuthState.SignedIn>(harness.repository.authState.value)
        assertEquals(TrustedEmail, signedIn.session.email)
    }

    @Test
    fun confirmAfterTheAutoSendDoesNotSendTheSpentCodeAgain() = runTest {
        val viewModel = openedAfterRequest()

        viewModel.type("123456")
        // The root has not replaced the screen yet: the button and IME Done are still reachable.
        viewModel.onConfirmClick()
        viewModel.type("123456")

        assertEquals(1, harness.codeChecks)
        assertNull(viewModel.uiState.value.error)
        assertIs<AuthState.SignedIn>(harness.repository.authState.value)
    }

    @Test
    fun stateTextHidesTheAddressAndTheCode() = runTest {
        val viewModel = openedAfterRequest()
        harness.offline = true
        viewModel.type("123456")

        val state = viewModel.uiState.value
        assertEquals("123456", state.code)
        val text = state.toString()
        assertFalse(TrustedEmail in text, text)
        assertFalse("123456" in text, text)
        assertTrue("Network" in text, text)
    }

    @Test
    fun confirmButtonSendsACompleteCodeButNotAnIncompleteOne() = runTest {
        val viewModel = openedAfterRequest()

        viewModel.type("123")
        viewModel.onConfirmClick()
        assertEquals(0, harness.codeChecks)
    }

    @Test
    fun wrongCodeClearsTheFieldAndSaysHowManyAttemptsAreLeft() = runTest {
        val viewModel = openedAfterRequest()

        viewModel.type("000000")

        val state = viewModel.uiState.value
        assertEquals(VerifyError.WrongCode, state.error)
        assertEquals(4, state.attemptsLeft)
        assertEquals("", state.code)
        assertFalse(state.isLocked)
        assertFalse(state.isVerifying)
    }

    @Test
    fun typingAgainRemovesTheError() = runTest {
        val viewModel = openedAfterRequest()
        viewModel.type("000000")

        viewModel.type("1")

        val state = viewModel.uiState.value
        assertNull(state.error)
        assertEquals("1", state.code)
    }

    @Test
    fun fifthWrongCodeLocksTheField() = runTest {
        val viewModel = openedAfterRequest()
        repeat(5) { viewModel.type("000000") }

        val state = viewModel.uiState.value
        assertEquals(VerifyError.TooManyAttempts, state.error)
        assertTrue(state.isLocked)
        assertEquals(5, harness.codeChecks)

        // A locked field takes nothing, not even the right code.
        viewModel.type("123456")
        assertEquals("", viewModel.uiState.value.code)
        assertEquals(5, harness.codeChecks)
    }

    @Test
    fun newCodeUnlocksTheFieldAfterTheWait() = runTest {
        val viewModel = openedAfterRequest()
        repeat(5) { viewModel.type("000000") }
        wait(60)
        assertEquals(0, viewModel.uiState.value.resendInSeconds)

        viewModel.onResendClick()

        val state = viewModel.uiState.value
        assertFalse(state.isLocked)
        assertNull(state.error)
        assertNull(state.attemptsLeft)
        assertEquals(60, state.resendInSeconds)
        assertEquals("", state.code)

        viewModel.type("123456")
        assertIs<AuthState.SignedIn>(harness.repository.authState.value)
    }

    @Test
    fun resendIsNotPossibleBeforeTheCountdownEnds() = runTest {
        val viewModel = openedAfterRequest()
        val requestsBefore = harness.codeRequests

        viewModel.onResendClick()

        assertEquals(requestsBefore, harness.codeRequests)
    }

    @Test
    fun oldCodeIsOutOfDateAfterTenMinutes() = runTest {
        val viewModel = openedAfterRequest()
        harness.advance(10.minutes)

        viewModel.type("123456")

        val state = viewModel.uiState.value
        assertEquals(VerifyError.Expired, state.error)
        assertFalse(state.isLocked)
    }

    @Test
    fun lostConnectionKeepsTheCodeInTheFieldForAnotherTry() = runTest {
        val viewModel = openedAfterRequest()
        harness.offline = true

        viewModel.type("123456")

        val failed = viewModel.uiState.value
        assertEquals(VerifyError.Network, failed.error)
        assertEquals("123456", failed.code)
        assertFalse(failed.isVerifying)

        harness.offline = false
        viewModel.onConfirmClick()

        assertIs<AuthState.SignedIn>(harness.repository.authState.value)
    }

    @Test
    fun lostConnectionOnResendIsANetworkError() = runTest {
        val viewModel = openedAfterRequest()
        wait(60)
        harness.offline = true

        viewModel.onResendClick()

        val state = viewModel.uiState.value
        assertEquals(VerifyError.Network, state.error)
        assertFalse(state.isResending)
        assertEquals(0, state.resendInSeconds)
    }

    @Test
    fun serverCooldownThatTheScreenDidNotKnowAboutBecomesTheCountdown() = runTest {
        // The code was requested before the app was restarted: the screen knows nothing of it.
        harness.source.requestCode(TrustedEmail)
        harness.advance(10.seconds)
        val viewModel = harness.verifyViewModel(TrustedEmail)
        assertEquals(0, viewModel.uiState.value.resendInSeconds)

        viewModel.onResendClick()

        val state = viewModel.uiState.value
        assertEquals(VerifyError.TooManyRequests, state.error)
        assertEquals(50, state.resendInSeconds)
        assertFalse(state.isResending)
    }

    @Test
    fun countdownTextIsMinutesAndTwoDigitSeconds() {
        assertEquals("1:00", countdownText(60))
        assertEquals("0:09", countdownText(9))
        assertEquals("12:30", countdownText(750))
        assertEquals("0:00", countdownText(-1))
    }
}
