package org.matchlab.academic_net.gateway.contract

/**
 * The link of the confirmation letter, as the backend builds it: `smtp.verification_url` of its mail service with the
 * `token` parameter added, in production `https://matchlab.space/verify-email?token=<token>` (the token is base64url and
 * lives 30 minutes; matchlab-backend, `cmd/mail`). The page behind the link is expected to take the token and post it
 * to `POST /auth/email/confirm`; the app takes the same token when the link is opened on the phone.
 *
 * This is the strict reading used for a link the system hands to the app (an Android App Link, an iOS universal
 * link). What a person pastes into the confirmation screen is read more generously by [ConfirmationLinks], which
 * takes the token out of the same link.
 */
object LetterLinks {
    /** The host the letters point to. The app's links (`AndroidManifest.xml`, the iOS associated domains) name the same. */
    const val Host = "matchlab.space"

    /** The path of the page of the letter. */
    const val Path = "/verify-email"

    /**
     * The token of [link] when it is a link of the confirmation letter, and `null` for anything else: another scheme
     * (only `https` is a link of the letter), another host, another path, no `token` or an empty one. The host is compared
     * without regard to case; a port, a user name or a fragment make it not a link of the letter.
     */
    fun tokenOf(link: String): String? {
        val text = link.trim()
        val afterScheme = text.removePrefixIgnoreCase("https://") ?: return null
        val authorityEnd = afterScheme.indexOfFirst { it == '/' || it == '?' || it == '#' }
        if (authorityEnd < 0) return null
        if (!afterScheme.substring(0, authorityEnd).equals(Host, ignoreCase = true)) return null

        val rest = afterScheme.substring(authorityEnd)
        if ('#' in rest) return null
        val path = rest.substringBefore('?')
        if (path != Path && path != "$Path/") return null
        val query = rest.substringAfter('?', missingDelimiterValue = "")
        // The backend sets `token` with Go's `url.Values.Encode()`; base64url needs no escapes, and a token that has
        // any is not one of the letter.
        val token = query.split('&').firstOrNull { it.startsWith("token=") }?.removePrefix("token=")
        return token?.takeIf { it.isNotEmpty() && it.none { char -> char == '%' || char.isWhitespace() } }
    }

    private fun String.removePrefixIgnoreCase(prefix: String): String? =
        if (startsWith(prefix, ignoreCase = true)) substring(prefix.length) else null
}
