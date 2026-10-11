package org.matchlab.academic_net.feature.chats.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.components.AppBasicTextField
import org.matchlab.academic_net.components.AppIcon
import org.matchlab.academic_net.components.AppIcons
import org.matchlab.academic_net.feature.chats.ui.generated.resources.Res
import org.matchlab.academic_net.feature.chats.ui.generated.resources.chats_attach
import org.matchlab.academic_net.feature.chats.ui.generated.resources.chats_message_placeholder
import org.matchlab.academic_net.feature.chats.ui.generated.resources.chats_send

@Immutable
data class ChatComposerUiModel(
    val message: String = "",
)

/** "Chat/Composer": message field with an attach action and a send button. */
@Composable
fun ChatComposer(
    model: ChatComposerUiModel,
    onMessageChange: (String) -> Unit,
    onSendClick: () -> Unit,
    modifier: Modifier = Modifier,
    // `null` hides the attach icon: sending files is not available yet.
    onAttachClick: (() -> Unit)? = null,
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
        AppBasicTextField(
            value = model.message,
            onValueChange = onMessageChange,
            modifier = Modifier.weight(1f),
            maxLines = 4,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            decorationBox = { innerTextField ->
                // The 1dp outline adds to the size, as in Figma, so the padding is 1dp larger.
                Row(
                    Modifier
                        .background(colors.surface, CircleShape)
                        .border(1.dp, colors.outlineVariant, CircleShape)
                        .padding(horizontal = 17.dp, vertical = 13.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onAttachClick != null) {
                        AppIcon(
                            icon = AppIcons.Upload,
                            contentDescription = stringResource(Res.string.chats_attach),
                            modifier = Modifier.clickable(
                                interactionSource = null,
                                indication = ripple(bounded = false, radius = 20.dp),
                                role = Role.Button,
                                onClick = onAttachClick,
                            ),
                            size = 20.dp,
                            tint = colors.onSurfaceVariant,
                        )
                    }
                    Box(Modifier.weight(1f)) {
                        if (model.message.isEmpty()) {
                            Text(
                                text = stringResource(Res.string.chats_message_placeholder),
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                            )
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
                .clickable(role = Role.Button, onClick = onSendClick)
                .padding(12.dp),
        ) {
            AppIcon(
                icon = AppIcons.Send,
                contentDescription = stringResource(Res.string.chats_send),
                size = 20.dp,
                tint = colors.onPrimary,
            )
        }
    }
}
