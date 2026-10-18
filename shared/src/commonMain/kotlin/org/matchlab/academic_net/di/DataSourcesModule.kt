package org.matchlab.academic_net.di

import me.tatarka.inject.annotations.Provides
import org.matchlab.academic_net.feature.auth.domain.Session
import org.matchlab.academic_net.feature.chats.data.ChatsDataSource
import org.matchlab.academic_net.feature.chats.data.mock.MockChatsDataSource
import org.matchlab.academic_net.feature.collaborators.data.CollaboratorsDataSource
import org.matchlab.academic_net.feature.collaborators.data.GatewayCollaboratorsDataSource
import org.matchlab.academic_net.feature.feed.data.FeedDataSource
import org.matchlab.academic_net.feature.feed.data.GatewayFeedDataSource
import org.matchlab.academic_net.feature.profile.data.GatewayProfileDataSource
import org.matchlab.academic_net.feature.profile.data.ProfileDataSource
import org.matchlab.academic_net.feature.projectdetails.data.GatewayProjectDetailsDataSource
import org.matchlab.academic_net.feature.projectdetails.data.ProjectDetailsDataSource
import org.matchlab.academic_net.feature.projects.data.GatewayProjectsDataSource
import org.matchlab.academic_net.feature.projects.data.ProjectsDataSource
import org.matchlab.academic_net.feature.projectsearch.data.GatewayProjectSearchDataSource
import org.matchlab.academic_net.feature.projectsearch.data.ProjectSearchDataSource
import org.matchlab.academic_net.feature.settings.data.GatewaySettingsDataSource
import org.matchlab.academic_net.feature.settings.data.SettingsDataSource
import org.matchlab.academic_net.gateway.GatewaySession
import org.matchlab.academic_net.gateway.mapping.DirectoryProjectAssembler
import org.matchlab.academic_net.gateway.mapping.ProjectAssembler

/**
 * Where the data source of every feature comes from, for one session: this is the only place that picks the sources.
 * The sign-in is not here, it is process-wide (see [AuthModule]).
 *
 * Every source is [SessionScope]d, so it is made when the user signs in and is gone when they sign out: the next
 * account never sees the caches, the drafts and the local chats of the previous one.
 *
 * **Sources.** The profiles, the settings, the people search, the feed and the projects (their list, the search and the
 * page of a project) are the Gateway's and have no mock: their sources are made over the [GatewaySession] of the
 * session. Only the chats run on their local mock (API-GAP G50: the API has none). The Gateway sources take
 * `session.userId`.
 */
internal interface DataSourcesModule {
    /** API-GAP G47: a project names its owner by id only; owners are read through the session's directory. */
    @Provides
    @SessionScope
    fun projectAssembler(gateway: GatewaySession, session: Session): ProjectAssembler =
        DirectoryProjectAssembler(gateway.users, viewerId = session.userId)

    /** API-GAP G40, G43, G44, G42: creating, "Мои проекты" and unpublishing are off in the source's capabilities. */
    @Provides
    @SessionScope
    fun projectsDataSource(gateway: GatewaySession, assembler: ProjectAssembler): ProjectsDataSource =
        GatewayProjectsDataSource(gateway.projects, assembler)

    /** API-GAP G50: the API has no chats, so they are local: they start empty and messages go nowhere. */
    @Provides
    @SessionScope
    fun chatsDataSource(): ChatsDataSource = MockChatsDataSource()

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

    /** The page of a project (04). API-GAP G40, G41, G43: no type, status or period. */
    @Provides
    @SessionScope
    fun projectDetailsDataSource(gateway: GatewaySession, assembler: ProjectAssembler): ProjectDetailsDataSource =
        GatewayProjectDetailsDataSource(gateway.projectCatalog, assembler)

    /** API-GAP G40, G45: the type and duration filters are off; only `q`, `field` and `tag` are sent. */
    @Provides
    @SessionScope
    fun projectSearchDataSource(gateway: GatewaySession, assembler: ProjectAssembler): ProjectSearchDataSource =
        GatewayProjectSearchDataSource(gateway.projectCatalog, assembler)

    /**
     * The people of the feed (01c). API-GAP G32: random people open to collaboration; G33: the viewer and the repeats
     * are dropped.
     */
    @Provides
    @SessionScope
    fun feedDataSource(gateway: GatewaySession, session: Session): FeedDataSource =
        GatewayFeedDataSource(gateway.recommendations, viewerId = session.userId)

    /** The people search (08, 08b). API-GAP G11: no project-type filter; G31: the role is not sent. */
    @Provides
    @SessionScope
    fun collaboratorsDataSource(gateway: GatewaySession): CollaboratorsDataSource =
        GatewayCollaboratorsDataSource(gateway.peopleSearch)
}
