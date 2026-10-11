package org.matchlab.academic_net.utils

private val SchemePrefixes = listOf("https://", "http://")

/**
 * Whether the text is an `http` or `https` link that has a host: `https://doi.org/10.1234/cv`,
 * `http://localhost:8080`. The scheme is case-insensitive.
 *
 * It is a form check for what a user typed, not a full URL parser: the text must have no
 * whitespace (call it on the trimmed text), the host is made of non-empty dot-separated labels of
 * letters, digits and inner hyphens, and an optional port is digits. Text without a scheme
 * (`example.com`) and other schemes (`ftp://`, `javascript:`) are not web links.
 */
fun String.isWebLink(): Boolean {
    if (isEmpty() || any { it.isWhitespace() }) return false
    val prefix = SchemePrefixes.firstOrNull { startsWith(it, ignoreCase = true) } ?: return false
    val rest = substring(prefix.length)

    // The authority ends where the path, the query or the fragment starts.
    val authority = rest.takeWhile { it != '/' && it != '?' && it != '#' }
    // Credentials ("user:password@") are not part of the host.
    val hostAndPort = authority.substringAfterLast('@')

    val host = hostAndPort.substringBefore(':')
    val port = if (':' in hostAndPort) hostAndPort.substringAfter(':') else null
    if (port != null && (port.isEmpty() || !port.all { it.isDigit() })) return false

    return host.isNotEmpty() && host.split('.').all { it.isHostLabel() }
}

private fun String.isHostLabel(): Boolean =
    isNotEmpty() &&
        first() != '-' &&
        last() != '-' &&
        all { it.isLetterOrDigit() || it == '-' }
