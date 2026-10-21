package org.matchlab.academic_net.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Host of transient messages ("Не удалось сохранить"), which appear at the bottom of the screen
 * and go away on their own. The design has no snackbar: this is the Material 3 one in the
 * inverse colors of the theme and the medium shape.
 *
 * Put it at the bottom of a screen's root `Box` (above the system bars) and show a message with
 * `hostState.showSnackbar(text)` from a `LaunchedEffect` or a coroutine of the screen.
 */
@Composable
fun AppSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    SnackbarHost(hostState, modifier) { data ->
        Snackbar(
            snackbarData = data,
            shape = MaterialTheme.shapes.medium,
            containerColor = colors.inverseSurface,
            contentColor = colors.inverseOnSurface,
            // The theme has no inverse primary; the app has no snackbar actions yet.
            actionColor = colors.inverseOnSurface,
        )
    }
}
