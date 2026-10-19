package org.matchlab.academic_net.feature.responses.ui

import kotlinx.datetime.TimeZone
import org.matchlab.academic_net.feature.responses.domain.usecase.ApplyToRoleUseCase
import org.matchlab.academic_net.feature.responses.domain.usecase.DecideApplicationUseCase
import org.matchlab.academic_net.feature.responses.domain.usecase.DecideInvitationUseCase
import org.matchlab.academic_net.feature.responses.domain.usecase.GetInviteOptionsUseCase
import org.matchlab.academic_net.feature.responses.domain.usecase.GetRoleToApplyUseCase
import org.matchlab.academic_net.feature.responses.domain.usecase.InviteToProjectUseCase
import org.matchlab.academic_net.feature.responses.domain.usecase.MarkInvitationsSeenUseCase
import org.matchlab.academic_net.feature.responses.domain.usecase.ObserveHasNewInvitationsUseCase
import org.matchlab.academic_net.feature.responses.domain.usecase.ObserveMyResponsesUseCase
import org.matchlab.academic_net.feature.responses.domain.usecase.ObserveProjectResponsesUseCase
import org.matchlab.academic_net.feature.responses.domain.usecase.RefreshMyResponsesUseCase
import org.matchlab.academic_net.feature.responses.domain.usecase.RefreshProjectResponsesUseCase
import org.matchlab.academic_net.feature.responses.domain.usecase.WithdrawApplicationUseCase
import kotlin.time.Clock

/**
 * What the screens of the responses feature need from the outside; the app creates it once and hands it to every
 * `…Route`, to the two sheets and to [responsesTabHasBadge].
 *
 * @param clock and [timeZone] say how many days ago an application was made ("отклик 2 дня назад")
 */
class ResponsesUiDependencies(
    val observeMyResponses: ObserveMyResponsesUseCase,
    val refreshMyResponses: RefreshMyResponsesUseCase,
    val withdrawApplication: WithdrawApplicationUseCase,
    val decideInvitation: DecideInvitationUseCase,
    val observeHasNewInvitations: ObserveHasNewInvitationsUseCase,
    val markInvitationsSeen: MarkInvitationsSeenUseCase,
    val getRoleToApply: GetRoleToApplyUseCase,
    val applyToRole: ApplyToRoleUseCase,
    val observeProjectResponses: ObserveProjectResponsesUseCase,
    val refreshProjectResponses: RefreshProjectResponsesUseCase,
    val decideApplication: DecideApplicationUseCase,
    val getInviteOptions: GetInviteOptionsUseCase,
    val inviteToProject: InviteToProjectUseCase,
    val clock: Clock = Clock.System,
    val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {
    /** Whether 15 is on the screen: the dot of the tab is off while it is (like `MyProfileVisibility`). */
    internal val visibility: ResponsesVisibility = ResponsesVisibility()

    /** The list for the dot of the tab has been asked for in this session: it is read once, not at every composition. */
    internal var tabListRequested: Boolean = false
}
