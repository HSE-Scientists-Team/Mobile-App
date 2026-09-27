package org.matchlab.academic_net.ui.theme

import academicnet.shared.generated.resources.Res
import academicnet.shared.generated.resources.golos_text_bold
import academicnet.shared.generated.resources.golos_text_medium
import academicnet.shared.generated.resources.golos_text_regular
import academicnet.shared.generated.resources.golos_text_semibold
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font

private val Default = Typography()

/** Text styles from the Figma design system, all set in Golos Text. */
@Composable
internal fun appTypography(): Typography {
    val golosText = FontFamily(
        Font(Res.font.golos_text_regular, FontWeight.Normal),
        Font(Res.font.golos_text_medium, FontWeight.Medium),
        Font(Res.font.golos_text_semibold, FontWeight.SemiBold),
        Font(Res.font.golos_text_bold, FontWeight.Bold),
    )

    // Every Figma text style has zero letter spacing.
    fun TextStyle.spec(weight: FontWeight, size: TextUnit, lineHeight: TextUnit) = copy(
        fontFamily = golosText,
        fontWeight = weight,
        fontSize = size,
        lineHeight = lineHeight,
        letterSpacing = 0.sp,
    )

    return Typography(
        // Roles the design doesn't define keep Material 3 metrics.
        displayLarge = Default.displayLarge.copy(fontFamily = golosText),
        displayMedium = Default.displayMedium.copy(fontFamily = golosText),
        headlineLarge = Default.headlineLarge.copy(fontFamily = golosText),

        displaySmall = Default.displaySmall.spec(FontWeight.Bold, 28.sp, 36.sp),
        headlineMedium = Default.headlineMedium.spec(FontWeight.Bold, 22.sp, 28.sp),
        headlineSmall = Default.headlineSmall.spec(FontWeight.Bold, 18.sp, 24.sp),
        titleLarge = Default.titleLarge.spec(FontWeight.Bold, 16.sp, 22.sp),
        titleMedium = Default.titleMedium.spec(FontWeight.SemiBold, 14.sp, 20.sp),
        titleSmall = Default.titleSmall.spec(FontWeight.SemiBold, 13.sp, 18.sp),
        bodyLarge = Default.bodyLarge.spec(FontWeight.Normal, 14.sp, 20.sp),
        bodyMedium = Default.bodyMedium.spec(FontWeight.Normal, 13.sp, 18.sp),
        bodySmall = Default.bodySmall.spec(FontWeight.Normal, 12.sp, 16.sp),
        labelLarge = Default.labelLarge.spec(FontWeight.Medium, 13.sp, 18.sp),
        labelMedium = Default.labelMedium.spec(FontWeight.Medium, 12.sp, 16.sp),
        labelSmall = Default.labelSmall.spec(FontWeight.Medium, 11.sp, 14.sp),
    )
}
