package org.matchlab.academic_net.gateway.internal

import org.matchlab.academic_net.gateway.AccountApi
import org.matchlab.academic_net.gateway.dto.ConfirmEmailRequestDto
import org.matchlab.academic_net.gateway.dto.CredentialsDto
import org.matchlab.academic_net.gateway.dto.LoginResultDto
import org.matchlab.academic_net.gateway.dto.RegistrationResultDto
import org.matchlab.academic_net.gateway.dto.UserIdDto

/**
 * [AccountApi] over the public client of the Gateway, the one that sends no `Authorization` of its own: the
 * token of [currentSession] and [revokeSession] is passed to the call that needs it, and no other call here
 * has the header.
 */
internal class KtorAccountApi(private val requests: ApiRequests) : AccountApi {
    override suspend fun register(credentials: CredentialsDto): RegistrationResultDto =
        requests.post("auth/register", credentials)

    override suspend fun confirmEmail(request: ConfirmEmailRequestDto) =
        requests.postWithoutAnswer("auth/email/confirm", request)

    override suspend fun login(credentials: CredentialsDto): LoginResultDto =
        requests.post("auth/login", credentials)

    override suspend fun currentSession(accessToken: String): UserIdDto =
        requests.get("sessions/current", bearerToken = accessToken)

    override suspend fun revokeSession(accessToken: String) =
        requests.delete("sessions/current", bearerToken = accessToken)
}
