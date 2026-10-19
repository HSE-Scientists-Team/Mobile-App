package org.matchlab.academic_net

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import org.matchlab.academic_net.securestore.AndroidSecureStore
import org.matchlab.academic_net.securestore.InMemorySecureStore

// Same value as the androidx.activity default dark scrim.
private val LegacyNavigationBarScrim = Color.argb(0x80, 0x1B, 0x1B, 0x1B)

/**
 * The Gateway this build talks to, as the Gradle property `matchlab.gatewayUrl` sets it (see
 * `androidApp/build.gradle.kts`): the production server unless an address is given. Debug builds log the calls (method,
 * URL and status, never a body or a token).
 */
internal fun buildBackend(): Backend =
    Backend.Gateway(baseUrl = BuildConfig.GATEWAY_URL.ifBlank { null }, logCalls = BuildConfig.DEBUG)

class MainActivity : ComponentActivity() {
    /** The link the system opened the app with, until the app has taken it (see [App]). */
    private var incomingLink by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // The design is light-only, so keep dark system bar icons even when the device is in
        // dark mode. The dark scrim is only used where icons can't be dark (API 24-25 nav bar).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, LegacyNavigationBarScrim),
        )
        super.onCreate(savedInstanceState)
        // A recreated activity (a rotation, the process died) gets the intent it was first started with again; its link
        // was handled the first time, and a token is used once.
        if (savedInstanceState == null) incomingLink = linkOf(intent)

        setContent {
            // The session is kept encrypted with a key of the Android Keystore; it outlives the activity, so a
            // rotation or a relaunch does not ask for the password again.
            App(
                secureStore = { AndroidSecureStore(applicationContext) },
                backend = buildBackend(),
                incomingLink = incomingLink,
                onIncomingLinkHandled = { incomingLink = null },
            )
        }
    }

    /** The app is open and a link comes (`singleTask`): a tap on the link of the letter in the mail app. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        linkOf(intent)?.let { incomingLink = it }
    }

    private fun linkOf(intent: Intent?): String? = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.dataString
}

/**
 * The preview has no session to restore (an empty in-memory store), so it shows the sign-in and makes no request until
 * somebody signs in.
 */
@Preview
@Composable
fun AppAndroidPreview() {
    App(secureStore = { InMemorySecureStore() }, backend = Backend.Gateway())
}
