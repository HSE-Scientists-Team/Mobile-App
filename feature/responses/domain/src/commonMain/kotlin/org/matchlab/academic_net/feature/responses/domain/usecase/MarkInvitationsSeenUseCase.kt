package org.matchlab.academic_net.feature.responses.domain.usecase

import org.matchlab.academic_net.common.model.ProjectJoin
import org.matchlab.academic_net.common.model.ProjectJoinType
import org.matchlab.academic_net.feature.responses.domain.MyJoinsRepository

/** 15 was shown with [shown]: marks seen up to the newest `createdAt` of its invitations; none → nothing. */
class MarkInvitationsSeenUseCase(private val joins: MyJoinsRepository) {
    operator fun invoke(shown: List<ProjectJoin>) {
        val newest = shown.filter { it.type == ProjectJoinType.Invitation }.mapNotNull { it.createdAt }.maxOrNull() ?: return
        joins.markInvitationsSeen(newest)
    }
}
