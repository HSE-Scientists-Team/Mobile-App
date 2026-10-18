package org.matchlab.academic_net.feature.projects.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.components.AppButton
import org.matchlab.academic_net.components.AppButtonVariant
import org.matchlab.academic_net.components.AppCard
import org.matchlab.academic_net.components.PersonCardUiModel
import org.matchlab.academic_net.components.PersonSummary
import org.matchlab.academic_net.feature.projects.ui.generated.resources.Res
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_kick

/**
 * "Card/Participant manage": project member on the management screen, with chat and kick.
 *
 * The person row keeps the layout from before the redesign of "Card/Person" (2026-10-17): a 48dp
 * avatar, and with the chat button the row bottom-aligned. The component in Figma (59523:125) has
 * the same defect and its redesign waits for a decision (docs/sprint-1.md, "Card/Participant
 * manage"); when it is made, drop the two arguments below and the card follows [PersonSummary].
 */
@Composable
fun ParticipantManageCard(
    person: PersonCardUiModel,
    onKickClick: () -> Unit,
    modifier: Modifier = Modifier,
    onChatClick: () -> Unit = {},
) {
    AppCard(modifier, spacing = 12.dp) {
        PersonSummary(
            model = person,
            onChatClick = onChatClick,
            avatarSize = 48.dp,
            verticalAlignment = if (person.showChatButton) Alignment.Bottom else Alignment.CenterVertically,
        )
        AppButton(
            text = stringResource(Res.string.projects_kick),
            onClick = onKickClick,
            modifier = Modifier.fillMaxWidth(),
            variant = AppButtonVariant.OutlinedError,
        )
    }
}

@Immutable
data class StatCardUiModel(
    val value: String,
    val label: String,
)

/** "Card/Stat": counter on the project management screen. */
@Composable
fun StatCard(model: StatCardUiModel, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    AppCard(modifier, spacing = 2.dp, cornerRadius = 12.dp, horizontalPadding = 12.dp) {
        Text(model.value, style = MaterialTheme.typography.headlineSmall, color = colors.primary)
        Text(model.label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
    }
}
