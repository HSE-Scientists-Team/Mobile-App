package org.matchlab.academic_net.feature.auth.data

import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.feature.auth.domain.AccessToken
import org.matchlab.academic_net.feature.auth.domain.Credentials
import org.matchlab.academic_net.feature.auth.domain.Session
import org.matchlab.academic_net.gateway.AccountApi
import org.matchlab.academic_net.gateway.contract.ConfirmationLinks
import org.matchlab.academic_net.gateway.contract.UnverifiedContract
import org.matchlab.academic_net.gateway.dto.ConfirmEmailRequestDto
import org.matchlab.academic_net.gateway.dto.CredentialsDto

/**
 * [AuthDataSource] over the account calls of the Gateway: it turns the status code of each answer
 * into a reply, as `docs/openapi.yaml` describes it:
 *
 * - `register`: 201 is [RegisterReply.LetterSent], 403 [RegisterReply.DomainNotAllowed], 409
 *   [RegisterReply.AlreadyRegistered].
 * - `confirmEmail`: no token in the text is [ConfirmReply.InvalidLink] and no request is made; 204 is
 *   [ConfirmReply.Confirmed], 409 [ConfirmReply.AlreadyConfirmed], 400 and 404 [ConfirmReply.InvalidLink],
 *   410 [ConfirmReply.Expired], 403 [ConfirmReply.DomainNotAllowed].
 * - `signIn`: 200 is [SignInReply.Success], 401 [SignInReply.Unauthorized], 403 [SignInReply.Forbidden].
 * - `checkSession`: 200 is [SessionCheck.Valid], 401 and 403 are [SessionCheck.Rejected].
 * - `revoke`: 204 and 401 are success.
 *
 * Any other status, and every failure of the network (`RemoteFailure.NoConnection`, `Timeout`,
 * `Malformed`), is thrown as it is: what to do about it is for the repository and the screens.
 * An answer that says "ok" but lacks the user or the token is `Malformed`.
 */
class GatewayAuthDataSource(private val api: AccountApi) : AuthDataSource {
    override suspend fun register(credentials: Credentials): RegisterReply =
        try {
            api.register(credentials.toDto())
            RegisterReply.LetterSent
        } catch (failure: RemoteFailure.Http) {
            when (failure.status) {
                STATUS_FORBIDDEN -> RegisterReply.DomainNotAllowed
                STATUS_CONFLICT -> RegisterReply.AlreadyRegistered
                else -> throw failure
            }
        }

    // The shape of the link in the letter is not defined by the API: ConfirmationLinks guesses it.
    @OptIn(UnverifiedContract::class)
    override suspend fun confirmEmail(linkOrToken: String): ConfirmReply {
        val token = ConfirmationLinks.extractToken(linkOrToken) ?: return ConfirmReply.InvalidLink
        return try {
            api.confirmEmail(ConfirmEmailRequestDto(token))
            ConfirmReply.Confirmed
        } catch (failure: RemoteFailure.Http) {
            when (failure.status) {
                STATUS_CONFLICT -> ConfirmReply.AlreadyConfirmed
                STATUS_BAD_REQUEST, STATUS_NOT_FOUND -> ConfirmReply.InvalidLink
                STATUS_GONE -> ConfirmReply.Expired
                STATUS_FORBIDDEN -> ConfirmReply.DomainNotAllowed
                else -> throw failure
            }
        }
    }

    override suspend fun signIn(credentials: Credentials): SignInReply =
        try {
            val login = api.login(credentials.toDto())
            if (login.userId.isBlank() || login.accessToken.isBlank()) throw RemoteFailure.Malformed()
            SignInReply.Success(Session(UserId(login.userId), credentials.email, AccessToken(login.accessToken)))
        } catch (failure: RemoteFailure.Http) {
            when (failure.status) {
                STATUS_UNAUTHORIZED -> SignInReply.Unauthorized
                STATUS_FORBIDDEN -> SignInReply.Forbidden
                else -> throw failure
            }
        }

    override suspend fun checkSession(token: AccessToken): SessionCheck =
        try {
            val current = api.currentSession(token.value)
            if (current.userId.isBlank()) throw RemoteFailure.Malformed()
            SessionCheck.Valid(UserId(current.userId))
        } catch (failure: RemoteFailure.Http) {
            when (failure.status) {
                STATUS_UNAUTHORIZED, STATUS_FORBIDDEN -> SessionCheck.Rejected
                else -> throw failure
            }
        }

    override suspend fun revoke(token: AccessToken) {
        try {
            api.revokeSession(token.value)
        } catch (failure: RemoteFailure.Http) {
            // A token the server does not know is as good as revoked.
            if (failure.status != STATUS_UNAUTHORIZED) throw failure
        }
    }

    private fun Credentials.toDto() = CredentialsDto(email, password.value)

    private companion object {
        const val STATUS_BAD_REQUEST = 400
        const val STATUS_UNAUTHORIZED = 401
        const val STATUS_FORBIDDEN = 403
        const val STATUS_NOT_FOUND = 404
        const val STATUS_CONFLICT = 409
        const val STATUS_GONE = 410
    }
}
