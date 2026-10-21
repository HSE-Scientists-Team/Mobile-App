package org.matchlab.academic_net.feature.feed.data

import org.matchlab.academic_net.common.model.Collaborator
import org.matchlab.academic_net.feature.feed.domain.FeedRepository
import org.matchlab.academic_net.feature.feed.domain.RecommendedProject

/** [FeedRepository] over a [FeedDataSource]: the people and the projects are the source's. */
class FeedRepositoryImpl(
    private val dataSource: FeedDataSource,
) : FeedRepository {
    override suspend fun getRecommendedPeople(): List<Collaborator> = dataSource.getRecommendedPeople()

    override suspend fun getRecommendedProjects(): List<RecommendedProject> = dataSource.getRecommendedProjects()
}
