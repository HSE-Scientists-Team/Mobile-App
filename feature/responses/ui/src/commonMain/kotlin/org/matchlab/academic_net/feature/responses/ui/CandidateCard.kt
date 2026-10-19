package org.matchlab.academic_net.feature.responses.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import org.matchlab.academic_net.components.AppStatusChip
import org.matchlab.academic_net.components.AppTagChip
import org.matchlab.academic_net.components.Avatar
import org.matchlab.academic_net.components.ChatIconButton
import org.matchlab.academic_net.components.PersonName
import org.matchlab.academic_net.components.StatusChipUiModel
import org.matchlab.academic_net.components.UnknownAvatar

/**
 * What [CandidateCard] shows.
 *
 * @property name the candidate's name; an empty one is shown as "Без имени" (see [PersonName])
 * @property subtitle the line under the name, e.g. "Stanford · 3 курс бакалавриата · CS"; not drawn while blank
 * @property avatar the picture; `null` draws the neutral avatar of a person whose profile is unknown
 * @property note highlighted line, e.g. "Отклик 2 дня назад"; `null` for none
 * @property tags the candidate's skills as chips
 * @property status the decision on the record as a "Chip/Status" without an icon ("Принят", "Отклонён") at the start of
 *  the last row; `null` leaves it out (the record waits for a decision)
 * @property showActions accept and decline (the design's `showDecide`), for a record that waits for a decision
 * @property showChat the chat button at the end of the last row
 * @property deciding the decision is on its way: an arc stands in place of accept and decline (`showSpinner`)
 * @property acceptEnabled `false` dims accept: the role has no free place
 */
@Immutable
data class CandidateCardUiModel(
    val name: String,
    val subtitle: String,
    val avatar: DrawableResource?,
    val note: String? = null,
    val tags: List<String> = emptyList(),
    val status: StatusChipUiModel? = null,
    val showActions: Boolean = false,
    val showChat: Boolean = false,
    val deciding: Boolean = false,
    val acceptEnabled: Boolean = true,
)

/** "Card/Candidate": a person who applied to a role of the project, with the decision on it. */
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
            if (model.avatar != null) {
                Avatar(model.avatar, contentDescription = null, size = 48.dp)
            } else {
                UnknownAvatar(size = 48.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                PersonName(model.name, MaterialTheme.typography.titleMedium)
                if (model.subtitle.isNotBlank()) {
                    Text(model.subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
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
            AcceptDeclineActions(
                onAcceptClick = onAcceptClick,
                onDeclineClick = onDeclineClick,
                onChatClick = onChatClick,
                loading = model.deciding,
                acceptEnabled = model.acceptEnabled,
                showChat = model.showChat,
            )
        } else if (model.status != null || model.showChat) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                model.status?.let { AppStatusChip(it.text, it.tone, showIcon = false) }
                Spacer(Modifier.weight(1f))
                if (model.showChat) ChatIconButton(onChatClick)
            }
        }
    }
}
