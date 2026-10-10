package org.matchlab.academic_net

import org.matchlab.academic_net.feature.chats.data.ChatsRepositoryImpl
import org.matchlab.academic_net.feature.chats.data.mock.MockChatsDataSource
import org.matchlab.academic_net.feature.chats.domain.usecase.MarkConversationReadUseCase
import org.matchlab.academic_net.feature.chats.domain.usecase.ObserveConversationUseCase
import org.matchlab.academic_net.feature.chats.domain.usecase.ObserveConversationsUseCase
import org.matchlab.academic_net.feature.chats.domain.usecase.ObserveMessagesUseCase
import org.matchlab.academic_net.feature.chats.domain.usecase.OpenConversationUseCase
import org.matchlab.academic_net.feature.chats.domain.usecase.SendMessageUseCase
import org.matchlab.academic_net.feature.chats.ui.ChatsUiDependencies
import org.matchlab.academic_net.feature.collaborators.data.CollaboratorsRepositoryImpl
import org.matchlab.academic_net.feature.collaborators.data.mock.MockCollaboratorsDataSource
import org.matchlab.academic_net.feature.collaborators.domain.usecase.GetRoleRequirementUseCase
import org.matchlab.academic_net.feature.collaborators.domain.usecase.SearchCollaboratorsUseCase
import org.matchlab.academic_net.feature.collaborators.ui.CollaboratorsUiDependencies
import org.matchlab.academic_net.feature.projects.data.ProjectsRepositoryImpl
import org.matchlab.academic_net.feature.projects.data.mock.MockProjectsDataSource
import org.matchlab.academic_net.feature.projects.domain.usecase.AddRoleUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.GetSpheresUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.ObserveMyProjectsUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.ObserveProjectUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.PublishProjectUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.UnpublishProjectUseCase
import org.matchlab.academic_net.feature.projects.ui.ProjectsUiDependencies

/** What every feature's screens need, as `AppNavHost` hands it to them. */
class AppDependencies(
    val projects: ProjectsUiDependencies,
    val chats: ChatsUiDependencies,
    val collaborators: CollaboratorsUiDependencies,
)

/**
 * Composition root: the only place that knows which data source stands behind each repository.
 * It outlives activities, so the mock data keeps what the user has changed on rotation.
 *
 * When the backend appears, the mock sources are replaced here and nowhere else.
 */
internal object AppContainer {
    private val projectsRepository by lazy { ProjectsRepositoryImpl(MockProjectsDataSource()) }
    private val chatsRepository by lazy { ChatsRepositoryImpl(MockChatsDataSource()) }
    // What the people search knows about roles: the mock server reads the projects, a real one would too.
    private val roleRequirements by lazy { ProjectsRoleRequirementProvider(projectsRepository) }
    private val collaboratorsRepository by lazy {
        CollaboratorsRepositoryImpl(MockCollaboratorsDataSource(roleRequirements))
    }

    val dependencies: AppDependencies by lazy {
        AppDependencies(
            projects = ProjectsUiDependencies(
                observeMyProjects = ObserveMyProjectsUseCase(projectsRepository),
                observeProject = ObserveProjectUseCase(projectsRepository),
                publishProject = PublishProjectUseCase(projectsRepository),
                unpublishProject = UnpublishProjectUseCase(projectsRepository),
                addRole = AddRoleUseCase(projectsRepository),
                getSpheres = GetSpheresUseCase(projectsRepository),
            ),
            chats = ChatsUiDependencies(
                observeConversations = ObserveConversationsUseCase(chatsRepository),
                observeConversation = ObserveConversationUseCase(chatsRepository),
                observeMessages = ObserveMessagesUseCase(chatsRepository),
                openConversation = OpenConversationUseCase(chatsRepository),
                sendMessage = SendMessageUseCase(chatsRepository),
                markConversationRead = MarkConversationReadUseCase(chatsRepository),
            ),
            collaborators = CollaboratorsUiDependencies(
                getRoleRequirement = GetRoleRequirementUseCase(roleRequirements),
                searchCollaborators = SearchCollaboratorsUseCase(collaboratorsRepository),
            ),
        )
    }
}
