package org.matchlab.academic_net.feature.auth.ui

import androidx.lifecycle.SavedStateHandle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.feature.auth.domain.CredentialsError

/** A grinning face: one character for the user, two chars of UTF-16, four bytes of UTF-8. */
private const val Emoji = "\uD83D\uDE00"

class RegisterViewModelTest : AuthViewModelTest() {
    private fun RegisterViewModel.fill(
        email: String = TrustedEmail,
        password: String = TestPassword,
        repeated: String = password,
    ) {
        onEmailChange(email)
        onPasswordChange(password)
        onRepeatedPasswordChange(repeated)
    }

    private val RegisterViewModel.state get() = uiState.value

    // The form

    @Test
    fun startsWithTheAddressItWasGivenAndNoPasswords() {
        val viewModel = harness.registerViewModel(initialEmail = TrustedEmail)

        assertEquals(RegisterUiState(email = TrustedEmail), viewModel.state)
    }

    @Test
    fun typingChangesTheTextAsTypedWithoutTrimmingAnything() {
        val viewModel = harness.registerViewModel()

        viewModel.fill(email = " Anna@HSE.ru", password = " pass word ", repeated = " pass word  ")

        assertEquals(" Anna@HSE.ru", viewModel.state.email)
        assertEquals(" pass word ", viewModel.state.password)
        assertEquals(" pass word  ", viewModel.state.repeatedPassword)
    }

    // Checks of the form: nothing of an invalid form reaches the server

    @Test
    fun emptyFormIsAnErrorForTheAddressAndOneForThePassword() {
        val viewModel = harness.registerViewModel()

        viewModel.onRegisterClick()

        // The two empty passwords are the same, so they do not differ.
        assertEquals(setOf(CredentialsError.EmailEmpty, CredentialsError.PasswordEmpty), viewModel.state.errors)
        assertFalse(viewModel.state.isSubmitting)
        assertNull(viewModel.state.letterSentTo)
        assertEquals(0, harness.source.registers)
    }

    @Test
    fun addressWithATypoNeverReachesTheServer() {
        val viewModel = harness.registerViewModel()
        viewModel.fill(email = "anna.hse.ru")

        viewModel.onRegisterClick()

        assertEquals(setOf(CredentialsError.EmailMalformed), viewModel.state.errors)
        assertEquals(0, harness.source.registers)
    }

    @Test
    fun passwordOfSevenBytesIsTooShortAndOneOfEightIsNot() = runTest {
        val short = harness.registerViewModel()
        short.fill(password = "1234567")
        short.onRegisterClick()
        assertEquals(setOf(CredentialsError.PasswordTooShort), short.state.errors)
        assertEquals(0, harness.source.registers)

        val enough = harness.registerViewModel()
        enough.fill(password = "12345678")
        enough.onRegisterClick()
        assertTrue(enough.state.errors.isEmpty())
        assertEquals(1, harness.source.registers)
    }

    @Test
    fun cyrillicLettersCountAsTwoBytesEach() {
        // "раз" is six bytes, "пара" is eight.
        val short = harness.registerViewModel()
        short.fill(password = "раз")
        short.onRegisterClick()
        assertEquals(setOf(CredentialsError.PasswordTooShort), short.state.errors)

        val enough = harness.registerViewModel()
        enough.fill(password = "пара")
        enough.onRegisterClick()
        assertTrue(enough.state.errors.isEmpty())
        assertNotNull(enough.state.letterSentTo)
    }

    @Test
    fun passwordOfSeventyTwoBytesIsTheLongestOne() {
        val longest = harness.registerViewModel()
        longest.fill(password = "a".repeat(72))
        longest.onRegisterClick()
        assertTrue(longest.state.errors.isEmpty())

        val tooLong = harness.registerViewModel()
        tooLong.fill(password = "a".repeat(73))
        tooLong.onRegisterClick()
        assertEquals(setOf(CredentialsError.PasswordTooLong), tooLong.state.errors)
    }

