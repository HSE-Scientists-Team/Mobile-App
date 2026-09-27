package org.matchlab.academic_net.components

import academicnet.components.generated.resources.Res
import academicnet.components.generated.resources.components_edit
import academicnet.components.generated.resources.components_remove
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource

// Role cards and the stat card use 12dp corners and 16x12 padding ("Card/Role", "Card/Role apply").

/** "Card/Role" (state=occupied): filled role with its member, kick and message actions. */
@Composable
fun RoleCard(
    role: String,
    memberName: String,
    memberAvatar: Painter,
    modifier: Modifier = Modifier,
    kickText: String? = null,
    onKick: (() -> Unit)? = null,
    onMessage: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    CardSurface(modifier, spacing = 8.dp, cornerRadius = 12.dp, verticalPadding = 12.dp) {
        RoleTitle(role)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(memberAvatar, contentDescription = null, size = 32.dp)
            Text(memberName, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
        }
        if ((kickText != null && onKick != null) || onMessage != null) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (kickText != null && onKick != null) {
                    AppButton(kickText, onKick, variant = AppButtonVariant.OutlinedError)
                }
                Spacer(Modifier.weight(1f))
                if (onMessage != null) MessageIconButton(onMessage)
            }
        }
    }
}

/** "Card/Role" (vacant): open role with required skills; delete and edit for the initiator. */
@Composable
fun VacantRoleCard(
    role: String,
    skills: List<String>,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    CardSurface(modifier, spacing = 8.dp, cornerRadius = 12.dp, verticalPadding = 12.dp) {
        RoleTitle(role)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            FlowRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                skills.forEach { AppTagChip(it) }
            }
            if (onDelete != null) {
                CardIconButton(
                    AppIcons.Trash,
                    stringResource(Res.string.components_remove),
                    colors.errorContainer,
                    colors.error,
                    onDelete,
                )
            }
            if (onEdit != null) {
                CardIconButton(
                    AppIcons.Pencil,
                    stringResource(Res.string.components_edit),
                    colors.primaryContainer,
                    colors.primary,
                    onEdit,
                )
            }
        }
    }
}

/** "Card/Role apply": open role for someone outside the project, with a full-width apply button. */
@Composable
fun RoleApplyCard(
    role: String,
    skills: List<String>,
    applyText: String,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CardSurface(modifier, spacing = 12.dp, cornerRadius = 12.dp, verticalPadding = 12.dp) {
        RoleTitle(role)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            skills.forEach { AppTagChip(it) }
        }
        AppButton(applyText, onApply, Modifier.fillMaxWidth(), variant = AppButtonVariant.Tonal)
    }
}

/** "Card/Stat": counter on the project management screen. */
@Composable
fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    CardSurface(modifier, spacing = 2.dp, cornerRadius = 12.dp, horizontalPadding = 12.dp) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = colors.primary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
    }
}

@Composable
private fun RoleTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
}
