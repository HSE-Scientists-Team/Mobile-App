package org.matchlab.academic_net.di

import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.KmpComponentCreate
import me.tatarka.inject.annotations.Provides
import org.matchlab.academic_net.SessionDependencies
import org.matchlab.academic_net.feature.auth.domain.Session
import org.matchlab.academic_net.feature.collaborators.domain.RoleRequirementProvider
import org.matchlab.academic_net.feature.projects.domain.ProjectsRepository
import org.matchlab.academic_net.gateway.GatewaySession

/**
 * The object graph of one session, a child of the process's [AppComponent]: the data sources and repositories of
 * every feature, the adapters that connect the features to each other, and the session client of the Gateway. It is
 * made when the user signs in and [close]d when the session ends, so each account gets graphs of its own: the caches,
 * the local chats, the running work and the client of the previous one are gone, not hidden.
 *
 * It is the only place that knows which data source stands behind each repository (see [DataSourcesModule]).
 */
@SessionScope
@Component
internal abstract class SessionComponent(
    /** The process's graph: the Gateway client, the sign-in repository, the backend. */
    @Component val app: AppComponent,
    /** The signed-in user of this graph; its token goes into every call of the session client. */
    @get:Provides val session: Session,
) : SessionGatewayModule,
    DataSourcesModule,
    ProjectsModule,
    ChatsModule,
    AvatarsModule,
    CollaboratorsModule,
    ProjectSearchModule,
    ProjectDetailsModule,
    ResponsesModule,
    ComplaintsModule,
    ReviewsModule,
    ProfileModule,
    FeedModule,
    SettingsModule {
    /** What `AppNavHost` hands to the screens of every feature. */
    abstract val dependencies: SessionDependencies

    /** The scope and the closeable resources of the session; [close] ends them. */
    abstract val resources: SessionResources

    /**
     * The calls of this session to the Gateway, opened when the graph is built (opening a client sends no request); the
     * data sources connected to the server take it as it is, and [close] closes it.
     */
    abstract val gatewaySession: GatewaySession

    // Exposed so a test can check that the features share one repository.
    abstract val projectsRepository: ProjectsRepository
    abstract val roleRequirements: RoleRequirementProvider

    /**
     * Ends the session's work: cancels its coroutines and closes its Gateway session. Idempotent. It leaves the saved
     * session, the sign-in state and the process's Gateway alone.
     */
    fun close() = resources.close()
}

/** Creates the graph of [session] under [app]; the implementation is generated for each target. */
@KmpComponentCreate
internal expect fun createSessionComponent(app: AppComponent, session: Session): SessionComponent
