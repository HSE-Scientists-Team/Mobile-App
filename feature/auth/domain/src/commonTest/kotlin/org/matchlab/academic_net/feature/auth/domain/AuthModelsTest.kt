package org.matchlab.academic_net.feature.auth.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.seconds

class AuthModelsTest {
    @Test
    fun tokenIsHiddenInToString() {
        assertFalse("secret-token" in AccessToken("secret-token").toString())
    }

    @Test
    fun sessionHidesTokenAndEmailInToString() {
        val text = TestSession.toString()

        assertFalse("secret-token" in text)
        assertFalse("student@hse.ru" in text)
    }

    @Test
    fun challengeAndOutcomesHideTheAddress() {
        val challenge = VerificationChallenge("student@hse.ru", 60.seconds)

        assertFalse("student@hse.ru" in challenge.toString())
        assertFalse("student@hse.ru" in CodeRequestOutcome.Sent(challenge).toString())
        assertFalse("secret-token" in CodeVerificationOutcome.Verified(TestSession).toString())
        assertFalse("secret-token" in AuthState.SignedIn(TestSession).toString())
    }
}
