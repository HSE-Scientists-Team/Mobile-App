package org.matchlab.academic_net

import androidx.compose.ui.window.ComposeUIViewController
import org.matchlab.academic_net.securestore.KeychainSecureStore

/**
 * The root view controller of the iOS app. The session is kept in the Keychain, and the data comes from the Gateway
 * server: there is no way to switch to the mock on iOS.
 */
fun MainViewController() = ComposeUIViewController {
    App(secureStore = { KeychainSecureStore() }, backend = Backend.Gateway())
}
