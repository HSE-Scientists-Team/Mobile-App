package org.matchlab.academic_net.feature.responses.domain.usecase

import org.matchlab.academic_net.feature.responses.domain.MyJoinsRepository
import org.matchlab.academic_net.utils.suspendRunCatching

/** Reads the viewer's list again (15 on each entry, "Повторить"); a cancellation passes through. */
class RefreshMyResponsesUseCase(private val joins: MyJoinsRepository) {
    suspend operator fun invoke(): Result<Unit> = suspendRunCatching { joins.refreshMyJoins() }.map { }
}