    @Test
    fun theLimitOfTheLengthIsInBytesNotInLetters() {
        // 36 Cyrillic letters are 72 bytes, 37 are 74: a count of letters that fits in Latin is too long in Cyrillic.
        val fits = harness.registerViewModel()
        fits.fill(password = "ж".repeat(36))
        fits.onRegisterClick()
        assertTrue(fits.state.errors.isEmpty())

        val tooLong = harness.registerViewModel()
        tooLong.fill(password = "ж".repeat(37))
        tooLong.onRegisterClick()
        assertEquals(setOf(CredentialsError.PasswordTooLong), tooLong.state.errors)

        // An emoji is four bytes (two chars of UTF-16): 18 of them are 72.
        val emojiFits = harness.registerViewModel()
        emojiFits.fill(password = Emoji.repeat(18))
        emojiFits.onRegisterClick()
        assertTrue(emojiFits.state.errors.isEmpty())

        val emojiTooLong = harness.registerViewModel()
        emojiTooLong.fill(password = Emoji.repeat(19))
        emojiTooLong.onRegisterClick()
        assertEquals(setOf(CredentialsError.PasswordTooLong), emojiTooLong.state.errors)
    }

    @Test
    fun passwordOfEightSpacesIsAPassword() {
        val viewModel = harness.registerViewModel()
        viewModel.fill(password = " ".repeat(8))

        viewModel.onRegisterClick()

        assertTrue(viewModel.state.errors.isEmpty())
        assertEquals(1, harness.source.registers)
    }

    @Test
    fun passwordsThatDifferAreAnErrorOfTheRepeatedField() {
        val viewModel = harness.registerViewModel()
        viewModel.fill(password = TestPassword, repeated = OtherPassword)

        viewModel.onRegisterClick()

        assertEquals(setOf(CredentialsError.PasswordsDiffer), viewModel.state.errors)
        assertEquals(0, harness.source.registers)
    }

    @Test
    fun repeatedPasswordThatDiffersOnlyInCaseDiffers() {
        val viewModel = harness.registerViewModel()
        viewModel.fill(password = "Password-1", repeated = "password-1")

        viewModel.onRegisterClick()

        assertEquals(setOf(CredentialsError.PasswordsDiffer), viewModel.state.errors)
    }

    @Test
    fun everyProblemOfTheFormIsToldAtOnce() {
        val viewModel = harness.registerViewModel()
        viewModel.fill(email = "nonsense", password = "short", repeated = "other")

        viewModel.onRegisterClick()

        assertEquals(
            setOf(CredentialsError.EmailMalformed, CredentialsError.PasswordTooShort, CredentialsError.PasswordsDiffer),
            viewModel.state.errors,
        )
        assertEquals(0, harness.source.registers)
    }

    @Test
    fun typingInAFieldTakesAwayItsOwnErrorsAndKeepsTheOthers() {
        val viewModel = harness.registerViewModel()
        viewModel.fill(email = "nonsense", password = "short", repeated = "other")
        viewModel.onRegisterClick()

        viewModel.onEmailChange(TrustedEmail)
        assertEquals(setOf(CredentialsError.PasswordTooShort, CredentialsError.PasswordsDiffer), viewModel.state.errors)

        viewModel.onRepeatedPasswordChange("short")
        assertEquals(setOf(CredentialsError.PasswordTooShort), viewModel.state.errors)

        viewModel.onPasswordChange(TestPassword)
        assertTrue(viewModel.state.errors.isEmpty())
    }

    @Test
    fun aNewPasswordTakesAwayTheErrorOfThePairToo() {
        // "Пароли не совпадают" is about the two together: once either is edited it is stale.
        val viewModel = harness.registerViewModel()
        viewModel.fill(password = TestPassword, repeated = OtherPassword)
        viewModel.onRegisterClick()
        assertEquals(setOf(CredentialsError.PasswordsDiffer), viewModel.state.errors)

        viewModel.onPasswordChange(OtherPassword)

        assertTrue(viewModel.state.errors.isEmpty())
    }

    // The letter

    @Test
    fun theLetterIsSentAndTheScreenLeavesWithTheAddressAsTheServerHasIt() {
        val viewModel = harness.registerViewModel()
        viewModel.fill(email = "  Anna@HSE.ru ")

        viewModel.onRegisterClick()

        assertEquals(TrustedEmail, viewModel.state.letterSentTo)
        assertEquals(1, harness.source.registers)
        assertNull(viewModel.state.problem)
        assertTrue(viewModel.state.errors.isEmpty())
    }

