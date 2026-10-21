package org.matchlab.academic_net.feature.auth.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class AuthModelsTest {
    @Test
    fun tokenIsHiddenInToString() {
        assertFalse("secret-token" in AccessToken("secret-token").toString())
    }

    @Test
    fun passwordIsHiddenInToString() {
        assertFalse("hunter2-hunter2" in Password("hunter2-hunter2").toString())
        assertFalse("hunter2-hunter2" in "${Password("hunter2-hunter2")}")
    }

    @Test
    fun credentialsHideTheAddressAndThePassword() {
        val text = Credentials("student@hse.ru", Password("hunter2-hunter2")).toString()

        assertFalse("hunter2-hunter2" in text)
        assertFalse("student@hse.ru" in text)
    }

    @Test
    fun credentialsAreComparedByValue() {
        val credentials = Credentials("student@hse.ru", Password("hunter2-hunter2"))

        assertEquals(credentials, Credentials("student@hse.ru", Password("hunter2-hunter2")))
        assertNotEquals(credentials, Credentials("student@hse.ru", Password("other-password")))
        assertEquals("hunter2-hunter2", credentials.password.value)
    }

    @Test
    fun sessionHidesTokenAndEmailInToString() {
        val text = TestSession.toString()

        assertFalse("secret-token" in text)
        assertFalse("student@hse.ru" in text)
    }

    @Test
    fun pendingRegistrationHidesTheAddress() {
        val pending = PendingRegistration("student@hse.ru", 60.seconds, hasPassword = true)

        assertFalse("student@hse.ru" in pending.toString())
        assertTrue("hasPassword=true" in pending.toString())
    }

    @Test
    fun outcomesAndStatesHideTheAddressTheTokenAndThePassword() {
        val pending = PendingRegistration("student@hse.ru", 60.seconds, hasPassword = true)
        val texts = listOf(
            RegistrationOutcome.LetterSent(pending),
            RegistrationOutcome.DomainNotAllowed,
            RegistrationOutcome.AlreadyRegistered,
            ConfirmationOutcome.Confirmed(signedIn = true),
            ConfirmationOutcome.InvalidLink,
            ConfirmationOutcome.LinkExpired,
            ConfirmationOutcome.DomainNotAllowed,
            SignInOutcome.SignedIn,
            SignInOutcome.WrongCredentials,
            SignInOutcome.NotConfirmedYet,
            SignInOutcome.AccountUnavailable,
            AuthState.SignedIn(TestSession),
            AuthState.SignedOut(sessionExpired = true, lastEmail = "student@hse.ru"),
            AuthState.Restoring,
        ).map { it.toString() }

        for (text in texts) {
            assertFalse("student@hse.ru" in text, text)
            assertFalse("secret-token" in text, text)
        }
    }

    @Test
    fun signedOutIsNotExpiredAndKnowsNoAddressByDefault() {
        assertEquals(AuthState.SignedOut(sessionExpired = false, lastEmail = null), AuthState.SignedOut())
        assertFalse(AuthState.SignedOut().sessionExpired)
        assertNull(AuthState.SignedOut().lastEmail)
        assertNotEquals<AuthState>(AuthState.SignedOut(), AuthState.SignedOut(sessionExpired = true))
    }

    @Test
    fun signedOutStatesAreComparedByTheAddressToo() {
        val state = AuthState.SignedOut(lastEmail = "student@hse.ru")

        assertEquals(AuthState.SignedOut(false, "student@hse.ru"), state)
        assertNotEquals<AuthState>(AuthState.SignedOut(), state)
        assertNotEquals<AuthState>(AuthState.SignedOut(lastEmail = "other@hse.ru"), state)
        assertEquals("student@hse.ru", state.lastEmail)
    }

    @Test
    fun signedOutHidesTheAddressButSaysWhetherThereIsOne() {
        val withAddress = AuthState.SignedOut(sessionExpired = true, lastEmail = "student@hse.ru").toString()

        assertFalse("student@hse.ru" in withAddress)
        assertTrue("sessionExpired=true" in withAddress)
        assertTrue("hasLastEmail=true" in withAddress)
        assertTrue("hasLastEmail=false" in AuthState.SignedOut().toString())
    }

    @Test
    fun signedInStatesAreComparedBySession() {
        assertEquals(AuthState.SignedIn(TestSession), AuthState.SignedIn(TestSession.copy()))
        assertNotEquals<AuthState>(AuthState.SignedIn(TestSession), AuthState.SignedOut())
    }
}
