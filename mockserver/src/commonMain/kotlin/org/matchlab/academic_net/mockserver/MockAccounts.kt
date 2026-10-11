package org.matchlab.academic_net.mockserver

import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary

/**
 * The accounts of the mock server: the one that every sign-in lands on.
 *
 * The mock has no real accounts, so whatever e-mail address passes the sign-in is served as
 * [viewer]: the feed, the projects and the chats are all "theirs". The profile tables call the same
 * `user-me` by their full name ([MockPeople]); "Вы" is how the other mocks name the viewer.
 */
object MockAccounts {
    /** The signed-in user of the mock backend, whichever address they signed in with. */
    val viewer: UserSummary = UserSummary(UserId("user-me"), "Вы", isVerified = true)
}
