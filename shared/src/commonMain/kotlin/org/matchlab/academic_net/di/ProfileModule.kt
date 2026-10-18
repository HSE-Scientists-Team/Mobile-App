package org.matchlab.academic_net.di

import me.tatarka.inject.annotations.Provides
import org.matchlab.academic_net.feature.profile.data.ProfileDataSource
import org.matchlab.academic_net.feature.profile.data.ProfileRepositoryImpl
import org.matchlab.academic_net.feature.profile.domain.MyProfileRepository
import org.matchlab.academic_net.feature.profile.domain.ProfileRepository
import org.matchlab.academic_net.feature.profile.domain.usecase.AddDocumentUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.AddWorkUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.GetReviewUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.GetUserProfileUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.GetUserReviewsUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.GetUserWorksUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.MarkResultsSeenUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.ObserveHasUnseenResultsUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.ObserveMyProfileUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.ObserveVerificationResultsUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.UpdateAboutUseCase
import org.matchlab.academic_net.feature.profile.ui.ProfileUiDependencies

/**
 * The profile feature. One repository object serves both interfaces, so what the user adds to
 * their own profile is what other screens read back.
 */
internal interface ProfileModule {
    @Provides
    @SessionScope
    fun profileRepositoryImpl(source: ProfileDataSource): ProfileRepositoryImpl = ProfileRepositoryImpl(source)

    @Provides
    fun profileRepository(impl: ProfileRepositoryImpl): ProfileRepository = impl

    @Provides
    fun myProfileRepository(impl: ProfileRepositoryImpl): MyProfileRepository = impl

    @Provides
    fun profileUiDependencies(
        profiles: ProfileRepository,
        mine: MyProfileRepository,
    ): ProfileUiDependencies = ProfileUiDependencies(
        getUserProfile = GetUserProfileUseCase(profiles),
        getUserWorks = GetUserWorksUseCase(profiles),
        getUserReviews = GetUserReviewsUseCase(profiles),
        getReview = GetReviewUseCase(profiles),
        observeMyProfile = ObserveMyProfileUseCase(mine),
        updateAbout = UpdateAboutUseCase(mine),
        addWork = AddWorkUseCase(mine),
        addDocument = AddDocumentUseCase(mine),
        observeVerificationResults = ObserveVerificationResultsUseCase(mine),
        observeHasUnseenResults = ObserveHasUnseenResultsUseCase(mine),
        markResultsSeen = MarkResultsSeenUseCase(mine),
    )
}
