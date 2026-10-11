package org.matchlab.academic_net.feature.feed.ui

import org.matchlab.academic_net.feature.feed.domain.usecase.GetRecommendedPeopleUseCase
import org.matchlab.academic_net.feature.feed.domain.usecase.GetViewerUseCase

/**
 * What the feed screen needs from the outside; the app creates it once and hands it to
 * [FeedRoute].
 */
class FeedUiDependencies(
    val getViewer: GetViewerUseCase,
    val getRecommendedPeople: GetRecommendedPeopleUseCase,
)
