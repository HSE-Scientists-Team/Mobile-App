package org.matchlab.academic_net.feature.auth.data

import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.feature.auth.domain.AccessToken
import org.matchlab.academic_net.feature.auth.domain.Credentials
import org.matchlab.academic_net.feature.auth.domain.Session

/**
 * An [AuthDataSource] a test scripts call by call: every call is recorded and answered by the
 * matching `on…` lambda, which may return a reply, throw a failure or suspend.
 *
 * By default the server accepts everything: the letter is sent, the link is right, a sign-in gives a
 * session of [UserId] `user-me` with the token `token-1`, `token-2`, …, the session check is valid
 * for that user.
 */
class FakeAuthDataSource : AuthDataSource {
    private var issuedTokens = 0

    var onRegister: suspend (Credentials) -> RegisterReply = { RegisterReply.LetterSent }
    var onConfirm: suspend (String) -> ConfirmReply = { ConfirmReply.Confirmed }
    var onSignIn: suspend (Credentials) -> SignInReply = {
        SignInReply.Success(Session(UserId("user-me"), it.email, AccessToken("token-${++issuedTokens}")))
    }
    var onCheck: suspend (AccessToken) -> SessionCheck = { SessionCheck.Valid(UserId("user-me")) }
    var onRevoke: suspend (AccessToken) -> Unit = {}

    val registered = mutableListOf<Credentials>()
    val confirmed = mutableListOf<String>()
    val signedIn = mutableListOf<Credentials>()
    val checked = mutableListOf<AccessToken>()
    val revoked = mutableListOf<AccessToken>()

    override suspend fun register(credentials: Credentials): RegisterReply {
        registered += credentials
        return onRegister(credentials)
    }

    override suspend fun confirmEmail(linkOrToken: String): ConfirmReply {
        confirmed += linkOrToken
        return onConfirm(linkOrToken)
    }

    override suspend fun signIn(credentials: Credentials): SignInReply {
        signedIn += credentials
        return onSignIn(credentials)
    }

    override suspend fun checkSession(token: AccessToken): SessionCheck {
        checked += token
        return onCheck(token)
    }

    override suspend fun revoke(token: AccessToken) {
        revoked += token
        onRevoke(token)
    }
}
