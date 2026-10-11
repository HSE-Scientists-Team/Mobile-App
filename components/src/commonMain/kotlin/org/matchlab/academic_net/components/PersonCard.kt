package org.matchlab.academic_net.components

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

@Immutable
data class PersonCardUiModel(
    val name: String,
    val subtitle: String,
    val avatar: DrawableResource,
    val isVerified: Boolean = false,
    /** Extra line under the subtitle, e.g. "★ 4.9 · 12 завершённых проектов". */
    val stats: String? = null,
    val showChatButton: Boolean = false,
)

/**
 * "Card/Person", the universal person card (it was "Card/Initiator" before the design update):
 * avatar, name with the verification shield, subtitle and, when asked, the rating line and the
 * chat button. For a project initiator or member, a report subject, a review author. The user's
 * own card "Card/Self" (avatar, name, subtitle) is not used by any screen yet.
 */
@Composable
fun PersonCard(
    model: PersonCardUiModel,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onChatClick: () -> Unit = {},
) {
    AppCard(modifier, onClick = onClick) {
        PersonSummary(model, onChatClick)
    }
}

/** Content of [PersonCard], for cards that add their own actions under it. */
@Composable
fun PersonSummary(
    model: PersonCardUiModel,
    onChatClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        // With the chat button the row is bottom-aligned, otherwise centered.
        verticalAlignment = if (model.showChatButton) Alignment.Bottom else Alignment.CenterVertically,
    ) {
        Avatar(model.avatar, contentDescription = null, size = 48.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            PersonName(model.name, model.isVerified, MaterialTheme.typography.titleMedium)
            Text(model.subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            if (model.stats != null) {
                Text(model.stats, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }
        if (model.showChatButton) ChatIconButton(onChatClick)
    }
}
