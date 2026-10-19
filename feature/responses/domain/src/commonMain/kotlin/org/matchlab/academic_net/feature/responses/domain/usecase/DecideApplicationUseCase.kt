package org.matchlab.academic_net.feature.responses.domain.usecase

import org.matchlab.academic_net.common.model.ProjectJoin
import org.matchlab.academic_net.feature.responses.domain.ProjectJoinsRepository
import org.matchlab.academic_net.feature.responses.domain.ResponsesProjectSource
import org.matchlab.academic_net.utils.suspendRunCatching

/**
 * ✓/× of 07d. With accept: the role of [ProjectJoin] in `getProject` that has no vacant place is `RoleFull` and nothing is
 * sent. Any Done → `projects.refreshProject` (occupancy of the roles for accept, `pendingJoinCount` for both); a refusal → `refreshProjectResponses`
 * (best effort), since the screen showed what the server has changed.
 */
class DecideApplicationUseCase(
    private val joins: ProjectJoinsRepository,
    private val projects: ResponsesProjectSource,
) {
    suspend operator fun invoke(join: ProjectJoin, accept: Boolean): JoinActionResult {
        if (accept) {
            val project = suspendRunCatching { projects.getProject(join.projectId) }
                .getOrElse { return JoinActionResult.Failure(it) }
            val role = project?.roles?.firstOrNull { it.id == join.roleId }
            if (role != null && !role.isVacant) return JoinActionResult.RoleFull
        }
        val result = joinAction(
            onRefused = { suspendRunCatching { joins.refreshProjectResponses(join.projectId) } },
            action = { joins.decide(join.id, accept) },
        )
        if (result is JoinActionResult.Done) projects.refreshProject(join.projectId)
        return result
    }
}
