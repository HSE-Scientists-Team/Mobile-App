package org.matchlab.academic_net.feature.profile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.components.AppCard
import org.matchlab.academic_net.components.AppStatusChip
import org.matchlab.academic_net.components.AppStatusChipTone
import org.matchlab.academic_net.components.Avatar
import org.matchlab.academic_net.components.ChatIconButton
import org.matchlab.academic_net.feature.profile.ui.generated.resources.Res
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_message
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_open_to_collaboration

/**
 * One chip under the first row of [ProfileHeaderCard], e.g. "Открыт к сотрудничеству".
 *
 * @property text the words of the chip
 * @property tone the colors of the chip (see [AppStatusChipTone])
 */
@Immutable
data class ProfileStatusUiModel(
    val text: String,
    val tone: AppStatusChipTone,
)

/**
 * What [ProfileHeaderCard] shows. The person has no verification mark: trust is shown on the works and the documents of
 * the profile, not on the person.
 *
 * @property subtitle the line under the name ("Stanford · Бакалавриат, 3 курс · Computer Science", made by
 * `subtitleText`); it wraps, and it is not drawn while it is blank
 * @property statuses chips under the first row, e.g. "Открыт к сотрудничеству"; none for a profile that has nothing to
 * say
 * @property showChatButton the chat icon at the end of the first row: on someone else's profile, not
 * on the viewer's own
 */
@Immutable
data class ProfileHeaderCardUiModel(
    val name: String,
    val subtitle: String,
    val avatar: DrawableResource,
    val statuses: List<ProfileStatusUiModel> = emptyList(),
    val showChatButton: Boolean = false,
)

/**
 * "Card/Profile header": header of the user's own or someone else's profile.
 *
 * The first row is a 64dp avatar, the text column (the name, which wraps to a second line at most and is ellipsized
 * after that, and the subtitle, which wraps) and, with [ProfileHeaderCardUiModel.showChatButton], the round chat icon,
 * all centered vertically on each other. The chips of [ProfileHeaderCardUiModel.statuses] go under it. There is no wide
 * "Написать" button: the icon is the only way to start a chat from here, and "Написать" is what a screen reader says
 * for it.
 *
 * @param onChatClick the chat icon; a burst of taps runs it once
 */
@Composable
fun ProfileHeaderCard(
    model: ProfileHeaderCardUiModel,
    modifier: Modifier = Modifier,
    onChatClick: () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    AppCard(modifier, spacing = 12.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(model.avatar, contentDescription = null, size = 64.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                // A new account has neither a university nor a study: no empty line under the name.
                if (model.subtitle.isNotBlank()) {
                    Text(model.subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
            if (model.showChatButton) {
                ChatIconButton(onClick = onChatClick, contentDescription = stringResource(Res.string.profile_message))
            }
        }
        if (model.statuses.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                model.statuses.forEach { AppStatusChip(it.text, it.tone) }
            }
        }
    }
}

/** The chips under the header of [this] profile: "Открыт к сотрудничеству" when the person said so, else none. */
@Composable
internal fun ProfileUiModel.headerStatuses(): List<ProfileStatusUiModel> =
    if (openToCollaboration) {
        listOf(ProfileStatusUiModel(stringResource(Res.string.profile_open_to_collaboration), AppStatusChipTone.Success))
    } else {
        emptyList()
    }
