package org.matchlab.academic_net.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** "MatchLab Color" variables that have no Material 3 color role. */
@Immutable
data class ExtendedColors(
    val warningContainer: Color,
    val onWarningContainer: Color,
)

internal val LightExtendedColors = ExtendedColors(
    warningContainer = Color(0xFFFEF3C7),
    onWarningContainer = Color(0xFF92400E),
)

internal val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }

/** E.g. `MaterialTheme.extendedColors.warningContainer`. */
val MaterialTheme.extendedColors: ExtendedColors
    @Composable
    @ReadOnlyComposable
    get() = LocalExtendedColors.current
