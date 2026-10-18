package org.matchlab.academic_net.feature.feed.ui

import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.feature.feed.domain.usecase.GetRecommendedPeopleUseCase

/**
 * What the feed screen needs from the outside; the app creates it once per session and hands it to [FeedRoute].
 *
 * @property viewerId the signed-in user: the header draws their avatar by the id, without a request (API-GAP G18: no
 * avatars yet)
 * @property getRecommendedPeople the people block of "01c"
 */
class FeedUiDependencies(
    val viewerId: UserId,
    val getRecommendedPeople: GetRecommendedPeopleUseCase,
)
