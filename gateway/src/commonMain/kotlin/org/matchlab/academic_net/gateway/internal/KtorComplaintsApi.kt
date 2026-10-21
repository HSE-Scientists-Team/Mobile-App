package org.matchlab.academic_net.gateway.internal

import org.matchlab.academic_net.gateway.ComplaintsApi
import org.matchlab.academic_net.gateway.dto.CreateComplaintDto

/** [ComplaintsApi] over the client of a session. */
internal class KtorComplaintsApi(private val requests: ApiRequests) : ComplaintsApi {
    override suspend fun create(input: CreateComplaintDto) {
        requests.postWithoutAnswer("complaints", input)
    }
}
