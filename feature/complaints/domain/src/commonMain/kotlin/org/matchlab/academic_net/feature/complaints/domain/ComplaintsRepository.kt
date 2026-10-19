package org.matchlab.academic_net.feature.complaints.domain

/** 403: the e-mail is not confirmed (or the account is blocked); the form says "Подтвердите почту…". */
class ComplaintNotAllowedException : IllegalStateException("Complaints are not allowed for this account")

/** Complaints to the moderators. Suspending functions throw on failure; the use cases turn that into results. */
interface ComplaintsRepository {
    /** The card of the target; `null`: no such person or project for the viewer. Throws a failure. */
    suspend fun getSubject(target: ComplaintTarget): ComplaintSubject?

    /** `POST /complaints`. @throws ComplaintNotAllowedException 403; else the `RemoteFailure`. No retry (API-GAP G48). */
    suspend fun send(complaint: Complaint)
}
