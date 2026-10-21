package org.matchlab.academic_net.feature.auth.data

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.feature.auth.domain.AccessToken
import org.matchlab.academic_net.feature.auth.domain.Credentials
import org.matchlab.academic_net.feature.auth.domain.Password
import org.matchlab.academic_net.feature.auth.domain.Session

/**
 * An account server that remembers what it was told, for the tests that run the whole flow (register, open the link,
 * sign in, relaunch the app) through the real repository. It answers the way the Gateway does in `docs/openapi.yaml`:
 *  - a registration of an address of an untrusted domain is refused (403), of an address that has an account, too (409);
 *  - registering stores a request with the password and the time; the same address again replaces them;
 *  - the letter always carries [LetterToken]; the link (or the bare token) confirms the last request: the account gets
 *    the password of that request. A request older than [LinkLifetime] answers 410, a wrong token or a used one 404
 *    (both are [ConfirmReply.InvalidLink]);
 *  - an address without an account, a wrong password and an unconfirmed address all answer 401;
 *  - every sign-in lands on [viewer] and gets a token of its own; a token is good until it is revoked.
 */
internal class PlayedAccountServer(
    private val timeSource: TimeSource,
    trustedDomains: Set<String> = setOf("hse.ru", "msu.ru", "spbu.ru", "itmo.ru", "mipt.ru", "urfu.ru", "gmail.com"),
) : AuthDataSource {
    private val trusted = trustedDomains.map { it.trim().lowercase() }.toSet()
    private val accounts = mutableMapOf<String, Password>()
    private var request: Request? = null
    private val revoked = mutableSetOf<String>()
    private var issued = 0

    private class Request(val address: String, val password: Password, val madeAt: TimeMark)

    override suspend fun register(credentials: Credentials): RegisterReply {
        val address = credentials.email.trim().lowercase()
        if (!isTrusted(address)) return RegisterReply.DomainNotAllowed
        if (address in accounts) return RegisterReply.AlreadyRegistered

        request = Request(address, credentials.password, timeSource.markNow())
        return RegisterReply.LetterSent
    }

    override suspend fun confirmEmail(linkOrToken: String): ConfirmReply {
        val token = tokenOf(linkOrToken) ?: return ConfirmReply.InvalidLink
        if (token != LetterToken) return ConfirmReply.InvalidLink

        val waiting = request ?: return ConfirmReply.InvalidLink
        if (waiting.madeAt.elapsedNow() >= LinkLifetime) return ConfirmReply.Expired

        // The request is deleted and the token with it: the second try with the same letter finds nothing.
        request = null
        accounts[waiting.address] = waiting.password
        return ConfirmReply.Confirmed
    }

    override suspend fun signIn(credentials: Credentials): SignInReply {
        val address = credentials.email.trim().lowercase()
        if (accounts[address] != credentials.password) return SignInReply.Unauthorized

        return SignInReply.Success(Session(viewer, address, AccessToken("played-token-${++issued}")))
    }

    override suspend fun checkSession(token: AccessToken): SessionCheck =
        if (token.value.isNotBlank() && token.value !in revoked) SessionCheck.Valid(viewer) else SessionCheck.Rejected

    override suspend fun revoke(token: AccessToken) {
        revoked += token.value
    }

    private fun isTrusted(address: String): Boolean {
        if (address.count { it == '@' } != 1) return false
        val domain = address.substringAfter('@')
        return domain.isNotEmpty() && trusted.any { domain == it || domain.endsWith(".$it") }
    }

    /** The token of what the user pasted: the `token` parameter of a link, the last segment of its path, or the text. */
    private fun tokenOf(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
        if (trimmed.none { it == '/' || it == '?' || it == '#' }) return trimmed

        val parameter = trimmed.substringAfter("token=", "").substringBefore('&').substringBefore('#')
        if ("token=" in trimmed) return parameter.takeIf { it.isNotEmpty() }
        return trimmed.substringBefore('?').substringBefore('#').split('/').lastOrNull { it.isNotEmpty() }
    }

    companion object {
        /** The token every letter of this server carries. */
        const val LetterToken = "123456"

        /** How long the link of a letter works. */
        val LinkLifetime: Duration = 30.minutes

        /** The user every sign-in lands on. */
        val viewer = UserId("user-me")
    }
}
