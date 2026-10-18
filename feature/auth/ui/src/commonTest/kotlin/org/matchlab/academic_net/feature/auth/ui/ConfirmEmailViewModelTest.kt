package org.matchlab.academic_net.feature.auth.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.feature.auth.data.ConfirmReply
import org.matchlab.academic_net.feature.auth.data.RegisterReply
import org.matchlab.academic_net.feature.auth.data.SignInReply
import org.matchlab.academic_net.feature.auth.domain.AuthState

@OptIn(ExperimentalCoroutinesApi::class)
class ConfirmEmailViewModelTest : AuthViewModelTest() {
    private val ConfirmEmailViewModel.state get() = uiState.value

    /** Lets the virtual time of the coroutines pass, including the tasks due exactly at its end. */
    private fun TestScope.passSeconds(seconds: Int) {
        advanceTimeBy(seconds * 1000L)
        runCurrent()
    }

    /** Lets [seconds] pass for the clock of the screen and for that of the server and the repository. */
    private fun TestScope.wait(seconds: Int) {
        harness.advance(seconds.seconds)
        passSeconds(seconds)
    }

    private fun assertSignedInAs(email: String) {
        val signedIn = assertIs<AuthState.SignedIn>(harness.authState)
        assertEquals(email, signedIn.session.email)
    }

    // The pause before the next letter

    @Test
    fun startsWithTheWaitTheRegistrationHas() = runTest {
        harness.registerAndWait()

        val viewModel = harness.confirmViewModel()

        assertEquals(60, viewModel.state.resendInSeconds)
        assertEquals("", viewModel.state.link)
        assertNull(viewModel.state.running)
    }

    @Test
    fun countdownGoesDownEverySecondUntilResendIsAllowed() = runTest {
        harness.registerAndWait()
        val viewModel = harness.confirmViewModel()

        passSeconds(1)
        assertEquals(59, viewModel.state.resendInSeconds)

        passSeconds(58)
        assertEquals(1, viewModel.state.resendInSeconds)

        passSeconds(1)
        assertEquals(0, viewModel.state.resendInSeconds)

        // And stays there.
        passSeconds(5)
        assertEquals(0, viewModel.state.resendInSeconds)
    }

    @Test
    fun aScreenOpenedLaterShowsTheTimeThatIsReallyLeft() = runTest {
        harness.registerAndWait()
        harness.advance(45.seconds)

        val viewModel = harness.confirmViewModel()

        assertEquals(15, viewModel.state.resendInSeconds)
    }

    @Test
    fun aPartOfASecondLeftIsRoundedUp() = runTest {
        harness.registerAndWait()
        harness.advance(59.seconds + 500.milliseconds)

        val viewModel = harness.confirmViewModel()

        assertEquals(1, viewModel.state.resendInSeconds)
    }

    @Test
    fun aWaitThatIsOverIsNoWait() = runTest {
        harness.registerAndWait()
        harness.advance(61.seconds)

        assertEquals(0, harness.confirmViewModel().state.resendInSeconds)
    }

    @Test
    fun noKnownRegistrationMeansNoWait() {
        assertEquals(0, harness.confirmViewModel().state.resendInSeconds)
    }

    // "Я подтвердил(а) почту"

    @Test
    fun signsInOnceTheLinkWasOpenedOutsideTheApp() = runTest {
        harness.registerAndWait()
        harness.openLinkInBrowser()
        val viewModel = harness.confirmViewModel()

        viewModel.onSignedInClick()

        assertSignedInAs(TrustedEmail)
        // The root swaps the screens by the auth state; until it does the screen takes no more actions.
        assertEquals(ConfirmAction.SignIn, viewModel.state.running)
        assertNull(viewModel.state.submitProblem)
        assertNull(viewModel.state.exit)
    }

    @Test
    fun saysThatTheLinkWasNotOpenedYet() = runTest {
        harness.registerAndWait()
        val viewModel = harness.confirmViewModel()

        viewModel.onSignedInClick()

        assertEquals(AuthProblem.NotConfirmedYet, viewModel.state.submitProblem)
        assertNull(viewModel.state.running)
        assertNull(viewModel.state.exit)
        assertFalse(harness.authState is AuthState.SignedIn)
    }

