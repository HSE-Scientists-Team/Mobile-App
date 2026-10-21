package org.matchlab.academic_net.feature.complaints.ui

import org.matchlab.academic_net.feature.complaints.domain.GetComplaintSubjectUseCase
import org.matchlab.academic_net.feature.complaints.domain.SendComplaintUseCase

/**
 * What the screens of the complaints feature need from the outside; the app creates it once and hands it to
 * `ComplaintRoute`.
 */
class ComplaintsUiDependencies(
    val getComplaintSubject: GetComplaintSubjectUseCase,
    val sendComplaint: SendComplaintUseCase,
)
