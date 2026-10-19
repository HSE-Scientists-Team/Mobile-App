package org.matchlab.academic_net.components

import academicnet.components.generated.resources.Res
import academicnet.components.generated.resources.components_message
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.components.AvatarSource

/**
 * What [PersonCard] and [PersonSummary] show about a person.
 *
 * @property avatar the picture of the person; `null` draws the [UnknownAvatar] (a person whose profile is not known)
 * @property subtitle the line under the name, e.g. "Инициатор · Stanford University"; it wraps, and it is not drawn while
 * it is blank
 * @property stats extra line under the subtitle, e.g. "★ 4.9 · 12 завершённых проектов"
 * @property showChatButton the chat button at the end of the row
 * @property chatContentDescription what a screen reader says for the chat button; the generic
 * "Написать" when `null`
 */
@Immutable
data class PersonCardUiModel(
    val name: String,
    val subtitle: String,
    val avatar: AvatarSource?,
    val stats: String? = null,
    val showChatButton: Boolean = false,
    val chatContentDescription: String? = null,
)

/**
 * "Card/Person", the universal person card (it was "Card/Initiator" before the design update):
 * a 64dp avatar, the name (one line, ellipsized), the subtitle and, when asked, the rating line and the chat
 * button, all centered vertically on the avatar (see [PersonSummary]). For a project initiator or member, a
 * report subject, a review author. The user's own card "Card/Self" (avatar, name, subtitle) is not used by any
 * screen yet.
 *
 * With [onClick] the whole card is clickable (a burst of taps runs it once, see [AppCard]); the chat
 * button runs [onChatClick] on its own.
 */
@Composable
fun PersonCard(
    model: PersonCardUiModel,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onChatClick: () -> Unit = {},
) {
    AppCard(modifier, onClick = onClick) {
        PersonSummary(model, onChatClick)
    }
}

/**
 * Content of [PersonCard], for cards that add their own actions under it: the avatar, the text
 * column (the name, the subtitle, the optional rating line) and, with
 * [PersonCardUiModel.showChatButton], the chat button at the end of the row.
 *
 * The row is the design's "who": a 64dp avatar, 12dp between the parts, and the avatar, the text and
 * the button all centered vertically on each other. A card is 98dp tall while the text is not taller
 * than the avatar; a subtitle of two lines together with the rating line makes it taller.
 *
 * @param avatarSize side of the round avatar; the 64dp of "Card/Person" (48dp before the design update
 * of 2026-10-17) unless a card still keeps the older layout, see `ParticipantManageCard`
 * @param verticalAlignment how the avatar, the text column and the chat button sit against each
 * other; centered as in "Card/Person". [Alignment.Bottom] is the pre-redesign layout, where the avatar
 * and the button sank to the last line of the text
 */
@Composable
fun PersonSummary(
    model: PersonCardUiModel,
    onChatClick: () -> Unit,
    modifier: Modifier = Modifier,
    avatarSize: Dp = 64.dp,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = verticalAlignment,
    ) {
        if (model.avatar != null) {
            Avatar(model.avatar, contentDescription = null, size = avatarSize)
        } else {
            UnknownAvatar(size = avatarSize)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            PersonName(model.name, MaterialTheme.typography.titleMedium)
            // A person without a subtitle (a team member whose role is unknown) has no empty line under the name.
            if (model.subtitle.isNotBlank()) {
                Text(model.subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            if (model.stats != null) {
                Text(model.stats, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }
        if (model.showChatButton) {
            ChatIconButton(
                onClick = onChatClick,
                contentDescription = model.chatContentDescription ?: stringResource(Res.string.components_message),
            )
        }
    }
}
