package org.matchlab.academic_net.components

import academicnet.components.generated.resources.Res
import academicnet.components.generated.resources.components_attach
import academicnet.components.generated.resources.components_send
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource

/** "Chat/Bubble": 280dp message bubble with the time under it. */
@Composable
fun ChatBubble(
    text: String,
    time: String,
    outgoing: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val bubble = if (outgoing) {
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
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (outgoing) colors.onPrimary else colors.onSurface,
            )
        }
        Text(time, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
    }
}

/** "Chat/Composer": message field with an attach action and a send button. */
@Composable
fun ChatComposer(
    message: String,
    onMessageChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = message,
            onValueChange = onMessageChange,
            modifier = Modifier.weight(1f),
            maxLines = 4,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            decorationBox = { innerTextField ->
                Row(
                    Modifier
                        .background(colors.surface, CircleShape)
                        .border(1.dp, colors.outlineVariant, CircleShape)
                        .padding(horizontal = 17.dp, vertical = 13.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(
                        icon = AppIcons.Upload,
                        contentDescription = stringResource(Res.string.components_attach),
                        modifier = Modifier.clickable(
                            interactionSource = null,
                            indication = ripple(bounded = false, radius = 20.dp),
                            role = Role.Button,
                            onClick = onAttach,
                        ),
                        size = 20.dp,
                        tint = colors.onSurfaceVariant,
                    )
                    Box(Modifier.weight(1f)) {
                        if (message.isEmpty()) {
                            Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant, maxLines = 1)
                        }
                        innerTextField()
                    }
                }
            },
        )
        Box(
            Modifier
                .clip(CircleShape)
                .background(colors.primary)
                .clickable(role = Role.Button, onClick = onSend)
                .padding(12.dp),
        ) {
            AppIcon(
                icon = AppIcons.Send,
                contentDescription = stringResource(Res.string.components_send),
                size = 20.dp,
                tint = colors.onPrimary,
            )
        }
    }
}
