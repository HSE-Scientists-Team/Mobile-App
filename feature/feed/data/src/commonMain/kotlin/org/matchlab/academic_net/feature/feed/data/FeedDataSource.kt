package org.matchlab.academic_net.feature.feed.data

import org.matchlab.academic_net.common.model.Collaborator
import org.matchlab.academic_net.common.model.UserSummary

/**
 * Where the feed's content comes from. The only implementation is
 * [org.matchlab.academic_net.feature.feed.data.mock.MockFeedDataSource]; a remote one (with DTOs
 * and their mappers) replaces it when the backend appears.
 */
interface FeedDataSource {
    suspend fun getViewer(): UserSummary

    suspend fun getRecommendedPeople(): List<Collaborator>
}
