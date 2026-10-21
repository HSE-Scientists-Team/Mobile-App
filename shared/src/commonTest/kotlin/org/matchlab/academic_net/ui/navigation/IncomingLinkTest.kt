package org.matchlab.academic_net.ui.navigation

import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.feature.auth.domain.AccessToken
import org.matchlab.academic_net.feature.auth.domain.AuthState
import org.matchlab.academic_net.feature.auth.domain.Session
import kotlin.test.Test
import kotlin.test.assertEquals

class IncomingLinkTest {
    private val letter = "https://matchlab.space/verify-email?token=pV1TQj5pu9TGwjQfh2T_THQyYN-Cf_x9lq5uNgX3OwQ"
    private val signedOut = AuthState.SignedOut(lastEmail = "a@edu.ru")
    private val signedIn = AuthState.SignedIn(Session(userId = UserId("u"), email = "a@edu.ru", accessToken = AccessToken("t")))

    @Test
    fun theLinkOfTheLetterIsConfirmedByAUserWhoIsSignedOut() {
        assertEquals(IncomingLinkAction.Confirm(letter), incomingLinkAction(letter, signedOut))
    }

    @Test
    fun theLinkWaitsWhileTheSavedSessionIsRead() {
        assertEquals(IncomingLinkAction.Wait, incomingLinkAction(letter, AuthState.Restoring))
    }

    @Test
    fun aSignedInUserHasNothingToConfirm() {
        assertEquals(IncomingLinkAction.Ignore, incomingLinkAction(letter, signedIn))
    }

    @Test
    fun aLinkThatIsNotOfTheLetterIsDroppedInAnyState() {
        for (link in listOf("https://matchlab.space/", "https://evil.example/verify-email?token=x", "http://matchlab.space/verify-email?token=x", "")) {
            for (state in listOf(AuthState.Restoring, signedOut, signedIn)) {
                assertEquals(IncomingLinkAction.Ignore, incomingLinkAction(link, state), "$link in $state")
            }
        }
    }
}
