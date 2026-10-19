package org.matchlab.academic_net.feature.projectdetails.domain

import kotlinx.coroutines.flow.Flow
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.RoleId

/**
 * Which roles of a project the viewer has applied to (records of type Application, status Pending or `null`). The
 * project page does not depend on the responses feature, so the app implements this port over it (an adapter in
 * `:shared`); the page then shows "Вы откликнулись" without a link through the navigation.
 */
fun interface AppliedRolesSource {
    /** Follows the viewer's records as the session knows them; empty while unknown. Never fails. */
    fun observeAppliedRoles(projectId: ProjectId): Flow<Set<RoleId>>
}
