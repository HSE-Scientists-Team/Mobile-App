package org.matchlab.academic_net.feature.auth.domain

/** What is wrong with the typed e-mail address. */
enum class EmailError {
    /** Nothing was typed. */
    Empty,

    /** Not an address: no `@`, no domain, a domain without a dot, spaces. */
    Malformed,
}

/** What is wrong with the typed verification code. */
enum class CodeError {
    /** Fewer than [VerificationCodeRules.LENGTH] digits. */
    Incomplete,
}

/**
 * Form rules for the e-mail address. They only keep obvious typos away from the server; whether
 * the domain is a registered university is decided by the server, from its registry.
 */
object EmailRules {
    private const val MaxLength = 254

    /** The address as the server sees it: without surrounding spaces, in lower case. */
    fun normalize(raw: String): String = raw.trim().lowercase()

    /**
     * The first problem of an already [normalize]d address, or `null` when it is fine: exactly one
     * `@`, a non-empty local part without spaces, a domain of at least two dot-separated labels of
     * letters, digits and inner hyphens, the last label (the TLD) at least two characters long.
     */
    fun validate(normalized: String): EmailError? {
        if (normalized.isEmpty()) return EmailError.Empty
        if (normalized.length > MaxLength) return EmailError.Malformed
        if (normalized.any { it.isWhitespace() }) return EmailError.Malformed
        if (normalized.count { it == '@' } != 1) return EmailError.Malformed

        val local = normalized.substringBefore('@')
        val domain = normalized.substringAfter('@')
        if (local.isEmpty()) return EmailError.Malformed

        val labels = domain.split('.')
        if (labels.size < 2) return EmailError.Malformed
        if (!labels.all { it.isDomainLabel() }) return EmailError.Malformed
        if (labels.last().length < 2) return EmailError.Malformed
        return null
    }

    private fun String.isDomainLabel(): Boolean =
        isNotEmpty() &&
            first() != '-' &&
            last() != '-' &&
            all { it.isLetterOrDigit() || it == '-' }
}

/** Form rules for the code from the e-mail. */
object VerificationCodeRules {
    /** The code is this many digits. */
    const val LENGTH = 6

    /** What a code field keeps of [input]: its ASCII digits, at most [LENGTH] of them. */
    fun sanitize(input: String): String = input.filter { it in '0'..'9' }.take(LENGTH)

    /** [CodeError.Incomplete] unless the sanitized [code] is a full code. */
    fun validate(code: String): CodeError? = if (sanitize(code).length == LENGTH) null else CodeError.Incomplete
}
