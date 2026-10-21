package org.matchlab.academic_net.feature.projects.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
 * "Card/Participant manage": a member of the project on the management screen: the person (a 64dp avatar centered on the
 * row, as on "Card/Person", with the chat button at its end when [PersonCardUiModel.showChatButton]) and "Выгнать" under it.
 *
 * @param onClick the card itself, e.g. the way to the profile of the member; `null` makes it no button (the chat and kick
 *  buttons work on their own)
 * @param removing the member is being removed: "Выгнать" shows an arc in the error color instead of its label and takes
 *  no taps (the design's `showSpinner`)
 */
@Composable
fun ParticipantManageCard(
    person: PersonCardUiModel,
    onKickClick: () -> Unit,
    modifier: Modifier = Modifier,
    onChatClick: () -> Unit = {},
    onClick: (() -> Unit)? = null,
    removing: Boolean = false,
) {
    AppCard(modifier, onClick = onClick, spacing = 12.dp) {
        PersonSummary(model = person, onChatClick = onChatClick)
        AppButton(
            text = stringResource(Res.string.projects_kick),
            onClick = onKickClick,
            modifier = Modifier.fillMaxWidth(),
            variant = AppButtonVariant.OutlinedError,
            loading = removing,
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
