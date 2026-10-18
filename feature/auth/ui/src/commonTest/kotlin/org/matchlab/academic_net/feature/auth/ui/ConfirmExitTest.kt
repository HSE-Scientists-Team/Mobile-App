package org.matchlab.academic_net.feature.auth.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class ConfirmExitTest {
    /** What the three callbacks of the screen were called with, in order, as "name:address". */
    private fun calls(exit: ConfirmExit, email: String = "anna@hse.ru"): List<String> {
        val calls = mutableListOf<String>()
        exit.dispatch(
            email = email,
            onSignInRequired = { calls += "signIn:$it" },
            onEmailConfirmed = { calls += "confirmed:$it" },
            onRegisterAgain = { calls += "register:$it" },
        )
        return calls
    }

    @Test
    fun anExitToSignInWithoutKnowingWhetherTheAddressIsConfirmedCallsOnlySignInRequired() {
        assertEquals(listOf("signIn:anna@hse.ru"), calls(ConfirmExit.SignIn(emailConfirmed = false)))
    }

    @Test
    fun anExitToSignInOfAConfirmedAddressCallsOnlyEmailConfirmed() {
        assertEquals(listOf("confirmed:anna@hse.ru"), calls(ConfirmExit.SignIn(emailConfirmed = true)))
    }

    @Test
    fun anExitToTheRegistrationCallsOnlyRegisterAgain() {
        assertEquals(listOf("register:anna@hse.ru"), calls(ConfirmExit.Register))
    }

    @Test
    fun theAddressOfTheScreenIsPassedOnAsItIs() {
        assertEquals(listOf("signIn:Anna@HSE.ru"), calls(ConfirmExit.SignIn(emailConfirmed = false), "Anna@HSE.ru"))
    }
}
