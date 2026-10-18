package org.matchlab.academic_net.di

import me.tatarka.inject.annotations.Provides
import org.matchlab.academic_net.Backend
import org.matchlab.academic_net.feature.chats.data.ChatsDataSource
import org.matchlab.academic_net.feature.chats.data.mock.MockChatsDataSource
import org.matchlab.academic_net.feature.collaborators.data.CollaboratorsDataSource
import org.matchlab.academic_net.feature.collaborators.data.mock.MockCollaboratorsDataSource
import org.matchlab.academic_net.feature.collaborators.domain.RoleRequirementProvider
import org.matchlab.academic_net.feature.feed.data.FeedDataSource
import org.matchlab.academic_net.feature.feed.data.mock.MockFeedDataSource
import org.matchlab.academic_net.feature.profile.data.ProfileDataSource
import org.matchlab.academic_net.feature.profile.data.mock.MockProfileDataSource
import org.matchlab.academic_net.feature.projectdetails.data.MockProjectDetailsDataSource
import org.matchlab.academic_net.feature.projectdetails.data.ProjectDetailsDataSource
import org.matchlab.academic_net.feature.projects.data.ProjectsDataSource
import org.matchlab.academic_net.feature.projects.data.mock.MockProjectsDataSource
import org.matchlab.academic_net.feature.projectsearch.data.MockProjectSearchDataSource
import org.matchlab.academic_net.feature.projectsearch.data.ProjectSearchDataSource
import org.matchlab.academic_net.feature.settings.data.SettingsDataSource
import org.matchlab.academic_net.feature.settings.data.mock.MockSettingsDataSource

/**
 * Where the data source of every feature comes from, for one session: this is the only place that picks the mocks.
 * The sign-in is not here, it is process-wide (see [AuthModule]).
 *
 * Every source is [SessionScope]d, so it is made when the user signs in and is gone when they sign out: the next
 * account never sees the caches, the drafts and the mock state of the previous one.
 *
 * **Modes.** A feature that is connected to the Gateway chooses its source with `when (backend)`: the mock in
 * [Backend.Mock], the Gateway source over `Lazy<GatewaySession>` in [Backend.Gateway] (lazy, so that the mock mode
 * never opens a session client). A feature that is not connected yet runs on its mock in both modes; so far these
 * are all of them. Those mocks keep the viewer they were written for (`MockAccounts.viewer`, `user-me`) in both
 * modes: the mock world has people under its own ids only, and an id given by the server would show an empty
 * profile. In the mock mode the id of the session is that same one. The Gateway sources take `session.userId`.
 */
internal interface DataSourcesModule {
    @Provides
    @SessionScope
    fun projectsDataSource(): ProjectsDataSource = MockProjectsDataSource()

    /**
     * API-GAP G50: the API has no chats, so they are local in both modes and messages go nowhere. The demo chats exist
     * in the mock mode only, because their people do not exist on the server: opening one of them would fail there.
     */
    @Provides
    @SessionScope
    fun chatsDataSource(backend: Backend): ChatsDataSource = MockChatsDataSource(seed = backend == Backend.Mock)

    @Provides
    @SessionScope
    fun settingsDataSource(): SettingsDataSource = MockSettingsDataSource()

    /** The mock moderation runs in the scope of the session, so that it stops when the user signs out. */
    @Provides
    @SessionScope
    fun profileDataSource(resources: SessionResources): ProfileDataSource =
        MockProfileDataSource(scope = resources.scope)

    @Provides
    @SessionScope
    fun projectDetailsDataSource(): ProjectDetailsDataSource = MockProjectDetailsDataSource()

    @Provides
    @SessionScope
    fun projectSearchDataSource(): ProjectSearchDataSource = MockProjectSearchDataSource()

    @Provides
    @SessionScope
    fun feedDataSource(): FeedDataSource = MockFeedDataSource()

    @Provides
    @SessionScope
    fun collaboratorsDataSource(requirements: RoleRequirementProvider): CollaboratorsDataSource =
        MockCollaboratorsDataSource(requirements)
}
