package org.matchlab.academic_net.feature.auth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.feature.auth.ui.generated.resources.Res
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_app_name

/**
 * "22 Заставка": what the app shows while the saved session is being read (`AuthState.Restoring`): the `surface`
 * background and the name of the app in `headlineMedium` in the middle, and nothing that would say "wait" (no
 * indicator): the reading of a saved session does not wait for the network and takes a moment.
 *
 * It is the app's root that shows it, and what it shows next depends on the answer: the tabs when a session was
 * restored, the sign-in screen ([SignInRoute]) only when there is none. Showing the sign-in screen before the
 * answer would make it flash on the screen of a user who is still signed in; this screen is what stands there
 * instead.
 */
@Composable
fun AuthSplash(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(Res.string.auth_app_name),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
