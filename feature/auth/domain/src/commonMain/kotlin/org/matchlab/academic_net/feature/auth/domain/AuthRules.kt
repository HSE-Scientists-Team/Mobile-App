package org.matchlab.academic_net.feature.auth.domain

/** What is wrong with the typed e-mail address. */
enum class EmailError {
    /** Nothing was typed. */
    Empty,

    /** Not an address: no `@`, no domain, a domain without a dot, spaces. */
    Malformed,
}

/** What is wrong with the credentials typed in the sign-in or the registration form; each case belongs to one field. */
enum class CredentialsError {
    /** The e-mail field is empty. */
    EmailEmpty,

    /** The e-mail is not an address, see [EmailError.Malformed]. */
    EmailMalformed,

    /** The password field is empty. */
    PasswordEmpty,

    /** The password is shorter than [PasswordRules.MinBytes] bytes. */
    PasswordTooShort,

    /** The password is longer than [PasswordRules.MaxBytes] bytes. */
    PasswordTooLong,

    /** The repeated password is not the same as the password (registration only). */
    PasswordsDiffer,
}

/** What is wrong with the text typed in the field for the link from the letter. */
enum class ConfirmationInputError {
    /** Nothing was typed. */
    Empty,
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

/**
 * Form rules for the password of a registration. The server takes from [MinBytes] to [MaxBytes]
 * bytes of UTF-8 (schema `Credentials` of the API), so a Cyrillic letter counts as two bytes and
 * most emoji as four. Whether a password is easy to guess is not checked.
 */
object PasswordRules {
    /** The shortest password, in bytes of UTF-8. */
    const val MinBytes = 8

    /** The longest password, in bytes of UTF-8. */
    const val MaxBytes = 72

    /**
     * The first problem of [password], or `null` when it is fine. It is taken as typed: spaces at
     * the ends are part of the password and are never trimmed, so a password of eight spaces is a
     * password. The length is in UTF-8 bytes, not in characters.
     */
    fun validate(password: String): CredentialsError? {
        if (password.isEmpty()) return CredentialsError.PasswordEmpty
        val bytes = utf8Length(password)
        return when {
            bytes < MinBytes -> CredentialsError.PasswordTooShort
            bytes > MaxBytes -> CredentialsError.PasswordTooLong
            else -> null
        }
    }

    /**
     * The length of [text] in UTF-8, counted without encoding it: the password is not copied into
     * a byte array that would outlive it in memory. A lone surrogate counts as the three bytes of
     * the replacement character, which is what the platforms and the server make of it.
     */
    private fun utf8Length(text: String): Int {
        var bytes = 0
        var index = 0
        while (index < text.length) {
            val char = text[index]
            bytes += when {
                char.code < 0x80 -> 1
                char.code < 0x800 -> 2
                char.isHighSurrogate() && index + 1 < text.length && text[index + 1].isLowSurrogate() -> {
                    // A pair of surrogates is one code point of four bytes.
                    index++
                    4
                }
                else -> 3
            }
            index++
        }
        return bytes
    }
}
