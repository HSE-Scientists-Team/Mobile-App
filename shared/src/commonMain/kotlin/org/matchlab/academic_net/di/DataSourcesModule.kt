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
import org.matchlab.academic_net.feature.profile.data.GatewayProfileDataSource
import org.matchlab.academic_net.feature.profile.data.ProfileDataSource
import org.matchlab.academic_net.feature.projectdetails.data.MockProjectDetailsDataSource
import org.matchlab.academic_net.feature.projectdetails.data.ProjectDetailsDataSource
import org.matchlab.academic_net.feature.projects.data.ProjectsDataSource
import org.matchlab.academic_net.feature.projects.data.mock.MockProjectsDataSource
import org.matchlab.academic_net.feature.projectsearch.data.MockProjectSearchDataSource
import org.matchlab.academic_net.feature.projectsearch.data.ProjectSearchDataSource
import org.matchlab.academic_net.feature.settings.data.GatewaySettingsDataSource
import org.matchlab.academic_net.feature.settings.data.SettingsDataSource
import org.matchlab.academic_net.gateway.GatewaySession

/**
 * Where the data source of every feature comes from, for one session: this is the only place that picks the sources.
 * The sign-in is not here, it is process-wide (see [AuthModule]).
 *
 * Every source is [SessionScope]d, so it is made when the user signs in and is gone when they sign out: the next
 * account never sees the caches, the drafts and the mock state of the previous one.
 *
 * **Sources.** The profiles and the settings are the Gateway's and have no mock: their sources are made over the
 * [GatewaySession] of the session, in both backends. In [Backend.Mock] there is no Gateway, so asking for them there
 * fails with the `IllegalStateException` of the Gateway provider (see [GatewayModule]); that mode is for the features
 * that still run on their mocks. A feature that is not connected to the Gateway yet runs on its mock in both modes; those
 * mocks keep the viewer they were written for (`MockAccounts.viewer`, `user-me`): the mock world has people under its
 * own ids only, and an id given by the server would show an empty profile. In the mock mode the id of the session is that
 * same one. The Gateway sources take `session.userId`.
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

    /** "Открыт к сотрудничеству" is a field of the profile, so the settings go through the profile call. */
    @Provides
    @SessionScope
    fun settingsDataSource(gateway: GatewaySession): SettingsDataSource = GatewaySettingsDataSource(gateway.profiles)

    /**
     * The profiles, the works and documents, the reviews. API-GAP G11, G12, G13: what the API does not define is off in
     * the capabilities of the source (no project-type chips, no statuses of records, no role of a review's author).
     */
    @Provides
    @SessionScope
    fun profileDataSource(gateway: GatewaySession): ProfileDataSource =
        GatewayProfileDataSource(gateway.profiles, gateway.achievements, gateway.reviews, gateway.users)

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
