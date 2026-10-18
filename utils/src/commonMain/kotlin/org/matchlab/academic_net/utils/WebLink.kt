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

/**
 * The host of an http/https [link] in lowercase without a leading `www.`, `null` if the text is not
 * such a link. It is what the label of a link's source and a list of trusted sites need from a link
 * (see [linkSource] and [hostIsOf]), not a URL parser: outer whitespace is trimmed, credentials, the
 * port, the path, the query and the fragment are skipped, and the host itself is not validated (use
 * [isWebLink] for that).
 *
 * ```
 * webLinkHost("https://WWW.Example.com:8080/Path?q=1") // "example.com"
 * webLinkHost("example.com")                           // null
 * ```
 */
fun webLinkHost(link: String): String? {
    val rest = link.trim().let { text ->
        when {
            text.startsWith("https://", ignoreCase = true) -> text.substring("https://".length)
            text.startsWith("http://", ignoreCase = true) -> text.substring("http://".length)
            else -> return null
        }
    }
    val authority = rest.takeWhile { it != '/' && it != '?' && it != '#' }
    val host = authority.substringAfterLast('@').substringBefore(':').lowercase().removePrefix("www.")
    return host.takeIf { it.isNotEmpty() }
}

/**
 * Whether the [host] is [site] itself or one of its subdomains: `gist.github.com` is `github.com`, while
 * `notgithub.com` is not. Both are expected in lowercase, as [webLinkHost] returns them.
 */
fun hostIsOf(host: String, site: String): Boolean = host == site || host.endsWith(".$site")

/**
 * The label of where something lives, derived from its [link] on the client: `doi.org/X` is "DOI X"
 * (just "DOI" without an identifier), `github.com` is "GitHub", `arxiv.org` is "arXiv", any other site is its
 * domain without `www.` (see [webLinkHost]). `null` when there is no link or it is not an http/https link.
 *
 * The API of the Gateway has no such field, so the app derives it; the mock server did it the same way.
 */
fun linkSource(link: String?): String? {
    if (link == null) return null
    val host = webLinkHost(link) ?: return null
    return when {
        hostIsOf(host, "doi.org") -> {
            val identifier = doiIdentifier(link)
            if (identifier.isEmpty()) "DOI" else "DOI $identifier"
        }
        hostIsOf(host, "github.com") -> "GitHub"
        hostIsOf(host, "arxiv.org") -> "arXiv"
        else -> host
    }
}

/** The part of a doi.org link after the host, without the query and the fragment: `10.1234/cv`. */
private fun doiIdentifier(link: String): String {
    val afterScheme = link.trim().substringAfter("://")
    val path = afterScheme.substringAfter('/', missingDelimiterValue = "")
    return path.substringBefore('?').substringBefore('#').trim('/')
}