    @Test
    fun tryingAgainAfterTheLinkWasOpenedSignsIn() = runTest {
        harness.registerAndWait()
        val viewModel = harness.confirmViewModel()
        viewModel.onSignedInClick()
        assertEquals(AuthProblem.NotConfirmedYet, viewModel.state.submitProblem)

        harness.openLinkInBrowser()
        viewModel.onSignedInClick()

        assertSignedInAs(TrustedEmail)
        assertNull(viewModel.state.submitProblem)
    }

    @Test
    fun withoutARememberedPasswordTheUserIsSentToSignInWithoutBeingToldThatTheAddressIsConfirmed() {
        val viewModel = harness.confirmViewModel()

        viewModel.onSignedInClick()

        assertEquals(ConfirmExit.SignIn(emailConfirmed = false), viewModel.state.exit)
        assertNull(viewModel.state.submitProblem)
        assertEquals(0, harness.source.signIns)
    }

    @Test
    fun aRefusedAccountIsTold() = runTest {
        harness.registerAndWait()
        harness.source.signInReply = SignInReply.Forbidden
        val viewModel = harness.confirmViewModel()

        viewModel.onSignedInClick()

        assertEquals(AuthProblem.AccountUnavailable, viewModel.state.submitProblem)
        assertNull(viewModel.state.running)
    }

    @Test
    fun lostConnectionIsToldUnderTheButtonAndTheNextTryWorks() = runTest {
        harness.registerAndWait()
        harness.openLinkInBrowser()
        harness.source.offline = true
        val viewModel = harness.confirmViewModel()

        viewModel.onSignedInClick()

        val problem = assertIs<AuthProblem.CallFailed>(viewModel.state.submitProblem)
        assertIs<RemoteFailure.NoConnection>(problem.cause)
        assertNull(viewModel.state.running)

        harness.source.offline = false
        viewModel.onSignedInClick()
        assertSignedInAs(TrustedEmail)
    }

    @Test
    fun aSecondTapWhileTheRequestIsOnItsWayAsksTheServerOnce() = runTest {
        harness.registerAndWait()
        harness.openLinkInBrowser()
        val viewModel = harness.confirmViewModel()
        harness.source.hold()

        viewModel.onSignedInClick()
        assertEquals(ConfirmAction.SignIn, viewModel.state.running)
        viewModel.onSignedInClick()
        viewModel.onSignedInClick()
        assertEquals(1, harness.source.signIns)

        harness.source.release()
        assertSignedInAs(TrustedEmail)
        assertEquals(1, harness.source.signIns)
    }

    @Test
    fun aTapAfterTheSignInDoesNotAskForASecondSession() = runTest {
        harness.registerAndWait()
        harness.openLinkInBrowser()
        val viewModel = harness.confirmViewModel()
        viewModel.onSignedInClick()
        assertSignedInAs(TrustedEmail)

        viewModel.onSignedInClick()
        viewModel.onSignedInClick()

        assertEquals(1, harness.source.signIns)
    }

    // The link pasted into the field

    @Test
    fun typingInTheFieldKeepsTheTextAsPasted() {
        val viewModel = harness.confirmViewModel()

        viewModel.onLinkChange("  https://matchlab.space/confirm?token=abc  ")

        assertEquals("  https://matchlab.space/confirm?token=abc  ", viewModel.state.link)
    }

    @Test
    fun emptyFieldIsAnErrorThatNeverReachesTheServer() {
        val viewModel = harness.confirmViewModel()

        viewModel.onConfirmLinkClick()

        assertEquals(AuthProblem.LinkEmpty, viewModel.state.linkProblem)
        assertNull(viewModel.state.running)
        assertEquals(0, harness.source.confirms)
    }

    @Test
    fun blankFieldIsEmptyToo() {
        val viewModel = harness.confirmViewModel()
        viewModel.onLinkChange("   \n ")

        viewModel.onConfirmLinkClick()

        assertEquals(AuthProblem.LinkEmpty, viewModel.state.linkProblem)
        assertEquals(0, harness.source.confirms)
    }

