package org.matchlab.academic_net

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import org.matchlab.academic_net.securestore.AndroidSecureStore
import org.matchlab.academic_net.securestore.InMemorySecureStore

// Same value as the androidx.activity default dark scrim.
private val LegacyNavigationBarScrim = Color.argb(0x80, 0x1B, 0x1B, 0x1B)

/**
 * Where this build gets its data from, as the Gradle properties `matchlab.backend` and `matchlab.gatewayUrl` set it
 * (see `androidApp/build.gradle.kts`): the mock for `-Pmatchlab.backend=mock`, otherwise the Gateway, the production
 * server unless an address is given. Debug builds log the calls (method, URL and status, never a body or a token).
 */
internal fun buildBackend(): Backend =
    if (BuildConfig.MOCK_BACKEND) {
        Backend.Mock
    } else {
        Backend.Gateway(baseUrl = BuildConfig.GATEWAY_URL.ifBlank { null }, logCalls = BuildConfig.DEBUG)
    }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // The design is light-only, so keep dark system bar icons even when the device is in
        // dark mode. The dark scrim is only used where icons can't be dark (API 24-25 nav bar).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, LegacyNavigationBarScrim),
        )
        super.onCreate(savedInstanceState)

        setContent {
            // The session is kept encrypted with a key of the Android Keystore; it outlives the activity, so a
            // rotation or a relaunch does not ask for the password again.
            App(secureStore = { AndroidSecureStore(applicationContext) }, backend = buildBackend())
        }
    }
}

/** The preview runs on the mock: it has no network, and no session to restore. */
@Preview
@Composable
fun AppAndroidPreview() {
    App(secureStore = { InMemorySecureStore() }, backend = Backend.Mock)
}
