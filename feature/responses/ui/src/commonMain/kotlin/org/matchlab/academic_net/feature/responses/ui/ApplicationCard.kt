package org.matchlab.academic_net.feature.responses.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.matchlab.academic_net.components.AppButton
import org.matchlab.academic_net.components.AppButtonVariant
import org.matchlab.academic_net.components.AppCard
import org.matchlab.academic_net.components.AppStatusChip
import org.matchlab.academic_net.components.ChatIconButton
import org.matchlab.academic_net.components.StatusChipUiModel

/**
 * What [ApplicationCard] shows.
 *
 * @property subtitle role and time, e.g. "Роль: ML Engineer · отклик 2 дня назад"
 * @property status the status of the record as a "Chip/Status" without an icon, right of the title ("Ожидает",
 *  "Принят"); `null` leaves it out (the status is unknown)
 * @property actionText outlined action in the error color, e.g. "Отозвать отклик"; hidden when `null` (the records that
 *  are decided already have no buttons)
 * @property actionLoading the action is on its way: the button shows an arc and takes no taps
 */
@Immutable
data class ApplicationCardUiModel(
    val title: String,
    val subtitle: String,
    val status: StatusChipUiModel? = null,
    val actionText: String? = null,
    val showChatButton: Boolean = false,
    val actionLoading: Boolean = false,
)

/**
 * "Card/Application": the user's application to a role of a project, with its status. A pending one has
 * "Отозвать отклик" and the chat with the initiator; decided ones are the title, the chip and the line of the role.
 */
@Composable
fun ApplicationCard(
    model: ApplicationCardUiModel,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onActionClick: () -> Unit = {},
    onChatClick: () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    AppCard(modifier, onClick = onClick, spacing = 8.dp) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = model.title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
            )
            model.status?.let { AppStatusChip(it.text, it.tone, showIcon = false) }
        }
        if (model.subtitle.isNotBlank()) {
            Text(model.subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        if (model.actionText != null || model.showChatButton) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (model.actionText != null) {
                    AppButton(
                        text = model.actionText,
                        onClick = onActionClick,
                        modifier = Modifier.weight(1f),
                        variant = AppButtonVariant.OutlinedError,
                        loading = model.actionLoading,
                    )
                }
                if (model.showChatButton) ChatIconButton(onChatClick)
            }
        }
    }
}
