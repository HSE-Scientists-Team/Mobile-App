package org.matchlab.academic_net.feature.responses.domain.usecase

import org.matchlab.academic_net.common.model.ProjectJoin
import org.matchlab.academic_net.feature.responses.domain.MyJoinsRepository
import org.matchlab.academic_net.utils.suspendRunCatching

/** "Отозвать отклик": `withdraw`; a refusal reads the list again (best effort), since it showed what the server changed. */
class WithdrawApplicationUseCase(private val joins: MyJoinsRepository) {
    suspend operator fun invoke(join: ProjectJoin): JoinActionResult = joinAction(
        onRefused = { suspendRunCatching { joins.refreshMyJoins() } },
        action = { joins.withdraw(join.id) },
    )
}
