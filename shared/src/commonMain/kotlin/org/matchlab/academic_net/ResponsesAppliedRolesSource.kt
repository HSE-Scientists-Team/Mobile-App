package org.matchlab.academic_net

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import me.tatarka.inject.annotations.Inject
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectJoinStatus
import org.matchlab.academic_net.common.model.ProjectJoinType
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.feature.projectdetails.domain.AppliedRolesSource
import org.matchlab.academic_net.feature.responses.domain.MyJoinsRepository

/**
 * Tells the project page which roles the viewer has applied to ("Вы откликнулись"), from the viewer's own records of the
 * responses feature. The two features do not know each other: this adapter is the only place that connects them.
 */
@Inject
internal class ResponsesAppliedRolesSource(
    private val joins: MyJoinsRepository,
) : AppliedRolesSource {
    /** The roles of [projectId] with an application that waits (status Pending or unknown); empty while the list is unknown. */
    override fun observeAppliedRoles(projectId: ProjectId): Flow<Set<RoleId>> =
        joins.observeMyJoins()
            .map { list ->
                list.orEmpty()
                    .filter {
                        it.projectId == projectId &&
                            it.type == ProjectJoinType.Application &&
                            (it.status == ProjectJoinStatus.Pending || it.status == null)
                    }
                    .map { it.roleId }
                    .toSet()
            }
            .distinctUntilChanged()
}
