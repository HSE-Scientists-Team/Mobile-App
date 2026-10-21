package org.matchlab.academic_net.feature.profile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import org.matchlab.academic_net.utils.isWebLink

/**
 * The link of a work as the app may open it in the browser, or `null` when it may not.
 *
 * API-GAP G56: the `url` of a work comes from other people's data and nothing checks its scheme, so it can be `tel:`,
 * `sms:`, `intent:`, `file:` or the address of some other app (`someapp://…`), and handing that to the system would
 * start whatever answers it. Only a web link, `http` or `https` with a host ([isWebLink]), is openable. The text is
 * trimmed first, as [isWebLink] asks, and the trimmed text is what is opened.
 *
 * A card of a work is a button, and the "Открыть ссылку" button of the form is there, only when this is not `null`.
 *
 * @param link the link of a work, `null` when it has none
 * @return the link to open, `null` for no link, a blank one, or one that is not a web link
 */
internal fun openableLink(link: String?): String? = link?.trim()?.takeIf { it.isWebLink() }

/**
 * Opens a link of a work in the browser of the device. The link is other people's data and nothing checks its scheme
 * (API-GAP G56), so a link that is not a web link is not opened at all ([openableLink]), whatever asks for it. A device
 * may still have nothing to open a web link with; that is not worth a crash, so a failure is ignored.
 */
@Composable
internal fun rememberLinkOpener(): (String) -> Unit {
    val handler = LocalUriHandler.current
    return remember(handler) {
        { link -> openableLink(link)?.let { url -> runCatching { handler.openUri(url) } } }
    }
}