    @Test
    fun theLinkOfTheLetterConfirmsTheAddressAndSignsInByItself() = runTest {
        harness.registerAndWait()
        val viewModel = harness.confirmViewModel()
        viewModel.onLinkChange("https://matchlab.space/confirm?token=123456")

        viewModel.onConfirmLinkClick()

        assertSignedInAs(TrustedEmail)
        assertEquals(ConfirmAction.ConfirmLink, viewModel.state.running)
        assertNull(viewModel.state.linkProblem)
        assertNull(viewModel.state.exit)
    }

    @Test
    fun theBareTokenWorksAsWellAsALink() = runTest {
        harness.registerAndWait()
        val viewModel = harness.confirmViewModel()
        viewModel.onLinkChange(" 123456 ")

        viewModel.onConfirmLinkClick()

        assertSignedInAs(TrustedEmail)
    }

    @Test
    fun aTokenTheServerDoesNotKnowIsAnInvalidLinkAndStaysInTheField() = runTest {
        harness.registerAndWait()
        val viewModel = harness.confirmViewModel()
        viewModel.onLinkChange("https://matchlab.space/confirm?token=654321")

        viewModel.onConfirmLinkClick()

        assertEquals(AuthProblem.LinkInvalid, viewModel.state.linkProblem)
        assertEquals("https://matchlab.space/confirm?token=654321", viewModel.state.link)
        assertNull(viewModel.state.running)
        assertFalse(harness.authState is AuthState.SignedIn)
    }

    @Test
    fun aSentenceIsNotATokenSoItIsAnInvalidLink() = runTest {
        harness.registerAndWait()
        val viewModel = harness.confirmViewModel()
        viewModel.onLinkChange("I could not find the link")

        viewModel.onConfirmLinkClick()

        assertEquals(AuthProblem.LinkInvalid, viewModel.state.linkProblem)
    }

    @Test
    fun aLinkThatWasUsedAlreadyIsAnInvalidLink() = runTest {
        harness.registerAndWait()
        harness.openLinkInBrowser()
        val viewModel = harness.confirmViewModel()
        viewModel.onLinkChange("123456")

        viewModel.onConfirmLinkClick()

        assertEquals(AuthProblem.LinkInvalid, viewModel.state.linkProblem)
    }

    @Test
    fun aLinkOlderThanThirtyMinutesHasExpired() = runTest {
        harness.registerAndWait()
        harness.advance(31.minutes)
        val viewModel = harness.confirmViewModel()
        viewModel.onLinkChange("123456")

        viewModel.onConfirmLinkClick()

        assertEquals(AuthProblem.LinkExpired, viewModel.state.linkProblem)
        assertNull(viewModel.state.running)
    }

    @Test
    fun anAddressThatCannotBeConfirmedAnyMoreIsToldUnderTheField() = runTest {
        harness.registerAndWait()
        harness.source.confirmReply = ConfirmReply.DomainNotAllowed
        val viewModel = harness.confirmViewModel()
        viewModel.onLinkChange("123456")

        viewModel.onConfirmLinkClick()

        assertEquals(AuthProblem.DomainNotAllowed, viewModel.state.linkProblem)
    }

    @Test
    fun anAddressThatWasConfirmedBeforeIsConfirmedAndSignedIn() = runTest {
        harness.registerAndWait()
        harness.openLinkInBrowser()
        // The server answers 409 to the second use of the link: the address is confirmed already.
        harness.source.confirmReply = ConfirmReply.AlreadyConfirmed
        val viewModel = harness.confirmViewModel()
        viewModel.onLinkChange("123456")

        viewModel.onConfirmLinkClick()

        assertSignedInAs(TrustedEmail)
        assertNull(viewModel.state.linkProblem)
    }

    @Test
    fun confirmedWithoutARememberedPasswordTheUserIsSentToSignInAndToldThatTheAddressIsConfirmed() = runTest {
        harness.registerOutsideTheApp()
        val viewModel = harness.confirmViewModel()
        viewModel.onLinkChange("123456")

        viewModel.onConfirmLinkClick()

        assertEquals(ConfirmExit.SignIn(emailConfirmed = true), viewModel.state.exit)
        assertFalse(harness.authState is AuthState.SignedIn)
        assertEquals(0, harness.source.signIns)
        assertNull(viewModel.state.linkProblem)
    }

