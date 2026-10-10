package org.matchlab.academic_net.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.savedstate.read
import kotlinx.coroutines.launch
import org.matchlab.academic_net.AppDependencies
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.feature.chats.domain.ConversationId
import org.matchlab.academic_net.feature.chats.ui.chat.ChatRoute
import org.matchlab.academic_net.feature.chats.ui.list.ChatsRoute
import org.matchlab.academic_net.feature.collaborators.ui.CollaboratorSearchRoute
import org.matchlab.academic_net.feature.projects.ui.create.CreateProjectRoute
import org.matchlab.academic_net.feature.projects.ui.list.MyProjectsRoute
import org.matchlab.academic_net.feature.projects.ui.management.ProjectManagementRoute

private object Routes {
    const val Main = "main"
    const val CreateProject = "create_project"

    const val ProjectArgument = "projectId"
    const val RoleArgument = "roleId"
    const val ConversationArgument = "conversationId"

    const val Project = "project/{$ProjectArgument}"
    const val Collaborators = "collaborators/{$ProjectArgument}/{$RoleArgument}"
    const val Chat = "chat/{$ConversationArgument}"

    fun project(id: ProjectId) = "project/${id.value}"
    fun collaborators(projectId: ProjectId, roleId: RoleId) = "collaborators/${projectId.value}/${roleId.value}"
    fun chat(id: ConversationId) = "chat/${id.value}"
}

private val StringArgument = NavType.StringType

/**
 * Screens above the tabs. Features know nothing about navigation: each screen reports what
 * happened through callbacks and the routes are wired here.
 */
@Composable
fun AppNavHost(dependencies: AppDependencies) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    NavHost(navController = navController, startDestination = Routes.Main) {
        composable(Routes.Main) {
            MainScaffold { destination ->
                when (destination) {
                    TopLevelDestination.Projects -> MyProjectsRoute(
                        dependencies = dependencies.projects,
                        onCreateProject = { navController.navigate(Routes.CreateProject) },
                        onProjectClick = { id -> navController.navigate(Routes.project(id)) },
                    )
                    TopLevelDestination.Chats -> ChatsRoute(
                        dependencies = dependencies.chats,
                        onChatClick = { id -> navController.navigate(Routes.chat(id)) },
                    )
                    else -> TabPlaceholder(destination)
                }
            }
        }
        composable(Routes.CreateProject) {
            CreateProjectRoute(
                dependencies = dependencies.projects,
                onClose = { navController.popBackStack() },
                // The form is replaced by the new project, so "back" returns to the list.
                onPublished = { id ->
                    navController.navigate(Routes.project(id)) {
                        popUpTo(Routes.CreateProject) { inclusive = true }
                    }
                },
            )
        }
        composable(
            route = Routes.Project,
            arguments = listOf(navArgument(Routes.ProjectArgument) { type = StringArgument }),
        ) { entry ->
            val id = entry.arguments?.read { getStringOrNull(Routes.ProjectArgument) }
            if (id != null) {
                ProjectManagementRoute(
                    dependencies = dependencies.projects,
                    projectId = ProjectId(id),
                    onNavigateBack = { navController.popBackStack() },
                    onFindCollaborators = { projectId, roleId ->
                        navController.navigate(Routes.collaborators(projectId, roleId))
                    },
                )
            }
        }
        composable(
            route = Routes.Collaborators,
            arguments = listOf(
                navArgument(Routes.ProjectArgument) { type = StringArgument },
                navArgument(Routes.RoleArgument) { type = StringArgument },
            ),
        ) { entry ->
            val projectId = entry.arguments?.read { getStringOrNull(Routes.ProjectArgument) }
            val roleId = entry.arguments?.read { getStringOrNull(Routes.RoleArgument) }
            if (projectId != null && roleId != null) {
                CollaboratorSearchRoute(
                    dependencies = dependencies.collaborators,
                    projectId = ProjectId(projectId),
                    roleId = RoleId(roleId),
                    onNavigateBack = { navController.popBackStack() },
                    // The person's profile is a story of the next block; until then a tap
                    // starts the conversation with them.
                    onCollaboratorClick = { user ->
                        scope.launch {
                            dependencies.chats.openConversation(user).onSuccess { id ->
                                navController.navigate(Routes.chat(id))
                            }
                        }
                    },
                )
            }
        }
        composable(
            route = Routes.Chat,
            arguments = listOf(navArgument(Routes.ConversationArgument) { type = StringArgument }),
        ) { entry ->
            val id = entry.arguments?.read { getStringOrNull(Routes.ConversationArgument) }
            if (id != null) {
                ChatRoute(
                    dependencies = dependencies.chats,
                    conversationId = ConversationId(id),
                    onNavigateBack = { navController.popBackStack() },
                )
            }
        }
    }
}
