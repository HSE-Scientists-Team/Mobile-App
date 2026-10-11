package org.matchlab.academic_net.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
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
import org.matchlab.academic_net.feature.profile.domain.ReviewId
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.feature.chats.domain.ConversationId
import org.matchlab.academic_net.feature.auth.domain.AuthState
import org.matchlab.academic_net.feature.auth.ui.SignInRoute
import org.matchlab.academic_net.feature.auth.ui.VerifyEmailRoute
import org.matchlab.academic_net.feature.chats.ui.chat.ChatRoute
import org.matchlab.academic_net.feature.chats.ui.list.ChatsRoute
import org.matchlab.academic_net.feature.collaborators.domain.RoleReference
import org.matchlab.academic_net.feature.collaborators.ui.CollaboratorSearchRoute
import org.matchlab.academic_net.feature.feed.ui.FeedRoute
import org.matchlab.academic_net.feature.profile.ui.my.AddDocumentRoute
import org.matchlab.academic_net.feature.profile.ui.my.AddWorkRoute
import org.matchlab.academic_net.feature.profile.ui.my.EditProfileRoute
import org.matchlab.academic_net.feature.profile.ui.my.MyProfileRoute
import org.matchlab.academic_net.feature.profile.ui.my.profileTabHasBadge
import org.matchlab.academic_net.feature.profile.ui.user.ReviewDetailsRoute
import org.matchlab.academic_net.feature.profile.ui.user.UserProfileRoute
import org.matchlab.academic_net.feature.profile.ui.user.UserReviewsRoute
import org.matchlab.academic_net.feature.profile.ui.user.UserWorksRoute
import org.matchlab.academic_net.feature.projectdetails.ui.ProjectDetailsRoute
import org.matchlab.academic_net.feature.projects.ui.create.CreateProjectRoute
import org.matchlab.academic_net.feature.projects.ui.list.MyProjectsRoute
import org.matchlab.academic_net.feature.projects.ui.management.ProjectManagementRoute
import org.matchlab.academic_net.feature.projectsearch.ui.ProjectSearchRoute
import org.matchlab.academic_net.feature.settings.ui.SettingsRoute

private object Routes {
    const val Main = "main"
    const val CreateProject = "create_project"

    const val ProjectArgument = "projectId"
    const val RoleArgument = "roleId"
    const val ConversationArgument = "conversationId"
    const val UserArgument = "userId"
    const val ReviewArgument = "reviewId"

    const val Project = "project/{$ProjectArgument}"
    const val ProjectDetails = "project_details/{$ProjectArgument}"
    const val Collaborators = "collaborators/{$ProjectArgument}/{$RoleArgument}"
    const val Chat = "chat/{$ConversationArgument}"
    const val PeopleSearch = "people_search"
    const val ProjectSearch = "project_search"
    const val Profile = "profile/{$UserArgument}"
    const val ProfileWorks = "profile/{$UserArgument}/works"
    const val ProfileReviews = "profile/{$UserArgument}/reviews"
    const val Review = "review/{$ReviewArgument}"
    const val EditProfile = "my_profile/edit"
    const val Settings = "settings"
    const val AddWork = "my_profile/add_work"
    const val AddDocument = "my_profile/add_document"

    fun project(id: ProjectId) = "project/${id.value}"
    fun projectDetails(id: ProjectId) = "project_details/${id.value}"
    fun collaborators(projectId: ProjectId, roleId: RoleId) = "collaborators/${projectId.value}/${roleId.value}"
    fun chat(id: ConversationId) = "chat/${id.value}"
    fun profile(id: UserId) = "profile/${id.value}"
    fun profileWorks(id: UserId) = "profile/${id.value}/works"
    fun profileReviews(id: UserId) = "profile/${id.value}/reviews"
    fun review(id: ReviewId) = "review/${id.value}"
}

private val StringArgument = NavType.StringType

/** Routes of the sign-in flow, shown while there is no session. */
private object AuthRoutes {
    const val Email = "auth/email"
    const val Verify = "auth/verify"
}

/** A [ViewModelStoreOwner] of its own: what lives in it can be dropped without touching the activity's. */
private class GraphViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}

/**
 * The view model stores of the two graphs. It lives in the activity's store, so it survives a
 * rotation like any view model, and the graphs can still be cleared one by one: the navigation of
 * a graph keeps its entries' view models in the owner it is composed under.
 */
private class GraphViewModelStores : ViewModel() {
    val auth = GraphViewModelStoreOwner()
    val main = GraphViewModelStoreOwner()

    override fun onCleared() {
        auth.viewModelStore.clear()
        main.viewModelStore.clear()
    }
}