    @Test
    fun lostConnectionIsToldUnderTheFieldAndTheLinkStaysForTheNextTry() = runTest {
        harness.registerAndWait()
        harness.source.offline = true
        val viewModel = harness.confirmViewModel()
        viewModel.onLinkChange("123456")

        viewModel.onConfirmLinkClick()

        val problem = assertIs<AuthProblem.CallFailed>(viewModel.state.linkProblem)
        assertIs<RemoteFailure.NoConnection>(problem.cause)
        assertEquals("123456", viewModel.state.link)
        assertNull(viewModel.state.running)

        harness.source.offline = false
        viewModel.onConfirmLinkClick()
        assertSignedInAs(TrustedEmail)
    }

    @Test
    fun aSecondTapOnTheLinkButtonAsksTheServerOnce() = runTest {
        harness.registerAndWait()
        val viewModel = harness.confirmViewModel()
        viewModel.onLinkChange("123456")
        harness.source.hold()

        viewModel.onConfirmLinkClick()
        viewModel.onConfirmLinkClick()
        viewModel.onConfirmLinkClick()
        assertEquals(1, harness.source.confirms)

        harness.source.release()
        assertSignedInAs(TrustedEmail)
        viewModel.onConfirmLinkClick()
        assertEquals(1, harness.source.confirms)
    }

    @Test
    fun typingInTheFieldTakesAwayTheProblemOfTheLink() = runTest {
        harness.registerAndWait()
        val viewModel = harness.confirmViewModel()
        viewModel.onLinkChange("wrong")
        viewModel.onConfirmLinkClick()
        assertEquals(AuthProblem.LinkInvalid, viewModel.state.linkProblem)

        viewModel.onLinkChange("wrong again")

        assertNull(viewModel.state.linkProblem)
    }

    @Test
    fun typingInTheFieldKeepsWhatCameOfTheOtherButtons() = runTest {
        harness.registerAndWait()
        val viewModel = harness.confirmViewModel()
        viewModel.onSignedInClick()
        assertEquals(AuthProblem.NotConfirmedYet, viewModel.state.submitProblem)

        viewModel.onLinkChange("123456")

        assertEquals(AuthProblem.NotConfirmedYet, viewModel.state.submitProblem)
    }

    @Test
    fun everyActionClearsTheProblemsOfTheOthersWhenItStarts() = runTest {
        harness.registerAndWait()
        val viewModel = harness.confirmViewModel()
        viewModel.onSignedInClick()
        assertEquals(AuthProblem.NotConfirmedYet, viewModel.state.submitProblem)
        harness.source.hold()
        viewModel.onLinkChange("123456")

        viewModel.onConfirmLinkClick()

        assertNull(viewModel.state.submitProblem)
        assertNull(viewModel.state.linkProblem)
        assertEquals(ConfirmAction.ConfirmLink, viewModel.state.running)
        harness.source.release()
    }

    // "Отправить письмо ещё раз"

    @Test
    fun theLetterIsNotSentAgainBeforeTheWaitIsOver() = runTest {
        harness.registerAndWait()
        val viewModel = harness.confirmViewModel()

        viewModel.onResendClick()

        assertEquals(1, harness.source.registers)
        assertNull(viewModel.state.running)
        assertEquals(60, viewModel.state.resendInSeconds)
    }

    @Test
    fun afterTheWaitTheLetterIsSentAgainAndTheWaitStartsOver() = runTest {
        harness.registerAndWait()
        val viewModel = harness.confirmViewModel()
        wait(60)
        assertEquals(0, viewModel.state.resendInSeconds)

        viewModel.onResendClick()

        assertEquals(2, harness.source.registers)
        assertEquals(60, viewModel.state.resendInSeconds)
        assertNull(viewModel.state.running)
        assertNull(viewModel.state.submitProblem)
        assertNull(viewModel.state.exit)

        passSeconds(10)
        assertEquals(50, viewModel.state.resendInSeconds)
    }

    @Test
    fun theNewLetterCarriesTheRememberedPasswordSoTheAddressCanBeConfirmedAndSignedInWithIt() = runTest {
        harness.registerAndWait()
        val viewModel = harness.confirmViewModel()
        wait(60)
        viewModel.onResendClick()

        harness.openLinkInBrowser()
        viewModel.onSignedInClick()

        assertSignedInAs(TrustedEmail)
        assertEquals(TestPassword, harness.source.signInAttempts.single().password.value)
    }

