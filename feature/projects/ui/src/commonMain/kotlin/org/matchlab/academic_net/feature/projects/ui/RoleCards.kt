package org.matchlab.academic_net.feature.projects.ui

import androidx.compose.foundation.layout.Arrangement
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
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.components.AppButton
import org.matchlab.academic_net.components.AppButtonVariant
import org.matchlab.academic_net.components.AppCard
import org.matchlab.academic_net.components.AppIcons
import org.matchlab.academic_net.components.AppTagChip
import org.matchlab.academic_net.components.Avatar
import org.matchlab.academic_net.components.CardIconButton
import org.matchlab.academic_net.components.ChatIconButton
import org.matchlab.academic_net.feature.projects.ui.generated.resources.Res
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_apply
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_delete
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_edit
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_find_people
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_kick

// Role cards use 12dp corners and 16x12 padding.

@Immutable
data class RoleCardUiModel(
    val role: String,
    val memberName: String,
    val memberAvatar: DrawableResource,
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
        RoleTitle(model.role)
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

@Immutable
data class VacantRoleCardUiModel(
    val role: String,
    val skills: List<String>,
    /** Delete and edit actions, shown to the project initiator. */
    val isEditable: Boolean = false,
    /** Search for people for this role: the only way to it, shown to the project initiator only. */
    val canFindPeople: Boolean = false,
)

/** "Card/Role" (vacant): open role with the skills it needs. The card itself is not clickable. */
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
        RoleTitle(model.role)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Skills(model.skills, Modifier.weight(1f))
            if (model.isEditable) {
                CardIconButton(
                    icon = AppIcons.Trash,
                    contentDescription = stringResource(Res.string.projects_delete),
                    onClick = onDeleteClick,
                    containerColor = colors.errorContainer,
                    contentColor = colors.error,
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

@Immutable
data class RoleApplyCardUiModel(
    val role: String,
    val skills: List<String>,
)

/** "Card/Role apply": open role for someone outside the project, with a full-width apply button. */
@Composable
fun RoleApplyCard(
    model: RoleApplyCardUiModel,
    onApplyClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier, spacing = 12.dp, cornerRadius = 12.dp, verticalPadding = 12.dp) {
        RoleTitle(model.role)
        Skills(model.skills)
        AppButton(
            text = stringResource(Res.string.projects_apply),
            onClick = onApplyClick,
            modifier = Modifier.fillMaxWidth(),
            variant = AppButtonVariant.Tonal,
        )
    }
}

@Composable
private fun RoleTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun Skills(skills: List<String>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        skills.forEach { AppTagChip(it) }
    }
}
