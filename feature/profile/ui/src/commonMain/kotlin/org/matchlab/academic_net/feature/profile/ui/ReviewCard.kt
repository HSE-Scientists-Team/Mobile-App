package org.matchlab.academic_net.feature.profile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.components.AppCard
import org.matchlab.academic_net.components.Avatar
import org.matchlab.academic_net.components.ChatIconButton
import org.matchlab.academic_net.components.RatingStars
import org.matchlab.academic_net.components.interaction.onceClickable
import org.matchlab.academic_net.feature.profile.ui.generated.resources.Res
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_message_author
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_read_more

/**
 * What [ReviewCard] shows.
 *
 * @property authorLine the line under the name of the author: "Инициатор · AI-навигация по кампусу", or the title of the
 *  project alone when the role of the author is not known (API-GAP G13)
 * @property rating the mark, a whole number of stars out of five
 * @property date the month the project ended ("мар 2026") in the list of reviews, `null` where it is not shown (the
 *  preview on a profile) or not known
 * @property showChat the chat icon next to the author: not for a review the viewer wrote
 */
@Immutable
data class ReviewCardUiModel(
    val authorName: String,
    val authorLine: String,
    val authorAvatar: DrawableResource,
    val title: String,
    val rating: Int,
    val date: String?,
    val text: String,
    val showChat: Boolean,
)

/** How many lines of the text of a review a card shows before it cuts it. */
private const val ReviewTextLines = 3

/**
 * "Card/Review": a review about a person after a project. The author is a compact person row (40dp avatar, the name
 * and [ReviewCardUiModel.authorLine], at the end the chat icon); then the title of the review, the stars with the
 * month the project ended at the other end, and the text, cut with an ellipsis after three lines. When the text is
 * really cut (the text decides, not its length), "Читать полностью" stands under it.
 *
 * Three things can be tapped, each runs once per burst of taps: the author row opens the profile of the author, the
 * chat icon starts a chat with them, and the rest of the card (with "Читать полностью") opens the review in full.
 *
 * @param onClick the card and "Читать полностью": the review in full
 * @param onAuthorClick the row of the author: their profile
 * @param onChatClick the chat icon, which is there only with [ReviewCardUiModel.showChat]
 */
@Composable
fun ReviewCard(
    model: ReviewCardUiModel,
    onClick: () -> Unit,
    onAuthorClick: () -> Unit,
    onChatClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    // Whether the layout of the text had to cut it: it is known only after the text is measured.
    var isCut by remember(model.text) { mutableStateOf(false) }
    AppCard(modifier, onClick = onClick, spacing = 8.dp) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f).onceClickable(role = Role.Button, onClick = onAuthorClick),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(model.authorAvatar, contentDescription = null, size = 40.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = model.authorName,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (model.authorLine.isNotBlank()) {
                        Text(model.authorLine, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    }
                }
            }
            if (model.showChat) {
                ChatIconButton(
                    onClick = onChatClick,
                    contentDescription = stringResource(Res.string.profile_message_author),
                )
            }
        }
        Text(model.title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RatingStars(value = model.rating)
            if (model.date != null) {
                Text(model.date, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }
        Text(
            text = model.text,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            maxLines = ReviewTextLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { isCut = it.hasVisualOverflow },
        )
        if (isCut) {
            Text(
                text = stringResource(Res.string.profile_read_more),
                modifier = Modifier.onceClickable(role = Role.Button, onClick = onClick).padding(vertical = 4.dp),
                style = MaterialTheme.typography.labelLarge,
                color = colors.primary,
            )
        }
    }
}
