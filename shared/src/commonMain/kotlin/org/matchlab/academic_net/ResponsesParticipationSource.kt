package org.matchlab.academic_net

import me.tatarka.inject.annotations.Inject
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectJoinStatus
import org.matchlab.academic_net.feature.projects.domain.ParticipationSource
import org.matchlab.academic_net.feature.responses.domain.MyJoinsRepository

/**
 * Tells the projects feature which projects the viewer joined (06b "Я участник", API-GAP G67), from the viewer's own
 * records of the responses feature. The two features do not know each other: this adapter is the only place that
 * connects them.
 */
@Inject
internal class ResponsesParticipationSource(
    private val joins: MyJoinsRepository,
) : ParticipationSource {
    /**
     * Reads the list afresh, so that a decision made elsewhere is seen; the records that are accepted or whose status is
     * unknown (`null`: a record is never hidden for a code the app does not know) name the projects, each once.
     */
    override suspend fun joinedProjectIds(): List<ProjectId> =
        joins.refreshMyJoins()
            .filter { it.status == ProjectJoinStatus.Accepted || it.status == null }
            .map { it.projectId }
            .distinct()
}
