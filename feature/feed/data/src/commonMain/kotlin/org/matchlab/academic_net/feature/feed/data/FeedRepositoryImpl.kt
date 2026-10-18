package org.matchlab.academic_net.feature.feed.data

import org.matchlab.academic_net.common.model.Collaborator
import org.matchlab.academic_net.feature.feed.domain.FeedRepository

/** [FeedRepository] over a [FeedDataSource]: the people are the source's. */
class FeedRepositoryImpl(
    private val dataSource: FeedDataSource,
) : FeedRepository {
    override suspend fun getRecommendedPeople(): List<Collaborator> = dataSource.getRecommendedPeople()
}
