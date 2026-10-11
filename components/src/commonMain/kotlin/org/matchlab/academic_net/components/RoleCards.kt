package org.matchlab.academic_net.components

import academicnet.components.generated.resources.Res
import academicnet.components.generated.resources.components_role_apply
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource

/** What [RoleApplyCard] shows: the role and the skills it needs. */
@Immutable
data class RoleApplyCardUiModel(
    val role: String,
    val skills: List<String>,
)

/**
 * "Card/Role apply": an open role as seen from outside the project, with the skills it needs.
 * With [onApplyClick] the card ends with a full-width "Откликнуться" button; without it (the
 * default) the card only shows the role, for the pages where applying is not available.
 */
@Composable
fun RoleApplyCard(
    model: RoleApplyCardUiModel,
    modifier: Modifier = Modifier,
    onApplyClick: (() -> Unit)? = null,
) {
    AppCard(modifier, spacing = 12.dp, cornerRadius = 12.dp, verticalPadding = 12.dp) {
        RoleCardTitle(model.role)
        SkillChips(model.skills)
        if (onApplyClick != null) {
            AppButton(
                text = stringResource(Res.string.components_role_apply),
                onClick = onApplyClick,
                modifier = Modifier.fillMaxWidth(),
                variant = AppButtonVariant.Tonal,
            )
        }
    }
}

/** The role name at the top of every role card. */
@Composable
fun RoleCardTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/** The skills of a role as wrapping tag chips, 6dp apart; an empty list draws no chips. */
@Composable
fun SkillChips(skills: List<String>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        skills.forEach { AppTagChip(it) }
    }
}
