package org.matchlab.academic_net

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import org.matchlab.academic_net.ui.navigation.MainScaffold
import org.matchlab.academic_net.ui.theme.AcademicNetTheme

@Composable
@Preview
fun App() {
    AcademicNetTheme {
        MainScaffold()
    }
}
