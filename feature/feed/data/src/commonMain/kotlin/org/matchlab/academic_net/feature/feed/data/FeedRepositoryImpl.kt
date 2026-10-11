package org.matchlab.academic_net.feature.feed.data

import org.matchlab.academic_net.common.model.Collaborator
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.feature.feed.domain.FeedRepository

class FeedRepositoryImpl(
    private val dataSource: FeedDataSource,
) : FeedRepository {
    override suspend fun getViewer(): UserSummary = dataSource.getViewer()

    override suspend fun getRecommendedPeople(): List<Collaborator> = dataSource.getRecommendedPeople()
}
