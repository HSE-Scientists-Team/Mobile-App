package org.matchlab.academic_net.feature.responses.domain.usecase

import org.matchlab.academic_net.common.model.ProjectJoin
import org.matchlab.academic_net.feature.responses.domain.MyJoinsRepository
import org.matchlab.academic_net.feature.responses.domain.ResponsesProjectSource
import org.matchlab.academic_net.utils.suspendRunCatching

/**
 * ✓/× of an invitation on 15: `decide`; any done reads the project again (with accept the viewer is on its team now, and its counters changed); a
 * refusal reads the list again (best effort).
 */
class DecideInvitationUseCase(
    private val joins: MyJoinsRepository,
    private val projects: ResponsesProjectSource,
) {
    suspend operator fun invoke(join: ProjectJoin, accept: Boolean): JoinActionResult {
        val result = joinAction(
            onRefused = { suspendRunCatching { joins.refreshMyJoins() } },
            action = { joins.decide(join.id, accept) },
        )
        if (result is JoinActionResult.Done) projects.refreshProject(join.projectId)
        return result
    }
}
