package org.matchlab.academic_net.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

// "MatchLab Color" variables from the Figma design system. The design has a single light mode.
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF3550DC),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE8EDFF),
    onPrimaryContainer = Color(0xFF1A2E8A),
    secondary = Color(0xFF475569),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF1F5F9),
    onSecondaryContainer = Color(0xFF0F172A),
    tertiary = Color(0xFF065F46),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD1FAE5),
    onTertiaryContainer = Color(0xFF065F46),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFDECEA),
    onErrorContainer = Color(0xFF8C1D18),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    onSurfaceVariant = Color(0xFF475569),
    surfaceDim = Color(0xFFF1F5F9),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = Color(0xFFF1F5F9),
    surfaceContainerHigh = Color(0xFFE9EEF5),
    surfaceContainerHighest = Color(0xFFE2E8F0),
    outline = Color(0xFF94A3B8),
    outlineVariant = Color(0xFFE2E8F0),
    inverseSurface = Color(0xFF0F172A),
    inverseOnSurface = Color(0xFFF8FAFC),
    scrim = Color(0xFF0F172A),
    // Roles without a Figma variable, aliased the way the Material 3 baseline scheme does.
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    surfaceBright = Color(0xFFFFFFFF),
)

@Composable
fun AcademicNetTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalExtendedColors provides LightExtendedColors) {
        // The design's radius tokens (4/8/12/16/28) match the Material 3 default shapes.
        MaterialTheme(
            colorScheme = LightColorScheme,
            typography = appTypography(),
            content = content,
        )
    }
}
