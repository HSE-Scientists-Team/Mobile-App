package org.matchlab.academic_net.feature.feed.ui

import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.feature.feed.domain.usecase.GetRecommendedPeopleUseCase
import org.matchlab.academic_net.feature.feed.domain.usecase.GetRecommendedProjectsUseCase
import org.matchlab.academic_net.feature.feed.domain.usecase.ObserveViewerNameUseCase

/**
 * What the feed screen needs from the outside; the app creates it once per session and hands it to [FeedRoute].
 *
 * @property isGuest the visitor has not signed in: the header shows the guest avatar instead of the picture, and a tap on
 * it opens the sign-in (the screen only reports the tap, see `onProfileClick`)
 * @property viewerId the signed-in user: the header asks for their picture by the id
 * @property observeViewerName their name, for the initials the header shows until the picture is there
 * @property getRecommendedPeople the people block of "01c"
 * @property getRecommendedProjects the projects block of "01", the mode the feed opens in
 */
class FeedUiDependencies(
    val isGuest: Boolean,
    val viewerId: UserId,
    val observeViewerName: ObserveViewerNameUseCase,
    val getRecommendedPeople: GetRecommendedPeopleUseCase,
    val getRecommendedProjects: GetRecommendedProjectsUseCase,
)
