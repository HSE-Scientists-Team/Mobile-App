package org.matchlab.academic_net.feature.complaints.domain

import org.matchlab.academic_net.common.result.SubmitResult
import org.matchlab.academic_net.utils.suspendRunCatching

/**
 * "Отправить жалобу": normalized, then validated (Invalid: nothing is sent), then sent. A repeat makes a second complaint
 * (API-GAP G48), so the screen guards against it.
 */
class SendComplaintUseCase(private val repository: ComplaintsRepository) {
    suspend operator fun invoke(draft: ComplaintDraft): SubmitResult<Unit, ComplaintDraftError> {
        val normalized = draft.normalized()
        val errors = normalized.validate()
        val reason = normalized.reason
        if (errors.isNotEmpty() || reason == null) return SubmitResult.Invalid(errors)
        val complaint = Complaint(normalized.target, reason, normalized.description)
        return suspendRunCatching { repository.send(complaint) }.fold(
            onSuccess = { SubmitResult.Success(Unit) },
            onFailure = { SubmitResult.Failure(it) },
        )
    }
}
