package org.matchlab.academic_net.feature.projects.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.matchlab.academic_net.components.AcceptDeclineActions
import org.matchlab.academic_net.components.AppCard
import org.matchlab.academic_net.components.AppTagChip
import org.matchlab.academic_net.components.Avatar
import org.matchlab.academic_net.components.PersonName

/**
 * What [CandidateCard] shows.
 *
 * @property name the candidate's name; an empty one is shown as "Без имени" (see [PersonName])
 * @property subtitle the line under the name, e.g. "Stanford · 3 курс бакалавриата · CS"
 * @property note highlighted line, e.g. "Совпадение 92% · отклик 2 дня назад"; `null` for none
 * @property tags the candidate's skills as chips
 * @property showActions accept, decline and chat buttons for an application to a role
 */
@Immutable
data class CandidateCardUiModel(
    val name: String,
    val subtitle: String,
    val avatar: DrawableResource,
    val note: String? = null,
    val tags: List<String> = emptyList(),
    val showActions: Boolean = false,
)

/** "Card/Candidate": person who applied to a role of the project. */
@Composable
fun CandidateCard(
    model: CandidateCardUiModel,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onAcceptClick: () -> Unit = {},
    onDeclineClick: () -> Unit = {},
    onChatClick: () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    AppCard(modifier, onClick = onClick, spacing = 12.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(model.avatar, contentDescription = null, size = 48.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                PersonName(model.name, MaterialTheme.typography.titleMedium)
                Text(model.subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                if (model.note != null) {
                    Text(model.note, style = MaterialTheme.typography.labelSmall, color = colors.primary)
                }
            }
        }
        if (model.tags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                model.tags.forEach { AppTagChip(it) }
            }
        }
        if (model.showActions) {
            AcceptDeclineActions(onAcceptClick, onDeclineClick, onChatClick)
        }
    }
}
