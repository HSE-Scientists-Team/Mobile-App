package org.matchlab.academic_net.di

import me.tatarka.inject.annotations.Provides
import org.matchlab.academic_net.feature.auth.domain.Session
import org.matchlab.academic_net.feature.feed.data.FeedDataSource
import org.matchlab.academic_net.feature.feed.data.FeedRepositoryImpl
import org.matchlab.academic_net.feature.feed.domain.FeedRepository
import org.matchlab.academic_net.feature.feed.domain.usecase.GetRecommendedPeopleUseCase
import org.matchlab.academic_net.feature.feed.ui.FeedUiDependencies

/** The feed feature: its repository, use cases and what its screen receives. */
internal interface FeedModule {
    @Provides
    @SessionScope
    fun feedRepository(source: FeedDataSource): FeedRepository = FeedRepositoryImpl(source)

    /** The header draws the avatar of the signed-in user by the id of the session, without a request. */
    @Provides
    fun feedUiDependencies(repository: FeedRepository, session: Session): FeedUiDependencies = FeedUiDependencies(
        viewerId = session.userId,
        getRecommendedPeople = GetRecommendedPeopleUseCase(repository),
    )
}
