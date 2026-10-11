package org.matchlab.academic_net.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * One quiet line with a lock that tells the user how their data is protected, e.g. under the
 * sign-in form or above a conversation. The design has no such line: it is the 14dp "Lock" icon
 * and a `labelSmall` text in the secondary color, so it never competes with the content.
 *
 * @param text what is guaranteed; keep it to a claim the app can actually keep
 */
@Composable
fun SecurityNote(text: String, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // The icon is as tall as one text line, so it stays at the first line when the text wraps.
        AppIcon(
            icon = AppIcons.Lock,
            contentDescription = null,
            modifier = Modifier.padding(top = 1.dp),
            size = 14.dp,
            tint = color,
        )
        Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = color)
    }
}
