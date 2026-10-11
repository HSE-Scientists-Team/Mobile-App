package org.matchlab.academic_net.feature.profile.data.mock

/**
 * The host of an http/https link in lowercase without a leading `www.`, `null` if the text is not
 * such a link. It is what the mock server needs from a link (the label of the source and the
 * moderator's trusted sites), not a URL parser.
 */
internal fun webLinkHost(link: String): String? {
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

/** Whether the [host] is [site] itself or one of its subdomains (`gist.github.com` is `github.com`). */
internal fun hostIsOf(host: String, site: String): Boolean = host == site || host.endsWith(".$site")

/**
 * The server's label of where a work lives, derived from its link: `doi.org/X` is "DOI X",
 * `github.com` is "GitHub", `arxiv.org` is "arXiv", any other site is its domain. `null` when there
 * is no link, or it is not an http/https link.
 */
internal fun linkSource(link: String?): String? {
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
