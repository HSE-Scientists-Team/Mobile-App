package org.matchlab.academic_net.feature.auth.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.matchlab.academic_net.feature.auth.domain.AuthState

class SignInStartTest {
    @Test
    fun aUserWhoseAddressIsKnownStartsWithItInTheField() {
        val start = AuthState.SignedOut(sessionExpired = false, lastEmail = "anna@hse.ru").signInStart()

        assertEquals(SignInStart(email = "anna@hse.ru", sessionExpired = false), start)
    }

    @Test
    fun aUserWhoseAddressIsNotKnownStartsWithAnEmptyField() {
        assertEquals("", AuthState.SignedOut().signInStart().email)
        assertEquals("", AuthState.SignedOut(lastEmail = null).signInStart().email)
    }

    @Test
    fun aSessionThatEndedIsPassedOnSoThatTheScreenSaysSo() {
        val start = AuthState.SignedOut(sessionExpired = true, lastEmail = "anna@hse.ru").signInStart()

        assertTrue(start.sessionExpired)
        assertEquals("anna@hse.ru", start.email)
    }

    @Test
    fun aSessionThatEndedWithNoKnownAddressStillSaysSo() {
        val start = AuthState.SignedOut(sessionExpired = true).signInStart()

        assertTrue(start.sessionExpired)
        assertEquals("", start.email)
    }

    @Test
    fun aSignOutDoesNotSayThatTheSessionEnded() {
        assertFalse(AuthState.SignedOut(sessionExpired = false, lastEmail = "anna@hse.ru").signInStart().sessionExpired)
    }

    @Test
    fun theAddressIsNotShownInLogs() {
        val text = AuthState.SignedOut(lastEmail = "anna@hse.ru").signInStart().toString()

        assertFalse("anna" in text, text)
        assertFalse("hse.ru" in text, text)
        assertTrue("hasEmail=true" in text, text)
    }
}
