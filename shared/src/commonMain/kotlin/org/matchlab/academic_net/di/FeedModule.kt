package org.matchlab.academic_net.di

import me.tatarka.inject.annotations.Provides
import org.matchlab.academic_net.feature.feed.data.FeedDataSource
import org.matchlab.academic_net.feature.feed.data.FeedRepositoryImpl
import org.matchlab.academic_net.feature.feed.domain.FeedRepository
import org.matchlab.academic_net.feature.feed.domain.usecase.GetRecommendedPeopleUseCase
import org.matchlab.academic_net.feature.feed.domain.usecase.GetViewerUseCase
import org.matchlab.academic_net.feature.feed.ui.FeedUiDependencies

/** The feed feature: its repository, use cases and what its screen receives. */
internal interface FeedModule {
    @Provides
    @AppScope
    fun feedRepository(source: FeedDataSource): FeedRepository = FeedRepositoryImpl(source)

    @Provides
    fun feedUiDependencies(repository: FeedRepository): FeedUiDependencies = FeedUiDependencies(
        getViewer = GetViewerUseCase(repository),
        getRecommendedPeople = GetRecommendedPeopleUseCase(repository),
    )
}
