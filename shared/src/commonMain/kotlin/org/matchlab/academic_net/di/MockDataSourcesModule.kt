package org.matchlab.academic_net.di

import me.tatarka.inject.annotations.Provides
import org.matchlab.academic_net.feature.auth.data.AuthDataSource
import org.matchlab.academic_net.feature.auth.data.mock.MockAuthDataSource
import org.matchlab.academic_net.feature.chats.data.ChatsDataSource
import org.matchlab.academic_net.feature.chats.data.mock.MockChatsDataSource
import org.matchlab.academic_net.feature.collaborators.data.CollaboratorsDataSource
import org.matchlab.academic_net.feature.collaborators.data.mock.MockCollaboratorsDataSource
import org.matchlab.academic_net.feature.collaborators.domain.RoleRequirementProvider
import org.matchlab.academic_net.feature.feed.data.FeedDataSource
import org.matchlab.academic_net.feature.feed.data.mock.MockFeedDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.matchlab.academic_net.feature.profile.data.ProfileDataSource
import org.matchlab.academic_net.feature.profile.data.mock.MockProfileDataSource
import org.matchlab.academic_net.feature.projectdetails.data.MockProjectDetailsDataSource
import org.matchlab.academic_net.feature.projectdetails.data.ProjectDetailsDataSource
import org.matchlab.academic_net.feature.projects.data.ProjectsDataSource
import org.matchlab.academic_net.feature.settings.data.mock.MockSettingsDataSource
import org.matchlab.academic_net.feature.settings.data.SettingsDataSource
import org.matchlab.academic_net.feature.projects.data.mock.MockProjectsDataSource
import org.matchlab.academic_net.feature.projectsearch.data.MockProjectSearchDataSource
import org.matchlab.academic_net.feature.projectsearch.data.ProjectSearchDataSource

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
    fun authDataSource(): AuthDataSource = MockAuthDataSource()

    @Provides
    @AppScope
    fun settingsDataSource(): SettingsDataSource = MockSettingsDataSource()

    /** The mock moderation runs in a scope that lives as long as the process. */
    @Provides
    @AppScope
    fun profileDataSource(): ProfileDataSource =
        MockProfileDataSource(scope = CoroutineScope(SupervisorJob() + Dispatchers.Default))

    @Provides
    @AppScope
    fun projectDetailsDataSource(): ProjectDetailsDataSource = MockProjectDetailsDataSource()

    @Provides
    @AppScope
    fun projectSearchDataSource(): ProjectSearchDataSource = MockProjectSearchDataSource()

    @Provides
    @AppScope
    fun feedDataSource(): FeedDataSource = MockFeedDataSource()

    @Provides
    @AppScope
    fun collaboratorsDataSource(requirements: RoleRequirementProvider): CollaboratorsDataSource =
        MockCollaboratorsDataSource(requirements)
}
