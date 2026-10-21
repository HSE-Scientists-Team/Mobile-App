package org.matchlab.academic_net.feature.responses.ui

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.ProjectJoinType
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
import org.matchlab.academic_net.feature.responses.domain.MyJoinsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The dot of the tab "Отклики": new invitations, off while 15 is on the screen, silent on a failure. */
class ResponsesTabBadgeTest {
    private val joins = FakeMyJoins(emptyList())
    private val projects = FakeProjectSource()
    private val projectJoins = FakeProjectJoins()

    private fun dependencies(mine: MyJoinsRepository = joins) = ResponsesUiDependencies(
        observeMyResponses = ObserveMyResponsesUseCase(mine, projects),
        refreshMyResponses = RefreshMyResponsesUseCase(mine),
        withdrawApplication = WithdrawApplicationUseCase(mine),
        decideInvitation = DecideInvitationUseCase(mine, projects),
        observeHasNewInvitations = ObserveHasNewInvitationsUseCase(mine),
        markInvitationsSeen = MarkInvitationsSeenUseCase(mine),
        getRoleToApply = GetRoleToApplyUseCase(projects),
        applyToRole = ApplyToRoleUseCase(mine),
        observeProjectResponses = ObserveProjectResponsesUseCase(projectJoins, projects),
        refreshProjectResponses = RefreshProjectResponsesUseCase(projectJoins, projects),
        decideApplication = DecideApplicationUseCase(projectJoins, projects),
        getInviteOptions = GetInviteOptionsUseCase(projects),
        inviteToProject = InviteToProjectUseCase(projectJoins),
    )

    private val newInvitation = join("i", type = ProjectJoinType.Invitation, createdAt = at(100))

    @Test
    fun theDotIsOnForANewInvitationWhileTheTabIsNotShown() = runTest {
        joins.joins.value = listOf(newInvitation)

        assertTrue(dependencies().observeTabBadge().first())
    }

    @Test
    fun theDotIsOffWhileTheTabIsShownAndComesBackWhenItIsLeft() = runTest {
        joins.joins.value = listOf(newInvitation)
        val dependencies = dependencies()

        dependencies.visibility.onShown()
        assertFalse(dependencies.observeTabBadge().first())

        dependencies.visibility.onHidden()
        assertTrue(dependencies.observeTabBadge().first())
    }

    @Test
    fun theDotIsOffOnceTheInvitationIsSeen() = runTest {
        joins.joins.value = listOf(newInvitation)
        joins.seenAt.value = at(100)

        assertFalse(dependencies().observeTabBadge().first())
    }

    @Test
    fun aFailureOfTheListIsNoDot() = runTest {
        val failing = object : MyJoinsRepository by joins {
            override fun observeMyJoins() = flow<List<org.matchlab.academic_net.common.model.ProjectJoin>?> {
                throw IllegalStateException("boom")
            }
        }

        assertFalse(dependencies(failing).observeTabBadge().first())
    }

    @Test
    fun theVisibilityIsIdempotent() {
        val dependencies = dependencies()

        dependencies.visibility.onShown()
        dependencies.visibility.onShown()
        assertTrue(dependencies.visibility.isVisible.value)
        dependencies.visibility.onHidden()
        dependencies.visibility.onHidden()
        assertEquals(false, dependencies.visibility.isVisible.value)
    }
}
