package org.matchlab.academic_net.feature.complaints.domain

import org.matchlab.academic_net.common.result.LoadResult
import org.matchlab.academic_net.utils.suspendRunCatching

/** The card at the top of the form: who or what the complaint is about. */
class GetComplaintSubjectUseCase(private val repository: ComplaintsRepository) {
    suspend operator fun invoke(target: ComplaintTarget): LoadResult<ComplaintSubject> =
        suspendRunCatching { repository.getSubject(target) }.fold(
            onSuccess = { subject -> if (subject == null) LoadResult.NotFound else LoadResult.Found(subject) },
            onFailure = { LoadResult.Failure(it) },
        )
}
