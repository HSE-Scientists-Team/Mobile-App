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
}
