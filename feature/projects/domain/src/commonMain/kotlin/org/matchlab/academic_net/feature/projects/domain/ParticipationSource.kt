package org.matchlab.academic_net.feature.projects.domain

import org.matchlab.academic_net.common.model.ProjectId

/**
 * The projects the viewer joined, as the responses know them. The projects feature does not depend on the responses
 * feature, so the app implements this port over it (an adapter in `:shared`). API-GAP G67: the API has no list of the
 * projects a person takes part in, so they are found from the viewer's own records.
 */
fun interface ParticipationSource {
    /**
     * Ids of the projects of the viewer's records with status Accepted or `null` (unknown), each once, in their order.
     * Reads afresh; throws a failure.
     */
    suspend fun joinedProjectIds(): List<ProjectId>
}
