package org.matchlab.academic_net.feature.chats.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Immutable
data class ChatBubbleUiModel(
    val text: String,
    val time: String,
    val isOutgoing: Boolean,
)

/** "Chat/Bubble": 280dp message bubble with the time under it. */
@Composable
fun ChatBubble(model: ChatBubbleUiModel, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (model.isOutgoing) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val bubble = if (model.isOutgoing) {
            Modifier.background(colors.primary, shape).padding(horizontal = 14.dp, vertical = 10.dp)
        } else {
            // The incoming outline adds 1dp on each side, as in Figma.
            Modifier
                .background(colors.surface, shape)
                .border(1.dp, colors.outlineVariant, shape)
                .padding(horizontal = 15.dp, vertical = 11.dp)
        }
        Box(Modifier.width(280.dp).then(bubble)) {
            Text(
                text = model.text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (model.isOutgoing) colors.onPrimary else colors.onSurface,
            )
        }
        Text(model.time, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
    }
}
