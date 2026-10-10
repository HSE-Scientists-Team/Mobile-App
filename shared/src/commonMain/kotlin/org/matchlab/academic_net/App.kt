package org.matchlab.academic_net

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import org.matchlab.academic_net.theme.AcademicNetTheme
import org.matchlab.academic_net.ui.navigation.AppNavHost

@Composable
@Preview
fun App() {
    AcademicNetTheme {
        AppNavHost(AppContainer.dependencies)
    }
}