    @Test
    fun withoutARememberedPasswordTheUserIsSentToTheRegistration() {
        val viewModel = harness.confirmViewModel()

        viewModel.onResendClick()

        assertEquals(ConfirmExit.Register, viewModel.state.exit)
        assertEquals(0, harness.source.registers)
    }

    @Test
    fun anAddressThatCannotBeRegisteredAnyMoreIsToldUnderTheButton() = runTest {
        harness.registerAndWait()
        val viewModel = harness.confirmViewModel()
        wait(60)
        harness.source.registerReply = RegisterReply.DomainNotAllowed

        viewModel.onResendClick()

        assertEquals(AuthProblem.DomainNotAllowed, viewModel.state.submitProblem)
        assertNull(viewModel.state.running)
        assertEquals(0, viewModel.state.resendInSeconds)
    }

    @Test
    fun anAddressConfirmedInTheMeantimeIsSignedInWithTheRememberedPassword() = runTest {
        harness.registerAndWait()
        val viewModel = harness.confirmViewModel()
        harness.openLinkInBrowser()
        wait(60)

        viewModel.onResendClick()

        assertSignedInAs(TrustedEmail)
        assertEquals(ConfirmAction.Resend, viewModel.state.running)
        assertNull(viewModel.state.exit)
    }

    @Test
    fun anAddressConfirmedInTheMeantimeWithAnotherPasswordIsSentToSignInAsConfirmed() = runTest {
        harness.registerAndWait(password = TestPassword)
        val viewModel = harness.confirmViewModel()
        // Meanwhile the address was registered again elsewhere with another password, and confirmed.
        harness.createAccount(password = OtherPassword)
        wait(60)

        viewModel.onResendClick()

        assertEquals(ConfirmExit.SignIn(emailConfirmed = true), viewModel.state.exit)
        assertFalse(harness.authState is AuthState.SignedIn)
    }

    @Test
    fun lostConnectionOnTheLetterIsToldUnderTheButtonAndTheWaitStaysOver() = runTest {
        harness.registerAndWait()
        val viewModel = harness.confirmViewModel()
        wait(60)
        harness.source.offline = true

        viewModel.onResendClick()

        val problem = assertIs<AuthProblem.CallFailed>(viewModel.state.submitProblem)
        assertIs<RemoteFailure.NoConnection>(problem.cause)
        assertNull(viewModel.state.running)
        assertEquals(0, viewModel.state.resendInSeconds)

        harness.source.offline = false
        viewModel.onResendClick()
        assertEquals(60, viewModel.state.resendInSeconds)
        assertNull(viewModel.state.submitProblem)
    }

    @Test
    fun theMailServiceBeingDownIsToldInWordsOfItsOwn() = runTest {
        harness.registerAndWait()
        val viewModel = harness.confirmViewModel()
        wait(60)
        harness.source.failure = RemoteFailure.Http(503, "smtp is down")

        viewModel.onResendClick()

        assertEquals(AuthProblem.LetterNotSent, viewModel.state.submitProblem)
    }

    @Test
    fun aFailureOfTheSignInThatFollowsTheLetterIsToldUnderTheButton() = runTest {
        harness.registerAndWait()
        val viewModel = harness.confirmViewModel()
        harness.openLinkInBrowser()
        wait(60)
        // The letter finds the account (the address was confirmed meanwhile); the sign-in that follows fails.
        val timeout = RemoteFailure.Timeout()
        harness.source.signInFailure = timeout

        viewModel.onResendClick()

        val problem = assertIs<AuthProblem.CallFailed>(viewModel.state.submitProblem)
        assertSame(timeout, problem.cause)
        assertNull(viewModel.state.running)
        assertNull(viewModel.state.exit)
    }

    @Test
    fun aSecondTapOnTheLetterButtonSendsOneLetter() = runTest {
        harness.registerAndWait()
        val viewModel = harness.confirmViewModel()
        wait(60)
        harness.source.hold()

        viewModel.onResendClick()
        assertEquals(ConfirmAction.Resend, viewModel.state.running)
        viewModel.onResendClick()
        viewModel.onResendClick()
        assertEquals(2, harness.source.registers)

        harness.source.release()
        assertEquals(2, harness.source.registers)
        assertEquals(60, viewModel.state.resendInSeconds)
    }

