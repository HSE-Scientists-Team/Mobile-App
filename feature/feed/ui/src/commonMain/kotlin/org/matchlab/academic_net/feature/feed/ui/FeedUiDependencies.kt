package org.matchlab.academic_net.feature.feed.ui

import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.feature.feed.domain.usecase.GetRecommendedPeopleUseCase
import org.matchlab.academic_net.feature.feed.domain.usecase.ObserveViewerNameUseCase

/**
 * What the feed screen needs from the outside; the app creates it once per session and hands it to [FeedRoute].
 *
 * @property viewerId the signed-in user: the header asks for their picture by the id
 * @property observeViewerName their name, for the initials the header shows until the picture is there
 * @property getRecommendedPeople the people block of "01c"
 */
class FeedUiDependencies(
    val viewerId: UserId,
    val observeViewerName: ObserveViewerNameUseCase,
    val getRecommendedPeople: GetRecommendedPeopleUseCase,
)
