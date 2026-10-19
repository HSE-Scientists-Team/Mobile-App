package org.matchlab.academic_net.feature.complaints.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.components.AppBottomSheet
import org.matchlab.academic_net.components.AppButton
import org.matchlab.academic_net.components.AppButtonVariant
import org.matchlab.academic_net.components.AppIcon
import org.matchlab.academic_net.components.AppIcons
import org.matchlab.academic_net.components.SheetHeader
import org.matchlab.academic_net.feature.complaints.domain.ComplaintTarget
import org.matchlab.academic_net.feature.complaints.ui.generated.resources.Res
import org.matchlab.academic_net.feature.complaints.ui.generated.resources.complaints_cancel
import org.matchlab.academic_net.feature.complaints.ui.generated.resources.complaints_entry_confirm
import org.matchlab.academic_net.feature.complaints.ui.generated.resources.complaints_entry_header
import org.matchlab.academic_net.feature.complaints.ui.generated.resources.complaints_entry_project
import org.matchlab.academic_net.feature.complaints.ui.generated.resources.complaints_entry_text
import org.matchlab.academic_net.feature.complaints.ui.generated.resources.complaints_entry_user

/**
 * 09c (a person) and 09j (a project) "Подтверждение действия": the sheet that opens from the flag. One card, "Пожаловаться
 * на пользователя" or "…на проект", says that the complaint goes to a moderator; "Пожаловаться" leads to the form,
 * "Отмена" closes the sheet. The card "Заблокировать" of the design is not here: blocking is a later sprint.
 *
 * @param onReport "Пожаловаться": the host closes the sheet and opens the form
 */
@Composable
fun ComplaintEntrySheet(
    target: ComplaintTarget,
    onReport: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    AppBottomSheet(onDismissRequest = onDismiss) {
        SheetHeader(title = stringResource(Res.string.complaints_entry_header))
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)
                .background(colors.surfaceContainerLow, shape)
                .border(1.dp, colors.outlineVariant, shape)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(AppIcons.Flag, contentDescription = null, size = 24.dp, tint = colors.error)
                Text(
                    text = stringResource(
                        if (target is ComplaintTarget.Project) Res.string.complaints_entry_project else Res.string.complaints_entry_user,
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurface,
                )
            }
            Text(
                text = stringResource(Res.string.complaints_entry_text),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppButton(
                    text = stringResource(Res.string.complaints_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    variant = AppButtonVariant.Outlined,
                )
                AppButton(
                    text = stringResource(Res.string.complaints_entry_confirm),
                    onClick = onReport,
                    modifier = Modifier.weight(1f),
                    variant = AppButtonVariant.FilledError,
                )
            }
        }
    }
}