    // One action at a time

    @Test
    fun nothingElseHappensWhileAnActionIsOnItsWay() = runTest {
        harness.registerAndWait()
        val viewModel = harness.confirmViewModel()
        wait(60)
        viewModel.onLinkChange("123456")
        harness.source.hold()

        viewModel.onResendClick()
        viewModel.onSignedInClick()
        viewModel.onConfirmLinkClick()

        assertEquals(ConfirmAction.Resend, viewModel.state.running)
        assertEquals(2, harness.source.registers)
        assertEquals(0, harness.source.signIns)
        assertEquals(0, harness.source.confirms)
        harness.source.release()
    }

    @Test
    fun theLinkRequestBlocksTheOtherActionsToo() = runTest {
        harness.registerAndWait()
        val viewModel = harness.confirmViewModel()
        wait(60)
        viewModel.onLinkChange("654321")
        harness.source.hold()

        viewModel.onConfirmLinkClick()
        viewModel.onSignedInClick()
        viewModel.onResendClick()

        assertEquals(ConfirmAction.ConfirmLink, viewModel.state.running)
        assertEquals(1, harness.source.registers)
        assertEquals(0, harness.source.signIns)
        harness.source.release()
    }

    // Leaving the screen

    @Test
    fun theScreenStaysBusyWhileItLeavesAndOnlyTheEventIsPassedOn() {
        val viewModel = harness.confirmViewModel()
        viewModel.onSignedInClick()
        assertEquals(ConfirmExit.SignIn(emailConfirmed = false), viewModel.state.exit)

        viewModel.onExitHandled()

        assertNull(viewModel.state.exit)
        assertEquals(ConfirmAction.SignIn, viewModel.state.running)
        viewModel.onSignedInClick()
        viewModel.onResendClick()
        assertEquals(0, harness.source.signIns)
        assertEquals(0, harness.source.registers)
    }

    @Test
    fun theScreenTakesActionsAgainWhenTheUserComesBackToIt() {
        val viewModel = harness.confirmViewModel()
        viewModel.onResendClick()
        viewModel.onExitHandled()

        viewModel.onReturned()

        assertNull(viewModel.state.running)
        assertNull(viewModel.state.exit)
    }

    @Test
    fun comingBackDoesNotLoseTheEventThatWasNotPassedOnYet() {
        val viewModel = harness.confirmViewModel()
        viewModel.onResendClick()

        viewModel.onReturned()

        assertEquals(ConfirmExit.Register, viewModel.state.exit)
        assertEquals(ConfirmAction.Resend, viewModel.state.running)
    }

    @Test
    fun showingTheScreenForTheFirstTimeChangesNothing() = runTest {
        harness.registerAndWait()
        val viewModel = harness.confirmViewModel()
        val before = viewModel.state

        viewModel.onReturned()

        assertEquals(before, viewModel.state)
    }

    @Test
    fun showingTheScreenAgainWhileARequestIsOnItsWayDoesNotFreeIt() = runTest {
        harness.registerAndWait()
        val viewModel = harness.confirmViewModel()
        harness.source.hold()
        viewModel.onSignedInClick()

        viewModel.onReturned()

        assertEquals(ConfirmAction.SignIn, viewModel.state.running)
        harness.source.release()
    }

    @Test
    fun aScreenThatSignedTheUserInIsNeverFreedAgain() = runTest {
        harness.registerAndWait()
        harness.openLinkInBrowser()
        val viewModel = harness.confirmViewModel()
        viewModel.onSignedInClick()
        assertSignedInAs(TrustedEmail)

        viewModel.onReturned()

        assertEquals(ConfirmAction.SignIn, viewModel.state.running)
    }

    // What the state tells

    @Test
    fun stateTextHidesTheLinkAndTheAddress() = runTest {
        harness.registerAndWait()
        val viewModel = harness.confirmViewModel()
        viewModel.onLinkChange("https://matchlab.space/confirm?token=654321")
        viewModel.onConfirmLinkClick()

        val text = viewModel.state.toString()

        assertFalse("654321" in text, text)
        assertFalse("matchlab.space" in text, text)
        assertFalse(TrustedEmail in text, text)
        assertTrue("hasLink=true" in text, text)
        assertTrue("LinkInvalid" in text, text)
    }
}
