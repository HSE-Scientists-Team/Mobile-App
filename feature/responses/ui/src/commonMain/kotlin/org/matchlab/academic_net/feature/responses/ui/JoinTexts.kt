package org.matchlab.academic_net.feature.responses.ui

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.common.model.ProjectJoinStatus
import org.matchlab.academic_net.common.model.ProjectJoinType
import org.matchlab.academic_net.components.AppStatusChipTone
import org.matchlab.academic_net.components.StatusChipUiModel
import org.matchlab.academic_net.feature.responses.ui.generated.resources.Res
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_applied_ago
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_applied_today
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_candidate_applied_ago
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_candidate_applied_today
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_status_accepted
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_status_declined
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_status_invitation_accepted
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_status_invitation_declined
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_status_invitation_withdrawn
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_status_pending
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_status_withdrawn

/**
 * The chip of the status of a record, as the design draws them (15g, 15m, 07p): an application is "Ожидает" (warning),
 * "Принят" (success), "Отклонён" (cancelled), "Отозван" (off); an invitation is "Принято", "Отклонено", "Отозвано". No icons.
 *
 * @param pendingChip whether a record that waits is drawn with "Ожидает". It is on the tab of the viewer's applications
 *  (15g); a waiting invitation and a waiting candidate of 07d have the buttons instead
 * @return `null` for an unknown status, and for a waiting record when [pendingChip] is off
 */
@Composable
internal fun joinStatusChip(
    type: ProjectJoinType?,
    status: ProjectJoinStatus?,
    pendingChip: Boolean = false,
): StatusChipUiModel? {
    val invitation = type == ProjectJoinType.Invitation
    return when (status) {
        null -> null
        ProjectJoinStatus.Pending ->
            if (pendingChip && !invitation) {
                StatusChipUiModel(stringResource(Res.string.responses_status_pending), AppStatusChipTone.Pending)
            } else {
                null
            }
        ProjectJoinStatus.Accepted -> StatusChipUiModel(
            stringResource(
                if (invitation) Res.string.responses_status_invitation_accepted else Res.string.responses_status_accepted,
            ),
            AppStatusChipTone.Success,
        )
        ProjectJoinStatus.Rejected -> StatusChipUiModel(
            stringResource(
                if (invitation) Res.string.responses_status_invitation_declined else Res.string.responses_status_declined,
            ),
            AppStatusChipTone.Cancelled,
        )
        ProjectJoinStatus.Cancelled -> StatusChipUiModel(
            stringResource(
                if (invitation) Res.string.responses_status_invitation_withdrawn else Res.string.responses_status_withdrawn,
            ),
            AppStatusChipTone.Off,
        )
    }
}

/** "отклик сегодня" | "отклик 2 дня назад": the end of the line of an application of the viewer; the days since it was made. */
@Composable
internal fun appliedAgoText(days: Int): String =
    if (days <= 0) {
        stringResource(Res.string.responses_applied_today)
    } else {
        pluralStringResource(Res.plurals.responses_applied_ago, days, days)
    }

/** "Отклик сегодня" | "Отклик 2 дня назад": the note of a candidate on 07d. */
@Composable
internal fun candidateAppliedAgoText(days: Int): String =
    if (days <= 0) {
        stringResource(Res.string.responses_candidate_applied_today)
    } else {
        pluralStringResource(Res.plurals.responses_candidate_applied_ago, days, days)
    }
