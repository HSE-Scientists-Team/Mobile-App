package org.matchlab.academic_net.gateway.contract

/**
 * Finds the token of `POST /auth/email/confirm` in what the user pasted from the confirmation letter.
 *
 * API-GAP G01: the spec says only "pass the token from the link in the letter", so what the link looks like is a
 * guess ([UnverifiedContract]). The guess is generous: the screen asks for a "link from the letter", yet a person may
 * paste the whole address, its tail, or just the token. It is a convenience of the client, not a value of the API:
 * the request carries the token the guess found, and the server is the judge of it (404 for a token it does not know).
 */
@UnverifiedContract
object ConfirmationLinks {
    private const val TokenParameter = "token"

    /**
     * The token in [input], looked for in this order:
     * 1. the `token` parameter of the link's query (`https://host/confirm?token=abc`), or of its fragment
     *    (`https://host/#/confirm?token=abc`); the name is case-insensitive, `%XX` escapes are decoded and a
     *    literal `+` stays a `+`;
     * 2. the last non-empty segment of the link's path (`https://host/confirm/abc`);
     * 3. the whole text, when it is not a link at all (`abc`): returned as typed, apart from the trimmed
     *    surrounding whitespace.
     *
     * Returns `null` when there is nothing to send: blank text, text with whitespace inside (a pasted
     * sentence is not a token), a link without a path and without a token (`https://host/`), or a `token`
     * parameter that is empty. The result is a guess about the token, not a proof: the server answers 404 for
     * a token it does not know.
     */
    fun extractToken(input: String): String? {
        val text = input.trim()
        if (text.isEmpty() || text.any { it.isWhitespace() || it.isISOControl() }) return null

        // No slash, question mark or hash: not a link, so the whole text is the token.
        if (text.none { it == '/' || it == '?' || it == '#' }) return text

        val beforeFragment = text.substringBefore('#')
        val fragment = text.substringAfter('#', missingDelimiterValue = "")
        val beforeQuery = beforeFragment.substringBefore('?')
        val query = beforeFragment.substringAfter('?', missingDelimiterValue = "")

        // "scheme://host/path": the part up to the first slash after the scheme is the host, not the path.
        val path = if ("://" in beforeQuery) {
            beforeQuery.substringAfter("://").substringAfter('/', missingDelimiterValue = "")
        } else {
            beforeQuery
        }

        // A parameter that is there but empty means a broken link, not "look at the path instead".
        val parameter = tokenParameter(query) ?: tokenParameter(fragment.substringAfter('?', fragment))
        if (parameter != null) return parameter.takeIf { it.isNotEmpty() }

        return path.split('/').lastOrNull { it.isNotEmpty() }?.let(::percentDecode)
    }

    /** The decoded value of the first `token=` pair of a query string, or `null` if there is none. */
    private fun tokenParameter(query: String): String? {
        for (pair in query.split('&')) {
            if ('=' !in pair) continue
            if (percentDecode(pair.substringBefore('=')).equals(TokenParameter, ignoreCase = true)) {
                return percentDecode(pair.substringAfter('='))
            }
        }
        return null
    }

    /**
     * Decodes `%XX` escapes as UTF-8. A `%` that is not followed by two hex digits stays as it is, and so does
     * a `+`: a token is never a text with spaces, so a `+` can only be a part of the token.
     */
    private fun percentDecode(text: String): String {
        if ('%' !in text) return text

        val decoded = StringBuilder()
        val escapedBytes = ArrayList<Byte>()
        fun flushBytes() {
            if (escapedBytes.isEmpty()) return
            decoded.append(escapedBytes.toByteArray().decodeToString())
            escapedBytes.clear()
        }

        var index = 0
        while (index < text.length) {
            val high = if (text[index] == '%' && index + 2 < text.length) text[index + 1].digitToIntOrNull(16) else null
            val low = if (high != null) text[index + 2].digitToIntOrNull(16) else null
            if (high != null && low != null) {
                escapedBytes.add(((high shl 4) or low).toByte())
                index += 3
            } else {
                flushBytes()
                decoded.append(text[index])
                index++
            }
        }
        flushBytes()
        return decoded.toString()
    }
}
