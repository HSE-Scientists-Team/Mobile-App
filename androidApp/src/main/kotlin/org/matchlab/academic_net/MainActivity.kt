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
            // The session is kept encrypted with a key of the Android Keystore.
            App(secureStore = { AndroidSecureStore(applicationContext) })
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App(secureStore = { InMemorySecureStore() })
}