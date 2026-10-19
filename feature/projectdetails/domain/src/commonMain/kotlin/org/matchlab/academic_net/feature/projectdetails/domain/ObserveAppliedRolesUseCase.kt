package org.matchlab.academic_net.feature.projectdetails.domain

import kotlinx.coroutines.flow.Flow
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.RoleId

/** The roles of the project the viewer has applied to (04x "Вы откликнулись"); follows the change. */
class ObserveAppliedRolesUseCase(private val source: AppliedRolesSource) {
    operator fun invoke(projectId: ProjectId): Flow<Set<RoleId>> = source.observeAppliedRoles(projectId)
}
