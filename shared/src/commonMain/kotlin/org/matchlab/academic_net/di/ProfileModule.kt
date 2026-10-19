package org.matchlab.academic_net.di

import me.tatarka.inject.annotations.Provides
import org.matchlab.academic_net.feature.auth.domain.Session
import org.matchlab.academic_net.feature.profile.data.ProfileDataSource
import org.matchlab.academic_net.feature.profile.data.ProfileRepositoryImpl
import org.matchlab.academic_net.feature.profile.domain.MyProfileRepository
import org.matchlab.academic_net.feature.profile.domain.ProfileRepository
import org.matchlab.academic_net.feature.profile.domain.usecase.AddDocumentUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.AddWorkUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.ChangeAvatarUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.DeletePortfolioItemUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.GetMyDocumentUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.GetMyWorkUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.GetReviewUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.GetReviewsPageUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.GetUserProfileUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.GetUserReviewsUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.GetUserWorksUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.MarkResultsSeenUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.ObserveHasUnseenResultsUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.ObserveMyProfileUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.ObserveVerificationResultsUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.RefreshMyProfileUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.SaveDocumentUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.SaveWorkUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.UpdateProfileUseCase
import org.matchlab.academic_net.feature.profile.ui.ProfileUiDependencies

/**
 * The profile feature. One repository object serves both interfaces, so what the user adds to their own profile is what
 * other screens read back. It knows the viewer by the id of the session: their own profile is read from the owner's side
 * of the API, and the reviews they wrote are marked.
 */
internal interface ProfileModule {
    @Provides
    @SessionScope
    fun profileRepositoryImpl(source: ProfileDataSource, session: Session): ProfileRepositoryImpl =
        ProfileRepositoryImpl(source, viewerId = session.userId)

    @Provides
    fun profileRepository(impl: ProfileRepositoryImpl): ProfileRepository = impl

    @Provides
    fun myProfileRepository(impl: ProfileRepositoryImpl): MyProfileRepository = impl

    /** The screens read `capabilities` to hide what the Gateway cannot give yet (API-GAP G12: the states of records). */
    @Provides
    fun profileUiDependencies(
        profiles: ProfileRepository,
        mine: MyProfileRepository,
    ): ProfileUiDependencies = ProfileUiDependencies(
        getUserProfile = GetUserProfileUseCase(profiles),
        getUserWorks = GetUserWorksUseCase(profiles),
        getUserReviews = GetUserReviewsUseCase(profiles),
        getReviewsPage = GetReviewsPageUseCase(profiles),
        getReview = GetReviewUseCase(profiles),
        observeMyProfile = ObserveMyProfileUseCase(mine),
        refreshMyProfile = RefreshMyProfileUseCase(mine),
        updateProfile = UpdateProfileUseCase(mine),
        changeAvatar = ChangeAvatarUseCase(mine),
        addWork = AddWorkUseCase(mine),
        addDocument = AddDocumentUseCase(mine),
        getMyWork = GetMyWorkUseCase(mine),
        getMyDocument = GetMyDocumentUseCase(mine),
        saveWork = SaveWorkUseCase(mine),
        saveDocument = SaveDocumentUseCase(mine),
        deletePortfolioItem = DeletePortfolioItemUseCase(mine),
        observeVerificationResults = ObserveVerificationResultsUseCase(mine),
        observeHasUnseenResults = ObserveHasUnseenResultsUseCase(mine),
        markResultsSeen = MarkResultsSeenUseCase(mine),
        capabilities = profiles.capabilities,
    )
}
