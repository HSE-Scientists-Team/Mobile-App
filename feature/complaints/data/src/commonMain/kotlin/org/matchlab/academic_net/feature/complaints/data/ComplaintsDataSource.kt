package org.matchlab.academic_net.feature.complaints.data

import org.matchlab.academic_net.feature.complaints.domain.Complaint
import org.matchlab.academic_net.feature.complaints.domain.ComplaintNotAllowedException
import org.matchlab.academic_net.feature.complaints.domain.ComplaintSubject
import org.matchlab.academic_net.feature.complaints.domain.ComplaintTarget
import org.matchlab.academic_net.feature.complaints.domain.ComplaintsRepository

/** Where complaints go: the Gateway ([GatewayComplaintsDataSource]). Mirrors [ComplaintsRepository]: the same members, the same meaning. */
interface ComplaintsDataSource {
    /** The card of the target; `null`: no such person or project for the viewer. Throws a failure. */
    suspend fun getSubject(target: ComplaintTarget): ComplaintSubject?

    /** Sends the complaint. @throws ComplaintNotAllowedException 403; else the `RemoteFailure`. No retry. */
    suspend fun send(complaint: Complaint)
}
