package org.matchlab.academic_net.feature.auth.ui

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class SignInViewModelTest {
    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val harness = AuthTestHarness()

    @Test
    fun startsWithAnEmptyForm() {
        val state = harness.signInViewModel().uiState.value

        assertEquals(SignInUiState(), state)
    }

    @Test
    fun trustedEmailGetsACodeAndTheScreenLeavesForItWithTheNormalizedAddress() {
        val viewModel = harness.signInViewModel()

        viewModel.onEmailChange("  Anna@HSE.ru ")
        viewModel.onGetCodeClick()

        val state = viewModel.uiState.value
        assertEquals("anna@hse.ru", state.codeSentTo)
        assertNull(state.error)
        assertFalse(state.isSending)
    }

    @Test
    fun subdomainOfARegisteredDomainIsAccepted() {
        val viewModel = harness.signInViewModel()

        viewModel.onEmailChange("anna@student.hse.ru")
        viewModel.onGetCodeClick()

        assertEquals("anna@student.hse.ru", viewModel.uiState.value.codeSentTo)
    }

    @Test
    fun emailOfAnotherDomainIsRefusedByTheServer() {
        val viewModel = harness.signInViewModel()

        viewModel.onEmailChange("anna@example.com")
        viewModel.onGetCodeClick()

        val state = viewModel.uiState.value
        assertEquals(SignInError.UntrustedDomain, state.error)
        assertNull(state.codeSentTo)
        assertFalse(state.isSending)
    }

    @Test
    fun emptyEmailIsAnErrorThatNeverReachesTheServer() {
        val viewModel = harness.signInViewModel()

        viewModel.onEmailChange("   ")
        viewModel.onGetCodeClick()

        assertEquals(SignInError.EmptyEmail, viewModel.uiState.value.error)
        assertEquals(0, harness.codeRequests)
    }

    @Test
    fun malformedEmailIsAnErrorThatNeverReachesTheServer() {
        val viewModel = harness.signInViewModel()

        viewModel.onEmailChange("anna.hse.ru")
        viewModel.onGetCodeClick()

        assertEquals(SignInError.MalformedEmail, viewModel.uiState.value.error)
        assertEquals(0, harness.codeRequests)
    }

    @Test
    fun typingRemovesTheError() {
        val viewModel = harness.signInViewModel()
        viewModel.onEmailChange("anna@example.com")
        viewModel.onGetCodeClick()

        viewModel.onEmailChange("anna@hse.ru")

        val state = viewModel.uiState.value
        assertNull(state.error)
        assertEquals("anna@hse.ru", state.email)
    }

    @Test
    fun lostConnectionIsANetworkErrorAndTheFormStaysFilled() {
        harness.offline = true
        val viewModel = harness.signInViewModel()
        viewModel.onEmailChange(TrustedEmail)

        viewModel.onGetCodeClick()

        val state = viewModel.uiState.value
        assertEquals(SignInError.Network, state.error)
        assertEquals(TrustedEmail, state.email)
        assertFalse(state.isSending)
    }

    @Test
    fun secondRequestSoonAfterTheFirstIsToldHowLongToWait() {
        val viewModel = harness.signInViewModel()
        viewModel.onEmailChange(TrustedEmail)
        viewModel.onGetCodeClick()
        viewModel.onCodeSentHandled()
        harness.advance(20.seconds)

        viewModel.onGetCodeClick()

        val state = viewModel.uiState.value
        assertEquals(SignInError.TooManyRequests, state.error)
        assertEquals(40, state.retryAfterSeconds)
        assertNull(state.codeSentTo)
    }

    @Test
    fun theEventOfALeavingScreenIsConsumedSoThatTheNextCodeIsNoticedToo() {
        val viewModel = harness.signInViewModel()
        viewModel.onEmailChange(TrustedEmail)
        viewModel.onGetCodeClick()
        assertEquals(TrustedEmail, viewModel.uiState.value.codeSentTo)

        viewModel.onCodeSentHandled()
        assertNull(viewModel.uiState.value.codeSentTo)

        // The user came back and asks again, for the same address, after the wait.
        harness.advance(61.seconds)
        viewModel.onGetCodeClick()

        assertEquals(TrustedEmail, viewModel.uiState.value.codeSentTo)
        assertEquals(2, harness.codeRequests)
    }

    @Test
    fun askingAgainBeforeTheScreenHasLeftSendsNothing() {
        // The server replaces the code of an address each time, so a second request would void the
        // first code, which the user is about to be asked for.
        val viewModel = harness.signInViewModel()
        viewModel.onEmailChange(TrustedEmail)
        viewModel.onGetCodeClick()
        assertEquals(TrustedEmail, viewModel.uiState.value.codeSentTo)

        viewModel.onGetCodeClick()
        viewModel.onGetCodeClick()

        val state = viewModel.uiState.value
        assertEquals(1, harness.codeRequests)
        assertEquals(TrustedEmail, state.codeSentTo)
        assertNull(state.error)
        assertFalse(state.isSending)
    }

    @Test
    fun stateTextHidesTheAddress() {
        val viewModel = harness.signInViewModel()
        viewModel.onEmailChange("  Anna@HSE.ru ")
        assertFalse("nna@" in viewModel.uiState.value.toString())

        viewModel.onGetCodeClick()

        val state = viewModel.uiState.value
        assertEquals("anna@hse.ru", state.codeSentTo)
        val text = state.toString()
        assertFalse("anna" in text.lowercase(), text)
        assertFalse("hse.ru" in text.lowercase(), text)
        assertTrue("codeSent=true" in text, text)
    }

    @Test
    fun waitIsRoundedUpToWholeSeconds() {
        assertEquals(0, 0.seconds.wholeSecondsRoundedUp())
        assertEquals(1, 1.seconds.wholeSecondsRoundedUp())
        assertEquals(2, (1.seconds + 1.seconds / 10).wholeSecondsRoundedUp())
        assertEquals(60, 60.seconds.wholeSecondsRoundedUp())
    }
}
