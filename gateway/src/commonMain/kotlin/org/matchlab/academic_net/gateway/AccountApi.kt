package org.matchlab.academic_net.gateway

import org.matchlab.academic_net.gateway.dto.ConfirmEmailRequestDto
import org.matchlab.academic_net.gateway.dto.CredentialsDto
import org.matchlab.academic_net.gateway.dto.LoginResultDto
import org.matchlab.academic_net.gateway.dto.RegistrationResultDto
import org.matchlab.academic_net.gateway.dto.UserIdDto

/**
 * The calls that are not made for a session: creating an account, confirming the mail and signing in. The
 * check of a token and the sign-out belong here too, because the repository that owns the session makes them
 * with the token it holds and handles their 401 itself.
 *
 * Every call throws `RemoteFailure` when it fails: `Http` for any status outside 2xx (a 403 on `register` is
 * "this mail domain is not allowed", a 409 is "already registered", and what a code means is the caller's
 * business), `NoConnection`, `Timeout` and `Malformed`. A `CancellationException` passes through untouched.
 *
 * No call here sends `Authorization` except [currentSession] and [revokeSession], and a 401 here never
 * reaches a `SessionRejectionListener`.
 */
interface AccountApi {
    /**
     * `POST /api/v1/auth/register` → 201. Stores a pending registration and sends the letter; there is no
     * account yet. Registering the same mail again replaces the password and invalidates the earlier link.
     */
    suspend fun register(credentials: CredentialsDto): RegistrationResultDto

    /** `POST /api/v1/auth/email/confirm` → 204. Creates the account of the pending registration the token belongs to. */
    suspend fun confirmEmail(request: ConfirmEmailRequestDto)

    /**
     * `POST /api/v1/auth/login` → 200. A 401 here means a wrong mail or password, and also an address that has
     * not confirmed the mail yet.
     */
    suspend fun login(credentials: CredentialsDto): LoginResultDto

    /** `GET /api/v1/sessions/current` → 200: whose session [accessToken] is. A 401 or 403: it is not valid. */
    suspend fun currentSession(accessToken: String): UserIdDto

    /** `DELETE /api/v1/sessions/current` → 204: ends the session of [accessToken] on the server. */
    suspend fun revokeSession(accessToken: String)
}
