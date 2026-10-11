package org.matchlab.academic_net.di

import me.tatarka.inject.annotations.Provides
import org.matchlab.academic_net.feature.chats.data.ChatsDataSource
import org.matchlab.academic_net.feature.chats.data.mock.MockChatsDataSource
import org.matchlab.academic_net.feature.collaborators.data.CollaboratorsDataSource
import org.matchlab.academic_net.feature.collaborators.data.mock.MockCollaboratorsDataSource
import org.matchlab.academic_net.feature.collaborators.domain.RoleRequirementProvider
import org.matchlab.academic_net.feature.feed.data.FeedDataSource
import org.matchlab.academic_net.feature.feed.data.mock.MockFeedDataSource
import org.matchlab.academic_net.feature.projects.data.ProjectsDataSource
import org.matchlab.academic_net.feature.projects.data.mock.MockProjectsDataSource

/**
 * Where every data source comes from. This is the only place that picks the mocks: when the
 * backend appears, a module with the same functions but real sources replaces this one in
 * [AppComponent] and nothing else changes.
 */
internal interface MockDataSourcesModule {
    @Provides
    @AppScope
    fun projectsDataSource(): ProjectsDataSource = MockProjectsDataSource()

    @Provides
    @AppScope
    fun chatsDataSource(): ChatsDataSource = MockChatsDataSource()

    @Provides
    @AppScope
    fun feedDataSource(): FeedDataSource = MockFeedDataSource()

    @Provides
    @AppScope
    fun collaboratorsDataSource(requirements: RoleRequirementProvider): CollaboratorsDataSource =
        MockCollaboratorsDataSource(requirements)
}
