package org.matchlab.academic_net.feature.responses.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectJoinType
import org.matchlab.academic_net.feature.responses.domain.ProjectJoinsRepository
import org.matchlab.academic_net.feature.responses.domain.ProjectResponses
import org.matchlab.academic_net.feature.responses.domain.ResponsesProjectSource

/**
 * 07d: `observeProject` and `observeProjectResponses` together; both known → the project with its applications (the
 * records of type Invitation are left out: the initiator's invitations are not shown there). `null` until both are known.
 */
class ObserveProjectResponsesUseCase(
    private val joins: ProjectJoinsRepository,
    private val projects: ResponsesProjectSource,
) {
    operator fun invoke(projectId: ProjectId): Flow<ProjectResponses?> =
        combine(projects.observeProject(projectId), joins.observeProjectResponses(projectId)) { project, responses ->
            if (project == null || responses == null) {
                null
            } else {
                ProjectResponses(project, responses.filter { it.join.type != ProjectJoinType.Invitation })
            }
        }
}
