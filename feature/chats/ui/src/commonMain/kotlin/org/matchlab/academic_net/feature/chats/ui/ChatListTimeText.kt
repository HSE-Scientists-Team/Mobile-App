package org.matchlab.academic_net.feature.chats.ui

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.components.format.dayMonthText
import org.matchlab.academic_net.feature.chats.ui.generated.resources.Res
import org.matchlab.academic_net.feature.chats.ui.generated.resources.chats_yesterday
import org.matchlab.academic_net.utils.ChatListTime

/**
 * Time of the last message in the chat list, in the language of the app: "10:22" for today,
 * "Вчера" | "Yesterday", or a date such as "9 мар" | "Mar 9" for earlier days.
 */
@Composable
internal fun chatListTimeText(time: ChatListTime): String = when (time) {
    is ChatListTime.Today -> time.time
    ChatListTime.Yesterday -> stringResource(Res.string.chats_yesterday)
    is ChatListTime.Earlier -> dayMonthText(time.date)
}
