package org.matchlab.academic_net.feature.responses.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import org.matchlab.academic_net.common.model.ProjectJoinType
import org.matchlab.academic_net.feature.responses.domain.MyJoinsRepository
import org.matchlab.academic_net.feature.responses.domain.isWaitingForDecision

/**
 * The dot of the "Отклики" tab: an invitation (type Invitation, status Pending or `null`) with a `createdAt` later than
 * `observeInvitationsSeenAt` (or any, when nothing was seen). A record without a time is never new. Never fails.
 * API-GAP G61: there is no push, so the dot follows the list this session has read.
 */
class ObserveHasNewInvitationsUseCase(private val joins: MyJoinsRepository) {
    operator fun invoke(): Flow<Boolean> =
        combine(joins.observeMyJoins(), joins.observeInvitationsSeenAt()) { list, seenAt ->
            list.orEmpty().any { join ->
                val createdAt = join.createdAt
                join.type == ProjectJoinType.Invitation &&
                    join.isWaitingForDecision &&
                    createdAt != null &&
                    (seenAt == null || createdAt > seenAt)
            }
        }.distinctUntilChanged()
}