/**
 * The root of the app. Which graph is on the screen follows the session: the sign-in flow while
 * there is none, the tabs and everything above them while there is one. The graphs replace each
 * other, and the view models of the one that goes away are cleared, so after signing out nothing
 * of the old one (chats, their drafts, their running work) stays alive.
 */
@Composable
fun AppNavHost(dependencies: AppDependencies) {
    val authState by remember { dependencies.auth.observeAuthState() }.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { dependencies.auth.restoreSession() }
    val stores = viewModel { GraphViewModelStores() }

    // Clear a graph's view models when the session says that graph is over.
    LaunchedEffect(authState is AuthState.SignedIn, authState is AuthState.SignedOut) {
        when (authState) {
            is AuthState.SignedIn -> stores.auth.viewModelStore.clear()
            AuthState.SignedOut -> stores.main.viewModelStore.clear()
            AuthState.Restoring -> Unit
        }
    }

    when (val state = authState) {
        // The stored session is being read; nothing to show but the background.
        AuthState.Restoring -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
        AuthState.SignedOut -> CompositionLocalProvider(LocalViewModelStoreOwner provides stores.auth) {
            AuthNavHost(dependencies)
        }
        is AuthState.SignedIn -> key(state.session.userId) {
            CompositionLocalProvider(LocalViewModelStoreOwner provides stores.main) {
                MainNavHost(dependencies)
            }
        }
    }
}

