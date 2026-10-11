package org.matchlab.academic_net.feature.profile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler

/**
 * Opens a link of a work in the browser of the device. The link was checked by the server, but a
 * device may still have nothing to open it with; that is not worth a crash, so a failure is
 * ignored.
 */
@Composable
internal fun rememberLinkOpener(): (String) -> Unit {
    val handler = LocalUriHandler.current
    return remember(handler) { { url -> runCatching { handler.openUri(url) } } }
}