    @Test
    fun theRegistrationIsRememberedWithItsPasswordForTheNextScreen() {
        val viewModel = harness.registerViewModel()
        viewModel.fill()

        viewModel.onRegisterClick()

        val pending = harness.getPendingRegistration(TrustedEmail)
        assertNotNull(pending)
        assertTrue(pending.hasPassword)
    }

    @Test
    fun aSubdomainOfARegisteredDomainIsAccepted() {
        val viewModel = harness.registerViewModel()
        viewModel.fill(email = "anna@student.hse.ru")

        viewModel.onRegisterClick()

        assertEquals("anna@student.hse.ru", viewModel.state.letterSentTo)
    }

    @Test
    fun theFormStaysBusyAfterTheLetterWhileTheScreenIsOnItsWayAway() {
        val viewModel = harness.registerViewModel()
        viewModel.fill()
        viewModel.onRegisterClick()
        assertTrue(viewModel.state.isSubmitting)

        // The screen passed the event on: the confirmation slides in, the form is still on the display.
        viewModel.onLetterSentHandled()

        assertNull(viewModel.state.letterSentTo)
        assertTrue(viewModel.state.isSubmitting)
        viewModel.onRegisterClick()
        viewModel.onRegisterClick()
        assertEquals(1, harness.source.registers)
    }

    @Test
    fun aSecondTapWhileTheRequestIsOnItsWayAsksTheServerOnce() {
        val viewModel = harness.registerViewModel()
        viewModel.fill()
        harness.source.hold()

        viewModel.onRegisterClick()
        assertTrue(viewModel.state.isSubmitting)
        viewModel.onRegisterClick()
        viewModel.onRegisterClick()
        assertEquals(1, harness.source.registers)

        harness.source.release()
        assertEquals(TrustedEmail, viewModel.state.letterSentTo)
        assertEquals(1, harness.source.registers)
    }

    @Test
    fun theFormTakesRequestsAgainWhenTheUserComesBackFromTheConfirmation() {
        val viewModel = harness.registerViewModel()
        viewModel.fill()
        viewModel.onRegisterClick()
        viewModel.onLetterSentHandled()

        viewModel.onReturned()

        assertFalse(viewModel.state.isSubmitting)
        assertNull(viewModel.state.letterSentTo)
        // The form is as the user left it: they change the address and send another letter.
        assertEquals(TrustedEmail, viewModel.state.email)
        assertEquals(TestPassword, viewModel.state.password)
        assertEquals(TestPassword, viewModel.state.repeatedPassword)
        viewModel.onEmailChange("other@hse.ru")
        viewModel.onRegisterClick()
        assertEquals(2, harness.source.registers)
        assertEquals("other@hse.ru", viewModel.state.letterSentTo)
    }

    @Test
    fun comingBackDoesNotLoseTheEventThatWasNotPassedOnYet() {
        // After a rotation the screen is composed again and learns that it is resumed before it passes the event on.
        val viewModel = harness.registerViewModel()
        viewModel.fill()
        viewModel.onRegisterClick()

        viewModel.onReturned()

        assertEquals(TrustedEmail, viewModel.state.letterSentTo)
        assertTrue(viewModel.state.isSubmitting)
    }

    @Test
    fun showingTheScreenForTheFirstTimeChangesNothing() {
        val viewModel = harness.registerViewModel(initialEmail = TrustedEmail)
        val before = viewModel.state

        viewModel.onReturned()

        assertEquals(before, viewModel.state)
    }

    @Test
    fun showingTheScreenAgainWhileARequestIsOnItsWayDoesNotFreeTheForm() {
        val viewModel = harness.registerViewModel()
        viewModel.fill()
        harness.source.hold()
        viewModel.onRegisterClick()

        // Rotation: the screen is resumed again, the request is still on its way.
        viewModel.onReturned()
        viewModel.onRegisterClick()

        assertTrue(viewModel.state.isSubmitting)
        assertEquals(1, harness.source.registers)
        harness.source.release()
    }

    @Test
    fun theEventIsOneShotSoTheScreenDoesNotLeaveAgainWhenItIsComposedAgain() {
        val viewModel = harness.registerViewModel()
        viewModel.fill()
        viewModel.onRegisterClick()
        viewModel.onLetterSentHandled()

        // Coming back, the route reads the state again: there is nothing to pass on.
        viewModel.onReturned()

        assertNull(viewModel.state.letterSentTo)
    }