/** The email, then the code from the letter; signing in is done by the root when the state changes. */
@Composable
private fun AuthNavHost(dependencies: AppDependencies) {
    val navController = rememberNavController()
    // Kept here, not in the route: an address has characters that routes do not carry well ("+").
    var email by rememberSaveable { mutableStateOf("") }

    NavHost(navController = navController, startDestination = AuthRoutes.Email) {
        composable(AuthRoutes.Email) {
            SignInRoute(
                dependencies = dependencies.auth,
                onCodeSent = { sent ->
                    email = sent
                    navController.navigate(AuthRoutes.Verify)
                },
            )
        }
        composable(AuthRoutes.Verify) {
            VerifyEmailRoute(
                dependencies = dependencies.auth,
                email = email,
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}

/**
 * Screens above the tabs. Features know nothing about navigation: each screen reports what
 * happened through callbacks and the routes are wired here.
 */
@Composable
private fun MainNavHost(dependencies: AppDependencies) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    val openProfile: (UserId) -> Unit = { id -> navController.navigate(Routes.profile(id)) }
    val openChatWith: (UserSummary) -> Unit = { user ->
        scope.launch {
            dependencies.chats.openConversation(user).onSuccess { id ->
                navController.navigate(Routes.chat(id))
            }
        }
    }

    NavHost(navController = navController, startDestination = Routes.Main) {
        composable(Routes.Main) {
            val profileBadge = profileTabHasBadge(dependencies.profile)
            MainScaffold(
                badges = if (profileBadge) setOf(TopLevelDestination.Profile) else emptySet(),
            ) { destination, selectTab ->
                when (destination) {
                    TopLevelDestination.Feed -> FeedRoute(
                        dependencies = dependencies.feed,
                        onProfileClick = { selectTab(TopLevelDestination.Profile) },
                        onSearchPeopleClick = { navController.navigate(Routes.PeopleSearch) },
                        onSearchProjectsClick = { navController.navigate(Routes.ProjectSearch) },
                        onPersonClick = { user -> openProfile(user.id) },
                    )
                    TopLevelDestination.Projects -> MyProjectsRoute(
                        dependencies = dependencies.projects,
                        onCreateProject = { navController.navigate(Routes.CreateProject) },
                        onProjectClick = { id -> navController.navigate(Routes.project(id)) },
                    )
                    TopLevelDestination.Chats -> ChatsRoute(
                        dependencies = dependencies.chats,
                        onChatClick = { id -> navController.navigate(Routes.chat(id)) },
                    )
                    TopLevelDestination.Profile -> MyProfileRoute(
                        dependencies = dependencies.profile,
                        onEditClick = { navController.navigate(Routes.EditProfile) },
                        onSettingsClick = { navController.navigate(Routes.Settings) },
                        onAllWorksClick = { id -> navController.navigate(Routes.profileWorks(id)) },
                        onAllReviewsClick = { id -> navController.navigate(Routes.profileReviews(id)) },
                        onReviewClick = { id -> navController.navigate(Routes.review(id)) },
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
                    role = RoleReference(ProjectId(projectId), RoleId(roleId)),
                    onNavigateBack = { navController.popBackStack() },
                    onCollaboratorClick = { user -> openProfile(user.id) },
                )
            }
        }
        composable(Routes.PeopleSearch) {
            // Opened from the search bar of the feed: everyone, with the cursor in the search bar.
            CollaboratorSearchRoute(
                dependencies = dependencies.collaborators,
                role = null,
                autoFocusSearch = true,
                onNavigateBack = { navController.popBackStack() },
                onCollaboratorClick = { user -> openProfile(user.id) },
            )
        }
        composable(Routes.ProjectSearch) {
            // Opened from the search bar of the feed in the "Projects" mode: every published project, with
            // the cursor in the search bar. A card opens the page of the project.
            ProjectSearchRoute(
                dependencies = dependencies.projectSearch,
                autoFocusSearch = true,
                onNavigateBack = { navController.popBackStack() },
                onProjectClick = { id -> navController.navigate(Routes.projectDetails(id)) },
            )
        }
        composable(
            route = Routes.ProjectDetails,
            arguments = listOf(navArgument(Routes.ProjectArgument) { type = StringArgument }),
        ) { entry ->
            val id = entry.arguments?.read { getStringOrNull(Routes.ProjectArgument) }
            if (id != null) {
                ProjectDetailsRoute(
                    dependencies = dependencies.projectDetails,
                    projectId = ProjectId(id),
                    onNavigateBack = { navController.popBackStack() },
                    onContactInitiator = openChatWith,
                    onInitiatorClick = openProfile,
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
                    onPeerClick = { userId ->
                        // A profile opened from this chat leads back here: do not stack the two.
                        val previous = navController.previousBackStackEntry
                        val cameFromTheSameProfile = previous?.destination?.route == Routes.Profile &&
                            previous.arguments?.read { getStringOrNull(Routes.UserArgument) } == userId.value
                        if (cameFromTheSameProfile) navController.popBackStack() else openProfile(userId)
                    },
                )
            }
        }
        composable(
            route = Routes.Profile,
            arguments = listOf(navArgument(Routes.UserArgument) { type = StringArgument }),
        ) { entry ->
            val id = entry.arguments?.read { getStringOrNull(Routes.UserArgument) }
            if (id != null) {
                UserProfileRoute(
                    dependencies = dependencies.profile,
                    userId = UserId(id),
                    onNavigateBack = { navController.popBackStack() },
                    onMessageClick = openChatWith,
                    onAllWorksClick = { userId -> navController.navigate(Routes.profileWorks(userId)) },
                    onAllReviewsClick = { userId -> navController.navigate(Routes.profileReviews(userId)) },
                    onReviewClick = { reviewId -> navController.navigate(Routes.review(reviewId)) },
                )
            }
        }
        composable(
            route = Routes.ProfileWorks,
            arguments = listOf(navArgument(Routes.UserArgument) { type = StringArgument }),
        ) { entry ->
            val id = entry.arguments?.read { getStringOrNull(Routes.UserArgument) }
            if (id != null) {
                UserWorksRoute(
                    dependencies = dependencies.profile,
                    userId = UserId(id),
                    onNavigateBack = { navController.popBackStack() },
                )
            }
        }
        composable(
            route = Routes.ProfileReviews,
            arguments = listOf(navArgument(Routes.UserArgument) { type = StringArgument }),
        ) { entry ->
            val id = entry.arguments?.read { getStringOrNull(Routes.UserArgument) }
            if (id != null) {
                UserReviewsRoute(
                    dependencies = dependencies.profile,
                    userId = UserId(id),
                    onNavigateBack = { navController.popBackStack() },
                    onReviewClick = { reviewId -> navController.navigate(Routes.review(reviewId)) },
                )
            }
        }
        composable(
            route = Routes.Review,
            arguments = listOf(navArgument(Routes.ReviewArgument) { type = StringArgument }),
        ) { entry ->
            val id = entry.arguments?.read { getStringOrNull(Routes.ReviewArgument) }
            if (id != null) {
                ReviewDetailsRoute(
                    dependencies = dependencies.profile,
                    reviewId = ReviewId(id),
                    onNavigateBack = { navController.popBackStack() },
                    onAuthorClick = openProfile,
                )
            }
        }
        composable(Routes.Settings) {
            SettingsRoute(
                dependencies = dependencies.settings,
                onNavigateBack = { navController.popBackStack() },
                // The root swaps the graphs when the session ends, so no navigation is needed here.
                onSignOut = { scope.launch { dependencies.auth.signOut() } },
            )
        }
        composable(Routes.EditProfile) {
            EditProfileRoute(
                dependencies = dependencies.profile,
                onClose = { navController.popBackStack() },
                onAddWorkClick = { navController.navigate(Routes.AddWork) },
                onAddDocumentClick = { navController.navigate(Routes.AddDocument) },
            )
        }
        composable(Routes.AddWork) {
            AddWorkRoute(dependencies = dependencies.profile, onClose = { navController.popBackStack() })
        }
        composable(Routes.AddDocument) {
            AddDocumentRoute(dependencies = dependencies.profile, onClose = { navController.popBackStack() })
        }
    }
}
