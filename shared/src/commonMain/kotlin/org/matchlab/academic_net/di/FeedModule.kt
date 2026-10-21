package org.matchlab.academic_net.di

import me.tatarka.inject.annotations.Provides
import org.matchlab.academic_net.ProfileViewerNameSource
import org.matchlab.academic_net.feature.auth.domain.Session
import org.matchlab.academic_net.feature.feed.data.FeedDataSource
import org.matchlab.academic_net.feature.feed.data.FeedRepositoryImpl
import org.matchlab.academic_net.feature.feed.domain.FeedRepository
import org.matchlab.academic_net.feature.feed.domain.ViewerNameSource
import org.matchlab.academic_net.feature.feed.domain.usecase.GetRecommendedPeopleUseCase
import org.matchlab.academic_net.feature.feed.domain.usecase.GetRecommendedProjectsUseCase
import org.matchlab.academic_net.feature.feed.domain.usecase.ObserveViewerNameUseCase
import org.matchlab.academic_net.feature.feed.ui.FeedUiDependencies

/** The feed feature: its repository, use cases and what its screen receives. */
internal interface FeedModule {
    @Provides
    @SessionScope
    fun feedRepository(source: FeedDataSource): FeedRepository = FeedRepositoryImpl(source)

    /** The feed gets the name of the signed-in user from the profile feature through [ProfileViewerNameSource]. */
    @Provides
    fun viewerNameSource(adapter: ProfileViewerNameSource): ViewerNameSource = adapter

    /** The header asks for the picture of the signed-in user by the id of the session and shows their initials meanwhile. */
    @Provides
    fun feedUiDependencies(
        repository: FeedRepository,
        viewerName: ViewerNameSource,
        session: Session,
    ): FeedUiDependencies = FeedUiDependencies(
        viewerId = session.userId,
        observeViewerName = ObserveViewerNameUseCase(viewerName),
        getRecommendedPeople = GetRecommendedPeopleUseCase(repository),
        getRecommendedProjects = GetRecommendedProjectsUseCase(repository),
    )
}