    // What the server answers

    @Test
    fun domainOfAnotherOrganizationIsRefusedUnderTheAddress() {
        val viewModel = harness.registerViewModel()
        viewModel.fill(email = UntrustedEmail)

        viewModel.onRegisterClick()

        assertEquals(AuthProblem.DomainNotAllowed, viewModel.state.problem)
        assertTrue(viewModel.state.problem!!.isAboutEmail)
        assertNull(viewModel.state.letterSentTo)
        assertFalse(viewModel.state.isSubmitting)
        // Nothing was remembered: no letter was sent.
        assertNull(harness.getPendingRegistration(UntrustedEmail))
    }

    @Test
    fun addressThatHasAnAccountIsToldAndTheUserIsOfferedToSignIn() = runTest {
        harness.createAccount()
        val viewModel = harness.registerViewModel()
        viewModel.fill()

        viewModel.onRegisterClick()

        assertEquals(AuthProblem.AlreadyRegistered, viewModel.state.problem)
        assertTrue(viewModel.state.problem!!.isAboutEmail)
        assertNull(viewModel.state.letterSentTo)
        assertFalse(viewModel.state.isSubmitting)
    }

    @Test
    fun lostConnectionIsToldAndTheFormStaysFilledForAnotherTry() {
        harness.source.offline = true
        val viewModel = harness.registerViewModel()
        viewModel.fill()

        viewModel.onRegisterClick()

        val problem = assertIs<AuthProblem.CallFailed>(viewModel.state.problem)
        assertIs<RemoteFailure.NoConnection>(problem.cause)
        assertFalse(problem.isAboutEmail)
        assertFalse(viewModel.state.isSubmitting)
        assertEquals(TestPassword, viewModel.state.password)
        assertEquals(TestPassword, viewModel.state.repeatedPassword)

        harness.source.offline = false
        viewModel.onRegisterClick()
        assertEquals(TrustedEmail, viewModel.state.letterSentTo)
    }

    @Test
    fun theMailServiceBeingDownIsToldInWordsOfItsOwn() {
        harness.source.failure = RemoteFailure.Http(503, "smtp is down")
        val viewModel = harness.registerViewModel()
        viewModel.fill()

        viewModel.onRegisterClick()

        assertEquals(AuthProblem.LetterNotSent, viewModel.state.problem)
        assertFalse(viewModel.state.problem!!.isAboutEmail)
        assertFalse(viewModel.state.isSubmitting)
    }

    @Test
    fun otherFailuresOfTheCallAreToldAsAnyFailedCallIs() {
        listOf(
            RemoteFailure.Timeout(),
            RemoteFailure.Http(500, null),
            RemoteFailure.Http(502, null),
            RemoteFailure.Http(400, "bad request"),
            RemoteFailure.Malformed(),
        ).forEach { cause ->
            harness.source.failure = cause
            val viewModel = harness.registerViewModel()
            viewModel.fill()

            viewModel.onRegisterClick()

            val problem = assertIs<AuthProblem.CallFailed>(viewModel.state.problem)
            assertSame(cause, problem.cause)
        }
    }

    @Test
    fun editingTheAddressTakesAwayEveryProblem() {
        val viewModel = harness.registerViewModel()
        viewModel.fill(email = UntrustedEmail)
        viewModel.onRegisterClick()
        assertEquals(AuthProblem.DomainNotAllowed, viewModel.state.problem)

        viewModel.onEmailChange(TrustedEmail)

        assertNull(viewModel.state.problem)
    }

    @Test
    fun editingAPasswordKeepsWhatIsTrueOfTheAddressAndTakesAwayWhatCameOfTheCall() {
        val refused = harness.registerViewModel()
        refused.fill(email = UntrustedEmail)
        refused.onRegisterClick()

        refused.onPasswordChange(OtherPassword)
        assertEquals(AuthProblem.DomainNotAllowed, refused.state.problem)
        refused.onRepeatedPasswordChange(OtherPassword)
        assertEquals(AuthProblem.DomainNotAllowed, refused.state.problem)

        harness.source.offline = true
        val failed = harness.registerViewModel()
        failed.fill()
        failed.onRegisterClick()
        assertIs<AuthProblem.CallFailed>(failed.state.problem)

        failed.onPasswordChange(OtherPassword)
        assertNull(failed.state.problem)
    }

