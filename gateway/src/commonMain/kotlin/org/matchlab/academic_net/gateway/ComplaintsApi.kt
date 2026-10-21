package org.matchlab.academic_net.gateway

import org.matchlab.academic_net.gateway.dto.CreateComplaintDto

/** Complaints to the moderators. Throws `RemoteFailure` when it fails and lets a `CancellationException` through. */
interface ComplaintsApi {
    /**
     * `POST /api/v1/complaints` → 201. The answer (`CreatedComplaint`) is not read: the app needs nothing of it, and an
     * answer it could not read must not turn a complaint that was made into a failure (API-GAP G48: a repeat makes a
     * second complaint).
     */
    suspend fun create(input: CreateComplaintDto)
}
