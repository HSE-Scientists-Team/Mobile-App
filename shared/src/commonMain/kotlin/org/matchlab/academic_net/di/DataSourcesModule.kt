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
 * `session.userId`. The values the API leaves open (types, spheres, durations, statuses, months, actions) are the codes
 * the app defines (`docs/api-values.md`), so the capabilities of these sources are on, except the states of works and
 * documents (API-GAP G12).
 */
internal interface DataSourcesModule {
    /** API-GAP G47: a project names its owner by id only; owners are read through the session's directory. */
    @Provides
    @SessionScope
    fun projectAssembler(gateway: GatewaySession, session: Session): ProjectAssembler =
        DirectoryProjectAssembler(gateway.users, viewerId = session.userId)

    /**
     * The viewer's projects: creating, "Я инициатор" (the catalog `GET /projects`, the viewer's own picked out by their
     * owner id) and unpublishing, all on (`docs/api-values.md`).
     */
    @Provides
    @SessionScope
    fun projectsDataSource(
        gateway: GatewaySession,
        assembler: ProjectAssembler,
        session: Session,
    ): ProjectsDataSource =
        GatewayProjectsDataSource(gateway.projects, gateway.projectCatalog, assembler, viewerId = session.userId)

    /** API-GAP G50: the API has no chats, so they are local: they start empty and messages go nowhere. */
    @Provides
    @SessionScope
    fun chatsDataSource(): ChatsDataSource = MockChatsDataSource()

    /** "Открыт к сотрудничеству" is a field of the profile, so the settings go through the profile call. */
    @Provides
    @SessionScope
    fun settingsDataSource(gateway: GatewaySession): SettingsDataSource = GatewaySettingsDataSource(gateway.profiles)

    /**
     * The profiles, the works and documents, the reviews. API-GAP G12, G13: what the API does not define and the app
     * cannot define for itself is off in the capabilities of the source (no statuses of records, no role of a review's
     * author); the project types of a profile are the app's codes and are on (`docs/api-values.md`).
     */
    @Provides
    @SessionScope
    fun profileDataSource(gateway: GatewaySession): ProfileDataSource =
        GatewayProfileDataSource(gateway.profiles, gateway.achievements, gateway.reviews, gateway.users)

    /** The page of a project (04): type, sphere, status and months are read from the app's codes (`docs/api-values.md`). */
    @Provides
    @SessionScope
    fun projectDetailsDataSource(gateway: GatewaySession, assembler: ProjectAssembler): ProjectDetailsDataSource =
        GatewayProjectDetailsDataSource(gateway.projectCatalog, assembler)

    /** The project search: `q`, `field` (the code of the sphere), `tag`, and `type` and `duration` when one chip is chosen. */
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

    /** The people search (08, 08b): `project_type` goes when one type is chosen; API-GAP G31: the role is not sent. */
    @Provides
    @SessionScope
    fun collaboratorsDataSource(gateway: GatewaySession): CollaboratorsDataSource =
        GatewayCollaboratorsDataSource(gateway.peopleSearch)
}
