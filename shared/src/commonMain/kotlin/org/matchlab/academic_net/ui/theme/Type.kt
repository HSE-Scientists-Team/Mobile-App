package org.matchlab.academic_net.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Text styles from the Figma design system. The design sets them in Outfit (display, headline,
// title large/medium) and DM Sans (everything else), but neither font has Cyrillic glyphs, so the
// platform font is used until a Cyrillic-capable pair is chosen.
private val Default = Typography()

internal val AppTypography = Typography(
    displaySmall = Default.displaySmall.copy(
        fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.sp,
    ),
    headlineMedium = Default.headlineMedium.copy(
        fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp,
    ),
    headlineSmall = Default.headlineSmall.copy(
        fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = 0.sp,
    ),
    titleLarge = Default.titleLarge.copy(
        fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.sp,
    ),
    titleMedium = Default.titleMedium.copy(
        fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp,
    ),
    titleSmall = Default.titleSmall.copy(
        fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.sp,
    ),
    bodyLarge = Default.bodyLarge.copy(
        fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp,
    ),
    bodyMedium = Default.bodyMedium.copy(
        fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.sp,
    ),
    bodySmall = Default.bodySmall.copy(
        fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.sp,
    ),
    labelLarge = Default.labelLarge.copy(
        fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.sp,
    ),
    labelMedium = Default.labelMedium.copy(
        fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.sp,
    ),
    labelSmall = Default.labelSmall.copy(
        fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.sp,
    ),
)
