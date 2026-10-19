package org.matchlab.academic_net.feature.projects.ui

import androidx.compose.foundation.layout.Arrangement
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
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.components.AppButton
import org.matchlab.academic_net.components.AppButtonVariant
import org.matchlab.academic_net.components.AppCard
import org.matchlab.academic_net.components.AppIcons
import org.matchlab.academic_net.components.Avatar
import org.matchlab.academic_net.components.AvatarSource
import org.matchlab.academic_net.components.CardIconButton
import org.matchlab.academic_net.components.ChatIconButton
import org.matchlab.academic_net.components.RoleCardTitle
import org.matchlab.academic_net.components.SkillChips
import org.matchlab.academic_net.feature.projects.ui.generated.resources.Res
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_delete
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_edit
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_find_people
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_kick

// Role cards use 12dp corners and 16x12 padding.

@Immutable
data class RoleCardUiModel(
    val role: String,
    val memberName: String,
    val memberAvatar: AvatarSource,
    /** Kick and chat actions, shown to the project initiator. */
    val canManage: Boolean = false,
)

/** "Card/Role" (state=occupied): filled role with its member. */
@Composable
fun RoleCard(
    model: RoleCardUiModel,
    modifier: Modifier = Modifier,
    onKickClick: () -> Unit = {},
    onChatClick: () -> Unit = {},
) {
    AppCard(modifier, spacing = 8.dp, cornerRadius = 12.dp, verticalPadding = 12.dp) {
        RoleCardTitle(model.role)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(model.memberAvatar, contentDescription = null, size = 32.dp)
            Text(
                text = model.memberName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (model.canManage) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppButton(
                    text = stringResource(Res.string.projects_kick),
                    onClick = onKickClick,
                    variant = AppButtonVariant.OutlinedError,
                )
                Spacer(Modifier.weight(1f))
                ChatIconButton(onChatClick)
            }
        }
    }
}

/**
 * What [VacantRoleCard] shows.
 *
 * @property places the line of taken places under the role name, e.g. the result of `rolePlacesText`; `null` for none
 *  (a role of one person with nothing taken needs no line)
 */
@Immutable
data class VacantRoleCardUiModel(
    val role: String,
    val skills: List<String>,
    /** Delete and edit actions, shown to the project initiator. */
    val isEditable: Boolean = false,
    /** With [isEditable]: the trash can be tapped; `false` dims it (a role that has people on it cannot be deleted). */
    val canDelete: Boolean = true,
    /** Search for people for this role: the only way to it, shown to the project initiator only. */
    val canFindPeople: Boolean = false,
    val places: String? = null,
)

/**
 * "Card/Role" (vacant): open role with the skills it needs. The card itself is not clickable. It also stands for a
 * role with no free place whose people the app does not know (API-GAP G52), with the [VacantRoleCardUiModel.places]
 * line telling how many places are taken.
 */
@Composable
fun VacantRoleCard(
    model: VacantRoleCardUiModel,
    modifier: Modifier = Modifier,
    onDeleteClick: () -> Unit = {},
    onEditClick: () -> Unit = {},
    onFindPeopleClick: () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    AppCard(modifier, spacing = 8.dp, cornerRadius = 12.dp, verticalPadding = 12.dp) {
        RoleCardTitle(model.role, places = model.places)
        // A role with no skills and no buttons has nothing under its name: no empty row, which would still add its gap.
        if (model.skills.isNotEmpty() || model.isEditable || model.canFindPeople) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                SkillChips(model.skills, Modifier.weight(1f))
                if (model.isEditable) {
                    CardIconButton(
                        icon = AppIcons.Trash,
                        contentDescription = stringResource(Res.string.projects_delete),
                        onClick = onDeleteClick,
                        containerColor = colors.errorContainer,
                        contentColor = colors.error,
                        enabled = model.canDelete,
                    )
                    CardIconButton(
                        icon = AppIcons.Pencil,
                        contentDescription = stringResource(Res.string.projects_edit),
                        onClick = onEditClick,
                        containerColor = colors.primaryContainer,
                        contentColor = colors.primary,
                    )
                }
                if (model.canFindPeople) {
                    CardIconButton(
                        icon = AppIcons.Search,
                        contentDescription = stringResource(Res.string.projects_find_people),
                        onClick = onFindPeopleClick,
                        containerColor = colors.primaryContainer,
                        contentColor = colors.primary,
                    )
                }
            }
        }
    }
}
