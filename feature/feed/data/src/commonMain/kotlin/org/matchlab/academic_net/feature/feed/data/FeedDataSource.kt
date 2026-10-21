package org.matchlab.academic_net.feature.feed.data

import org.matchlab.academic_net.common.model.Collaborator
import org.matchlab.academic_net.feature.feed.domain.FeedRepository
import org.matchlab.academic_net.feature.feed.domain.RecommendedProject

/**
 * Where the feed's content comes from: the Gateway ([GatewayFeedDataSource]); see [FeedRepository]. The server chooses
 * the recommendations, so a source only asks and translates the answer.
 */
interface FeedDataSource {
    /** The people the server recommends, in its order, without the viewer and without repeats. */
    suspend fun getRecommendedPeople(): List<Collaborator>

    /** The projects the server recommends, in its order, without repeats; see [FeedRepository.getRecommendedProjects]. */
    suspend fun getRecommendedProjects(): List<RecommendedProject>
}
