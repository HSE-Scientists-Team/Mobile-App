package org.matchlab.academic_net

import androidx.compose.runtime.Composable
import org.matchlab.academic_net.di.AppGraph
import org.matchlab.academic_net.securestore.SecureStore
import org.matchlab.academic_net.theme.AcademicNetTheme
import org.matchlab.academic_net.ui.navigation.AppNavHost

/**
 * The app. [secureStore] creates the platform's encrypted storage; it is called once per process,
 * when the object graph is built.
 */
@Composable
fun App(secureStore: () -> SecureStore) {
    AcademicNetTheme {
        AppNavHost(AppGraph.component(secureStore).dependencies)
    }
}
