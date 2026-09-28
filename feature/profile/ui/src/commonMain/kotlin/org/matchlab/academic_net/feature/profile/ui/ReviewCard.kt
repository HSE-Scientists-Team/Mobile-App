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
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.matchlab.academic_net.components.AppCard
import org.matchlab.academic_net.components.Avatar

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

/** "Card/Review": review about the user after a project. */
@Composable
fun ReviewCard(
    model: ReviewCardUiModel,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    AppCard(modifier, onClick = onClick, spacing = 8.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
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
        Text(model.text, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}
