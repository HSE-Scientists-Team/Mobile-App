package org.matchlab.academic_net

import androidx.compose.ui.window.ComposeUIViewController
import org.matchlab.academic_net.analytics.Analytics
import org.matchlab.academic_net.analytics.NoOpAnalytics
import org.matchlab.academic_net.securestore.KeychainSecureStore
import platform.UIKit.UIViewController

/**
 * The root view controller of the iOS app. The session is kept in the Keychain, and the data comes from the production
 * Gateway server. This one runs without analytics.
 */
fun MainViewController(): UIViewController = MainViewController(analytics = NoOpAnalytics)

/**
 * The root view controller of the iOS app with [analytics], the Swift class that implements the contract over the
 * AppMetrica SDK. It starts with sending switched off; the app switches it on by the person's choice. (Kotlin default
 * arguments are not visible to Swift, hence two functions.)
 */
fun MainViewController(analytics: Analytics): UIViewController = ComposeUIViewController {
    App(secureStore = { KeychainSecureStore() }, backend = Backend.Gateway(), analytics = analytics)
}
