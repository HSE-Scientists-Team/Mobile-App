package org.matchlab.academic_net.feature.feed.domain

import kotlinx.coroutines.flow.Flow

/**
 * Tells the feed how the signed-in user is called, for the initials in the avatar of its header. The name belongs to the
 * profile feature, the two features do not know each other, so an adapter in the app connects them.
 */
interface ViewerNameSource {
    /**
     * The name of the viewer as their profile has it, now and after every change of it; empty while it is not known (not
     * read yet, or an account with no profile). A failure to read it ends the flow with that failure.
     */
    fun observeName(): Flow<String>
}