    @Test
    fun problemsAreNotKeptForTheNextAttempt() {
        val viewModel = harness.registerViewModel()
        viewModel.fill(email = UntrustedEmail)
        viewModel.onRegisterClick()
        assertNotNull(viewModel.state.problem)
        harness.source.hold()

        viewModel.onRegisterClick()

        assertNull(viewModel.state.problem)
        assertTrue(viewModel.state.isSubmitting)
        harness.source.release()
    }

    // The address that comes from outside

    @Test
    fun anAddressSavedByAnEarlierIncarnationWinsOverTheGivenOne() {
        val saved = SavedStateHandle(mapOf("email" to "typed@hse.ru"))

        val viewModel = harness.registerViewModel(initialEmail = TrustedEmail, savedState = saved)

        assertEquals("typed@hse.ru", viewModel.state.email)
    }

    @Test
    fun aNewAddressFromOutsideReplacesTheTextAndIsSaved() {
        val saved = SavedStateHandle()
        val viewModel = harness.registerViewModel(initialEmail = "first@hse.ru", savedState = saved)

        viewModel.onEmailSuggested("second@hse.ru")

        assertEquals("second@hse.ru", viewModel.state.email)
        assertEquals("second@hse.ru", saved.get<String>("email"))
    }

    @Test
    fun theSameAddressGivenAgainOrAnEmptyOneLeavesWhatTheUserTypedAlone() {
        val viewModel = harness.registerViewModel(initialEmail = "first@hse.ru")
        viewModel.onEmailChange("typed@hse.ru")

        viewModel.onEmailSuggested("first@hse.ru")
        viewModel.onEmailSuggested("")

        assertEquals("typed@hse.ru", viewModel.state.email)
    }

    // What is saved

    @Test
    fun onlyTheAddressIsEverSaved() {
        val saved = SavedStateHandle()
        val viewModel = harness.registerViewModel(savedState = saved)

        viewModel.fill(password = TestPassword, repeated = TestPassword)
        viewModel.onRegisterClick()
        viewModel.onPasswordChange(OtherPassword)
        viewModel.onRepeatedPasswordChange(OtherPassword)

        assertEquals(setOf("email"), saved.keys())
        assertEquals(TrustedEmail, saved.get<String>("email"))
        saved.keys().forEach { key ->
            val value = saved.get<Any?>(key).toString()
            assertFalse(TestPassword in value, "a password is in the saved state under $key")
            assertFalse(OtherPassword in value, "a password is in the saved state under $key")
        }
    }

    @Test
    fun afterTheScreenIsMadeAgainTheAddressIsBackAndThePasswordsAreNot() {
        val saved = SavedStateHandle()
        harness.registerViewModel(initialEmail = "given@hse.ru", savedState = saved).fill()

        val restored = harness.registerViewModel(initialEmail = "given@hse.ru", savedState = saved)

        assertEquals(TrustedEmail, restored.state.email)
        assertEquals("", restored.state.password)
        assertEquals("", restored.state.repeatedPassword)
    }

    // What the state tells

    @Test
    fun stateTextHidesTheAddressAndBothPasswords() {
        val viewModel = harness.registerViewModel()
        viewModel.fill(password = TestPassword, repeated = OtherPassword)
        viewModel.onRegisterClick()

        val text = viewModel.state.toString()

        assertFalse(TrustedEmail in text, text)
        assertFalse("anna" in text.lowercase(), text)
        assertFalse(TestPassword in text, text)
        assertFalse(OtherPassword in text, text)
        assertTrue("hasEmail=true" in text, text)
    }

    @Test
    fun stateTextOfTheSentLetterDoesNotHoldTheAddress() {
        val viewModel = harness.registerViewModel()
        viewModel.fill()
        viewModel.onRegisterClick()

        val text = viewModel.state.toString()

        assertEquals(TrustedEmail, viewModel.state.letterSentTo)
        assertFalse("hse.ru" in text, text)
        assertTrue("letterSent=true" in text, text)
    }
}
