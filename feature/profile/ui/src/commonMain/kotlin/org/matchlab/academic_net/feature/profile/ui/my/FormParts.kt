package org.matchlab.academic_net.feature.profile.ui.my

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.components.AppButton
import org.matchlab.academic_net.components.AppButtonVariant
import org.matchlab.academic_net.components.AppIcons
import org.matchlab.academic_net.components.InfoBanner
import org.matchlab.academic_net.components.InfoBannerTone
import org.matchlab.academic_net.components.InfoCard
import org.matchlab.academic_net.components.StatusBanner
import org.matchlab.academic_net.feature.profile.domain.VerificationStatus
import org.matchlab.academic_net.feature.profile.ui.generated.resources.Res
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_conflict
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_moderator_comment
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_open_link
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_proof_attached
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_submit
import org.matchlab.academic_net.feature.profile.ui.bannerTitle
import org.matchlab.academic_net.feature.profile.ui.toBannerStatus

/** A small red line under a field or a button, for problems the field component has no slot for. */
@Composable
internal fun FormErrorText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.error,
    )
}

/**
 * The bar with the main button at the bottom of a form: the surface under the button reaches the
 * edge of the screen, the button stays above the navigation bar. A failed action is told above
 * the button ([errorText]).
 *
 * @param enabled whether the button takes taps; while the action runs ([loading]) it does not
 * @param loading the action of the button is on its way: an arc turns in place of the label
 */
@Composable
internal fun FormBottomBar(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    errorText: String?,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (errorText != null) {
            Text(text = errorText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        AppButton(
            text = text,
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            loading = loading,
        )
    }
}

/**
 * The banners at the top of the form of a record that is edited, one under another:
 *
 * - [isConflict]: the record was changed by someone else while the form was open, and the form shows the newer
 *   version ("Запись изменилась…"), an error banner;
 * - the state of the record ([StatusBanner]) when the server tells it. The comment of the moderator is part of that
 *   banner;
 * - API-GAP G12: when the state is not known (`null`, the Gateway never tells it) but the moderator left a comment, the
 *   comment is an error banner of its own ("Комментарий модератора: …"), whatever the state is.
 */
@Composable
internal fun PortfolioBanners(
    status: VerificationStatus?,
    moderationComment: String?,
    isConflict: Boolean,
) {
    if (isConflict) InfoBanner(stringResource(Res.string.profile_conflict), tone = InfoBannerTone.Error)
    val comment = moderationComment?.takeIf { it.isNotBlank() }
        ?.let { stringResource(Res.string.profile_moderator_comment, it) }
    when {
        status != null -> StatusBanner(status.toBannerStatus(), title = status.bannerTitle(), text = comment)
        comment != null -> InfoBanner(comment, tone = InfoBannerTone.Error)
    }
}

/**
 * "Подтверждение добавлено": the record has a proof file on the server (it was attached elsewhere, the app cannot
 * upload files yet). The card is not a button, there is nothing to do with it; the file stays when the record is saved.
 */
@Composable
internal fun EvidenceAttachedCard(modifier: Modifier = Modifier) {
    InfoCard(text = stringResource(Res.string.profile_proof_attached), icon = AppIcons.Check, modifier = modifier)
}

/**
 * The actions under the form of a record that is edited, each as wide as the form and 8dp apart:
 *
 * - "Отправить на проверку" (outlined) when the record [canSend];
 * - "Открыть ссылку" (a text button) when there is a link to open ([onOpenLink], a work only);
 * - the red text button that deletes the record ([deleteText]: "Удалить работу" / "Удалить документ"); the screen asks
 *   for the confirmation in [onDelete].
 *
 * @param enabled `false` while some action of the form runs: none of the buttons takes a tap then
 * @param isSending the sending is the action that runs: an arc turns in place of the label of the first button
 */
@Composable
internal fun PortfolioActions(
    canSend: Boolean,
    isSending: Boolean,
    enabled: Boolean,
    onSend: () -> Unit,
    deleteText: String,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenLink: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (canSend) {
            AppButton(
                text = stringResource(Res.string.profile_submit),
                onClick = onSend,
                modifier = Modifier.fillMaxWidth(),
                variant = AppButtonVariant.Outlined,
                enabled = enabled,
                loading = isSending,
            )
        }
        if (onOpenLink != null) {
            AppButton(
                text = stringResource(Res.string.profile_open_link),
                onClick = onOpenLink,
                modifier = Modifier.fillMaxWidth(),
                variant = AppButtonVariant.Text,
                enabled = enabled,
            )
        }
        AppButton(
            text = deleteText,
            onClick = onDelete,
            modifier = Modifier.fillMaxWidth(),
            variant = AppButtonVariant.TextError,
            enabled = enabled,
        )
    }
}
