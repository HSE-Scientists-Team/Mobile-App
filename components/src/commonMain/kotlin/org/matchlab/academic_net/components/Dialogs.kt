package org.matchlab.academic_net.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Confirmation of an action that is hard to undo. The design has no dialog: this is the Material 3
 * alert dialog dressed in the theme and the design's text buttons.
 */
@Composable
fun AppConfirmDialog(
    title: String,
    text: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        confirmButton = { AppButton(confirmText, onConfirm, variant = AppButtonVariant.Text) },
        dismissButton = { AppButton(dismissText, onDismiss, variant = AppButtonVariant.Text) },
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = { Text(text, style = MaterialTheme.typography.bodyLarge) },
        containerColor = colors.surface,
        titleContentColor = colors.onSurface,
        textContentColor = colors.onSurfaceVariant,
    )
}
