package org.matchlab.academic_net.feature.feed.domain

import org.matchlab.academic_net.common.model.Collaborator
import org.matchlab.academic_net.common.model.UserSummary

/**
 * What the feed shows. The server chooses the recommendations; the calls throw on failure (e.g.
 * no network) and the use cases turn that into results.
 */
interface FeedRepository {
    /** The signed-in user: the avatar in the feed's header. */
    suspend fun getViewer(): UserSummary

    /**
     * "Люди для ваших проектов": people the server recommends for the vacant roles of the
     * viewer's projects, the best fit first.
     */
    suspend fun getRecommendedPeople(): List<Collaborator>
}
