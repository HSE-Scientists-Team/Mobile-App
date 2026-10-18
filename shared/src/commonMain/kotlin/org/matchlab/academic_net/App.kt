package org.matchlab.academic_net

import androidx.compose.runtime.Composable
import org.matchlab.academic_net.di.AppGraph
import org.matchlab.academic_net.securestore.SecureStore
import org.matchlab.academic_net.theme.AcademicNetTheme
import org.matchlab.academic_net.ui.navigation.AppNavHost

/**
 * The app.
 *
 * The object graphs are built once per process, on the first call, and outlive the activity, so the session and the
 * data of the user survive a rotation: nobody signs in again because the screen turned. Which screen is shown follows
 * the state of the session: the splash, the sign-in or the tabs.
 *
 * @param secureStore creates the platform's encrypted storage, where the session is kept; called once per process,
 * when the object graph is built
 * @param backend the Gateway server to talk to (the production one by default, and what the iOS app uses); fixed for
 * the process by the first call
 */
@Composable
fun App(secureStore: () -> SecureStore, backend: Backend = Backend.Gateway()) {
    val graphs = AppGraph.get(secureStore, backend)
    AcademicNetTheme {
        AppNavHost(auth = graphs.auth, sessions = graphs.sessions)
    }
}
