package org.matchlab.academic_net

import androidx.compose.ui.window.ComposeUIViewController
import org.matchlab.academic_net.securestore.KeychainSecureStore

fun MainViewController() = ComposeUIViewController { App(secureStore = { KeychainSecureStore() }) }
