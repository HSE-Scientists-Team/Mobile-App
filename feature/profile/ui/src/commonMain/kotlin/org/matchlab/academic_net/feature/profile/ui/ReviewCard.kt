package org.matchlab.academic_net.feature.profile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.matchlab.academic_net.components.AppCard
import org.matchlab.academic_net.components.Avatar
import org.matchlab.academic_net.components.interaction.onceClickable

@Immutable
data class ReviewCardUiModel(
    val authorName: String,
    /** Author's role and project, e.g. "Инициатор · AI-навигация". */
    val authorRole: String,
    val authorAvatar: DrawableResource,
    val title: String,
    /** Rating out of five. */
    val rating: Int,
    val text: String,
)

/**
 * "Card/Review": review about the user after a project.
 *
 * @param onClick the whole card, e.g. to open the review in full
 * @param onAuthorClick the author line only, e.g. to open the author's profile; use it on a card
 *  that is not clickable itself
 * @param maxTextLines the review text is cut with an ellipsis after this many lines; the list of
 *  reviews shows a short text, the page of one review all of it
 */
@Composable
fun ReviewCard(
    model: ReviewCardUiModel,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onAuthorClick: (() -> Unit)? = null,
    maxTextLines: Int = Int.MAX_VALUE,
) {
    val colors = MaterialTheme.colorScheme
    AppCard(modifier, onClick = onClick, spacing = 8.dp) {
        Row(
            modifier = if (onAuthorClick != null) {
                Modifier.onceClickable(role = Role.Button, onClick = onAuthorClick)
            } else {
                Modifier
            },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(model.authorAvatar, contentDescription = null, size = 32.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(model.authorName, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                Text(model.authorRole, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = model.title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
            )
            Text("${model.rating}★", style = MaterialTheme.typography.titleMedium, color = colors.primary)
        }
        Text(
            text = model.text,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            maxLines = maxTextLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
