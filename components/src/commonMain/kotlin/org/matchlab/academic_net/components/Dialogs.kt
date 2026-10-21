package org.matchlab.academic_net.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Confirmation of an action that is hard to undo. This is the Material 3 alert dialog dressed in the theme
 * and the design's text buttons (the dialogs of the design, e.g. "Удалить работу?", are drawn after it).
 *
 * @param destructive whether the confirmed action destroys something (deletes a work, signs out, kicks a
 * participant): the confirming button is then written in `error` ([AppButtonVariant.TextError]) instead of
 * `primary`. The dismissing button stays `primary` either way: backing out is the safe choice.
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
    destructive: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        confirmButton = {
            AppButton(
                text = confirmText,
                onClick = onConfirm,
                variant = if (destructive) AppButtonVariant.TextError else AppButtonVariant.Text,
            )
        },
        dismissButton = { AppButton(dismissText, onDismiss, variant = AppButtonVariant.Text) },
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = { Text(text, style = MaterialTheme.typography.bodyLarge) },
        containerColor = colors.surface,
        titleContentColor = colors.onSurface,
        textContentColor = colors.onSurfaceVariant,
    )
}
