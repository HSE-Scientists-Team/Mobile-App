package org.matchlab.academic_net.feature.feed.domain

import org.matchlab.academic_net.common.model.Collaborator

/**
 * What the feed shows. The server chooses the recommendations; the call throws on failure (no network, a server error)
 * and the use case turns that into a result.
 */
interface FeedRepository {
    /**
     * "Люди для ваших проектов": the people the server recommends, in its order, without the viewer and without
     * repeats. API-GAP G32: the server picks random people open to collaboration, not people for the viewer's projects.
     */
    suspend fun getRecommendedPeople(): List<Collaborator>

    /**
     * "Рекомендации для вас": the projects the server recommends, in its order, without repeats. The projects the app cannot
     * read (no id, no initiator) are left out; the server alone decides which projects are offered, nothing is filtered by
     * status or by owner on the client (the viewer's own project is shown as such).
     */
    suspend fun getRecommendedProjects(): List<RecommendedProject>
}
