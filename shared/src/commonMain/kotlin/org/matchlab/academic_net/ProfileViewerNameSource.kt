package org.matchlab.academic_net

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import me.tatarka.inject.annotations.Inject
import org.matchlab.academic_net.feature.auth.domain.Session
import org.matchlab.academic_net.feature.feed.domain.ViewerNameSource
import org.matchlab.academic_net.feature.profile.domain.MyProfileRepository

/**
 * Tells the feed the name of the signed-in user, from the viewer's own profile of the profile feature (the initials in the
 * avatar of the header). The two features do not know each other: this adapter is the only place that connects them.
 */
@Inject
internal class ProfileViewerNameSource(
    private val profile: MyProfileRepository,
    private val session: Session,
) : ViewerNameSource {
    override fun observeName(): Flow<String> {
        // A guest has no profile (reading it needs an account): the header draws the guest avatar, not initials.
        if (session.isGuest) return emptyFlow()
        return profile.observeMyProfile().map { it.profile.person.user.name }.distinctUntilChanged()
    }
}
