package org.matchlab.academic_net.mockserver

import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary

/**
 * The accounts of the mock server: the one that every sign-in lands on.
 *
 * The mock has no real accounts, so whatever e-mail address passes the mock sign-in lands on [viewer].
 */
object MockAccounts {
    /** The signed-in user of the mock backend, whichever address they signed in with. */
    val viewer: UserSummary = UserSummary(UserId("user-me"), "Вы", isVerified = true)
}
