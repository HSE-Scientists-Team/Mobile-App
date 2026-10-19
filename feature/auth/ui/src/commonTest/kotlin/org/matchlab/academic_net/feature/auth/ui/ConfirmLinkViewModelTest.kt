package org.matchlab.academic_net.feature.auth.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.feature.auth.domain.AuthState

/** The address is confirmed with the link of the letter that opened the app. */
@OptIn(ExperimentalCoroutinesApi::class)
class ConfirmLinkViewModelTest : AuthViewModelTest() {
    private val ConfirmLinkViewModel.state get() = uiState.value

    @Test
    fun theRequestGoesAtOnceAndAnAppThatRemembersThePasswordIsSignedIn() = runTest {
        harness.registerAndWait()

        val viewModel = harness.confirmLinkViewModel()

        val signedIn = assertIs<AuthState.SignedIn>(harness.authState)
        assertEquals(TrustedEmail, signedIn.session.email)
        // The root leaves the screen when the session appears: nothing else is asked of the screen.
        assertFalse(viewModel.state.isConfirmed)
        assertTrue(viewModel.state.isRunning)
    }

    @Test
    fun anAppThatForgotThePasswordConfirmsTheAddressAndSendsTheUserToTheSignIn() = runTest {
        harness.registerOutsideTheApp()

        val viewModel = harness.confirmLinkViewModel()

        assertFalse(harness.authState is AuthState.SignedIn)
        assertTrue(viewModel.state.isConfirmed)
        assertFalse(viewModel.state.isRunning)
    }

    @Test
    fun aLinkTheServerDoesNotKnowIsAFinalProblem() = runTest {
        harness.registerAndWait()

        val viewModel = harness.confirmLinkViewModel(link = "https://matchlab.space/verify-email?token=unknown")

        assertEquals(AuthProblem.LinkInvalid, viewModel.state.problem)
        assertFalse(harness.authState is AuthState.SignedIn)
    }

    @Test
    fun aLinkThatWasUsedAlreadyIsInvalid() = runTest {
        harness.registerAndWait()
        harness.confirmLinkViewModel()

        val second = harness.confirmLinkViewModel()

        // The session of the first one is there, and the token cannot be spent twice.
        assertEquals(AuthProblem.LinkInvalid, second.state.problem)
    }

    @Test
    fun anExpiredLinkIsAFinalProblem() = runTest {
        harness.registerAndWait()
        harness.advance(31.minutes)

        val viewModel = harness.confirmLinkViewModel()

        assertEquals(AuthProblem.LinkExpired, viewModel.state.problem)
    }

    @Test
    fun aFailedCallCanBeRepeatedAndThenGoesThrough() = runTest {
        harness.registerAndWait()
        harness.source.offline = true
        val viewModel = harness.confirmLinkViewModel()

        assertIs<AuthProblem.CallFailed>(viewModel.state.problem)
        assertFalse(harness.authState is AuthState.SignedIn)

        harness.source.offline = false
        viewModel.onRetry()

        assertIs<AuthState.SignedIn>(harness.authState)
    }

    @Test
    fun aFinalProblemCannotBeRepeated() = runTest {
        harness.registerAndWait()
        val viewModel = harness.confirmLinkViewModel(link = "https://matchlab.space/verify-email?token=unknown")

        viewModel.onRetry()

        assertEquals(AuthProblem.LinkInvalid, viewModel.state.problem)
    }

    @Test
    fun aRetryAfterTheAddressWasConfirmedDoesNothing() = runTest {
        harness.registerOutsideTheApp()
        val viewModel = harness.confirmLinkViewModel()

        viewModel.onRetry()

        assertTrue(viewModel.state.isConfirmed)
    }
}
