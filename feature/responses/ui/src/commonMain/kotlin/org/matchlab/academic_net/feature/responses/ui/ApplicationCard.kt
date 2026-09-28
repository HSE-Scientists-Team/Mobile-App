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
import org.matchlab.academic_net.components.ChatIconButton

@Immutable
data class ApplicationCardUiModel(
    val title: String,
    /** Role and status, e.g. "Роль: ML Engineer · отклик 2 дня назад". */
    val subtitle: String,
    /** Outlined action, e.g. "Отозвать отклик"; hidden when null (archive records). */
    val actionText: String? = null,
    val showChatButton: Boolean = false,
)

/** "Card/Application": the user's application, an invitation or an archived record. */
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
        Text(model.title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
        Text(model.subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        if (model.actionText != null || model.showChatButton) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (model.actionText != null) {
                    AppButton(model.actionText, onActionClick, Modifier.weight(1f), variant = AppButtonVariant.Outlined)
                }
                if (model.showChatButton) ChatIconButton(onChatClick)
            }
        }
    }
}
