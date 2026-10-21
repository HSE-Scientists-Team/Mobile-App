package org.matchlab.academic_net.feature.reviews.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.components.AppIcons
import org.matchlab.academic_net.components.InfoBanner
import org.matchlab.academic_net.feature.reviews.ui.generated.resources.Res
import org.matchlab.academic_net.feature.reviews.ui.generated.resources.reviews_sent

/** The banner 09s "Отзыв отправлен": [InfoBanner] with a check, above the header of the profile the review was left on. */
@Composable
fun ReviewSentBanner(modifier: Modifier = Modifier) {
    InfoBanner(
        text = stringResource(Res.string.reviews_sent),
        modifier = modifier,
        icon = AppIcons.Check,
    )
}
