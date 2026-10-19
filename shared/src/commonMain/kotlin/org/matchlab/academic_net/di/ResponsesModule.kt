package org.matchlab.academic_net.di

import me.tatarka.inject.annotations.Provides
import org.matchlab.academic_net.ProjectsResponsesProjectSource
import org.matchlab.academic_net.feature.responses.data.ResponsesDataSource
import org.matchlab.academic_net.feature.responses.data.ResponsesRepositoryImpl
import org.matchlab.academic_net.feature.responses.domain.MyJoinsRepository
import org.matchlab.academic_net.feature.responses.domain.ProjectJoinsRepository
import org.matchlab.academic_net.feature.responses.domain.ResponsesProjectSource
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
import org.matchlab.academic_net.feature.responses.ui.ResponsesUiDependencies

/**
 * The responses feature: applications and invitations. One [ResponsesRepositoryImpl] per session serves both sides (the
 * viewer's [MyJoinsRepository] and the initiator's [ProjectJoinsRepository]), so a decision made on one is seen on the
 * other. What the feature needs to know about projects comes from the projects feature through
 * [ProjectsResponsesProjectSource], the one adapter connecting the two.
 */
internal interface ResponsesModule {
    @Provides
    @SessionScope
    fun responsesRepositoryImpl(source: ResponsesDataSource): ResponsesRepositoryImpl = ResponsesRepositoryImpl(source)

    @Provides
    fun myJoinsRepository(impl: ResponsesRepositoryImpl): MyJoinsRepository = impl

    @Provides
    fun projectJoinsRepository(impl: ResponsesRepositoryImpl): ProjectJoinsRepository = impl

    @Provides
    fun responsesProjectSource(adapter: ProjectsResponsesProjectSource): ResponsesProjectSource = adapter

    @Provides
    fun responsesUiDependencies(
        mine: MyJoinsRepository,
        project: ProjectJoinsRepository,
        projects: ResponsesProjectSource,
    ): ResponsesUiDependencies = ResponsesUiDependencies(
        observeMyResponses = ObserveMyResponsesUseCase(mine, projects),
        refreshMyResponses = RefreshMyResponsesUseCase(mine),
        withdrawApplication = WithdrawApplicationUseCase(mine),
        decideInvitation = DecideInvitationUseCase(mine, projects),
        observeHasNewInvitations = ObserveHasNewInvitationsUseCase(mine),
        markInvitationsSeen = MarkInvitationsSeenUseCase(mine),
        getRoleToApply = GetRoleToApplyUseCase(projects),
        applyToRole = ApplyToRoleUseCase(mine),
        observeProjectResponses = ObserveProjectResponsesUseCase(project, projects),
        refreshProjectResponses = RefreshProjectResponsesUseCase(project, projects),
        decideApplication = DecideApplicationUseCase(project, projects),
        getInviteOptions = GetInviteOptionsUseCase(projects),
        inviteToProject = InviteToProjectUseCase(project),
    )
}
