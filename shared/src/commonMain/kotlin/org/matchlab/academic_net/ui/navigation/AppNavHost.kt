package org.matchlab.academic_net.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.savedstate.read
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.feature.projects.ui.ProjectsUiDependencies
import org.matchlab.academic_net.feature.projects.ui.create.CreateProjectRoute
import org.matchlab.academic_net.feature.projects.ui.list.MyProjectsRoute
import org.matchlab.academic_net.feature.projects.ui.management.ProjectManagementRoute

private object Routes {
    const val Main = "main"
    const val CreateProject = "create_project"
    const val ProjectArgument = "projectId"
    const val Project = "project/{$ProjectArgument}"

    fun project(id: ProjectId) = "project/${id.value}"
}

/**
 * Screens above the tabs. Features know nothing about navigation: each screen reports what
 * happened through callbacks and the routes are wired here.
 */
@Composable
fun AppNavHost(projects: ProjectsUiDependencies) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.Main) {
        composable(Routes.Main) {
            MainScaffold { destination ->
                when (destination) {
                    TopLevelDestination.Projects -> MyProjectsRoute(
                        dependencies = projects,
                        onCreateProject = { navController.navigate(Routes.CreateProject) },
                        onProjectClick = { id -> navController.navigate(Routes.project(id)) },
                    )
                    else -> TabPlaceholder(destination)
                }
            }
        }
        composable(Routes.CreateProject) {
            CreateProjectRoute(
                dependencies = projects,
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
            arguments = listOf(navArgument(Routes.ProjectArgument) { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.read { getStringOrNull(Routes.ProjectArgument) }
            if (id != null) {
                ProjectManagementRoute(
                    dependencies = projects,
                    projectId = ProjectId(id),
                    onNavigateBack = { navController.popBackStack() },
                )
            }
        }
    }
}
