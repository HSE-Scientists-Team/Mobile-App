package org.matchlab.academic_net.feature.auth.data

import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.feature.auth.domain.AccessToken
import org.matchlab.academic_net.feature.auth.domain.Credentials
import org.matchlab.academic_net.feature.auth.domain.Session

/**
 * The account server: the Gateway ([GatewayAuthDataSource]) or the mock that plays it
 * ([org.matchlab.academic_net.feature.auth.data.mock.MockAuthDataSource]). It only translates what
 * the server says into replies; what a reply means for the user is decided by [AuthRepositoryImpl].
 *
 * The addresses are normalized (trimmed, lower case) by the caller. A call throws when it fails:
 * `RemoteFailure.NoConnection`, `Timeout`, `Malformed`, or `Http` for a status that has no reply of
 * its own. A `CancellationException` is never turned into anything.
 */
interface AuthDataSource {
    /** Registers [credentials]: the server stores the registration and sends the letter with the link. */
    suspend fun register(credentials: Credentials): RegisterReply

    /** Confirms the address with [linkOrToken], the text the user pasted from the letter. */
    suspend fun confirmEmail(linkOrToken: String): ConfirmReply

    /** Signs in: the session is made from the answer and from the address of [credentials]. */
    suspend fun signIn(credentials: Credentials): SignInReply

    /** Asks whose session [token] is, and whether the server still accepts it. */
    suspend fun checkSession(token: AccessToken): SessionCheck

    /** Ends the session of [token] on the server. A token the server has forgotten already is not a failure. */
    suspend fun revoke(token: AccessToken)
}

/** The answer to [AuthDataSource.register]. */
sealed interface RegisterReply {
    /** Accepted, the letter is on its way (201). */
    data object LetterSent : RegisterReply

    /** The domain of the address is not of an organization that may register (403). */
    data object DomainNotAllowed : RegisterReply

    /** The address has an account already (409). */
    data object AlreadyRegistered : RegisterReply
}

/** The answer to [AuthDataSource.confirmEmail]. */
sealed interface ConfirmReply {
    /** The address is confirmed and the account created (204). */
    data object Confirmed : ConfirmReply

    /** The address is confirmed already (409). */
    data object AlreadyConfirmed : ConfirmReply

    /** There is no token in the text (no request was made), or the server does not know it or it is used (400, 404). */
    data object InvalidLink : ConfirmReply

    /** The link is out of date (410). */
    data object Expired : ConfirmReply

    /** The domain of the address is not of an organization that may be confirmed any more (403). */
    data object DomainNotAllowed : ConfirmReply
}

/** The answer to [AuthDataSource.signIn]. */
sealed interface SignInReply {
    /** Signed in (200): the session to keep. */
    data class Success(val session: Session) : SignInReply

    /** The address or the password is wrong, or the address is not confirmed yet (401). */
    data object Unauthorized : SignInReply

    /** The address is not confirmed or the account is disabled (403). */
    data object Forbidden : SignInReply
}

/** The answer to [AuthDataSource.checkSession]. */
sealed interface SessionCheck {
    /** The server accepts the token, it belongs to [userId] (200). */
    data class Valid(val userId: UserId) : SessionCheck

    /** The server does not accept the token (401, 403). */
    data object Rejected : SessionCheck
}
