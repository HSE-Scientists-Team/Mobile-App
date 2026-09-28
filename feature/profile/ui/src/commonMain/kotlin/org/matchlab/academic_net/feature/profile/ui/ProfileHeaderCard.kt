package org.matchlab.academic_net.feature.profile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.matchlab.academic_net.components.AppButton
import org.matchlab.academic_net.components.AppCard
import org.matchlab.academic_net.components.AppStatusChip
import org.matchlab.academic_net.components.AppStatusChipTone
import org.matchlab.academic_net.components.Avatar
import org.matchlab.academic_net.components.ChatIconButton
import org.matchlab.academic_net.components.VerifiedBadge

@Immutable
data class ProfileStatusUiModel(
    val text: String,
    val tone: AppStatusChipTone,
)

@Immutable
data class ProfileHeaderCardUiModel(
    val name: String,
    val subtitle: String,
    val avatar: DrawableResource,
    val isVerified: Boolean = false,
    /** Chips under the name, e.g. "На проверке" or "Открыт к сотрудничеству". */
    val statuses: List<ProfileStatusUiModel> = emptyList(),
    /** Main button, e.g. "Пригласить" on someone else's profile; hidden when null. */
    val actionText: String? = null,
    val showChatButton: Boolean = false,
)

/** "Card/Profile header": header of the user's own or someone else's profile. */
@Composable
fun ProfileHeaderCard(
    model: ProfileHeaderCardUiModel,
    modifier: Modifier = Modifier,
    onActionClick: () -> Unit = {},
    onChatClick: () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    AppCard(modifier, spacing = 12.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(model.avatar, contentDescription = null, size = 64.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = model.name,
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.onSurface,
                    )
                    if (model.isVerified) VerifiedBadge(size = 18.dp)
                }
                Text(model.subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
        if (model.statuses.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                model.statuses.forEach { AppStatusChip(it.text, it.tone) }
            }
        }
        if (model.actionText != null || model.showChatButton) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (model.actionText != null) {
                    AppButton(model.actionText, onActionClick, Modifier.weight(1f))
                }
                if (model.showChatButton) ChatIconButton(onChatClick)
            }
        }
    }
}
