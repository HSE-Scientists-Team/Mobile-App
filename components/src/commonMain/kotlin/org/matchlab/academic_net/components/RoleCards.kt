package org.matchlab.academic_net.components

import academicnet.components.generated.resources.Res
import academicnet.components.generated.resources.components_role_apply
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.components.project.rolePlacesText

/**
 * What [RoleApplyCard] shows: the role, the skills it needs and, for a role of several places, how many are taken.
 *
 * @property occupied places of the role that are taken
 * @property slots places of the role; the line "1 из 3 мест занято" under the title is drawn when it is more than 1
 *  (API-GAP G49: the app creates roles for one person, but the server may have more places)
 */
@Immutable
data class RoleApplyCardUiModel(
    val role: String,
    val skills: List<String>,
    val occupied: Int = 0,
    val slots: Int = 1,
)

/**
 * "Card/Role apply": an open role as seen from outside the project, with the skills it needs (none: no chips) and, when
 * the role has more than one place, the line of taken places under its name (see [rolePlacesText]).
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
        RoleCardTitle(
            text = model.role,
            places = if (model.slots > 1) rolePlacesText(model.occupied, model.slots) else null,
        )
        // A role may need no skills (the server's list is optional): no empty row, which would still add its 12dp gap.
        if (model.skills.isNotEmpty()) SkillChips(model.skills)
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

/**
 * The role name at the top of every role card. With [places], e.g. the result of [rolePlacesText], the line of taken
 * places sits under the name, 2dp apart, in `labelSmall` and `onSurfaceVariant` like the counter of
 * `RolesSectionHeader`.
 *
 * @param places the line under the name; `null` for none
 */
@Composable
fun RoleCardTitle(text: String, modifier: Modifier = Modifier, places: String? = null) {
    val colors = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
        )
        if (places != null) {
            Text(
                text = places,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
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
