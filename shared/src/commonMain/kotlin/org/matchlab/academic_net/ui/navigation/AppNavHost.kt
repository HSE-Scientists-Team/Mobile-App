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
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.savedstate.read
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

/**
 * The email, then the code from the letter; signing in is done by the root when the state changes.
 *
 * Every navigation names the entry of the screen that asks for it (`navigateSingleTop(entry, ...)`,
 * `popBackStackOnce(entry)`): see [isCurrentEntry].
 */
@Composable
private fun AuthNavHost(dependencies: AppDependencies) {
    val navController = rememberNavController()
    // Kept here, not in the route: an address has characters that routes do not carry well ("+").
    var email by rememberSaveable { mutableStateOf("") }

    NavHost(
        navController = navController,
        startDestination = AuthRoutes.Email,
        enterTransition = NavTransitions.enter,
        exitTransition = NavTransitions.exit,
        popEnterTransition = NavTransitions.enter,
        popExitTransition = NavTransitions.exit,
    ) {
        composable(AuthRoutes.Email) { entry ->
            SignInRoute(
                dependencies = dependencies.auth,
                onCodeSent = { sent ->
                    email = sent
                    navController.navigateSingleTop(entry, AuthRoutes.Verify)
                },
            )
        }
        composable(AuthRoutes.Verify) { entry ->
            VerifyEmailRoute(
                dependencies = dependencies.auth,
                email = email,
                onNavigateBack = { navController.popBackStackOnce(entry) },
            )
        }
    }
}

/**
 * Screens above the tabs. Features know nothing about navigation: each screen reports what
 * happened through callbacks and the routes are wired here.
 *
 * Every transition goes through [navigateOnce], [navigateSingleTop] or [popBackStackOnce], never
 * through `navigate` or `popBackStack`, and names the entry of the screen that asks for it (the
 * `entry` of `composable(...) { entry -> ... }`). The request is carried out only while that
 * screen is the current one ([isCurrentEntry]): repeated taps open one screen, and an event of a
 * screen the user has already left does nothing.
 */
@Composable
private fun MainNavHost(dependencies: AppDependencies) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val chatOpener = remember { ExclusiveLauncher() }
    val signOutLauncher = remember { ExclusiveLauncher() }

    val openProfile: (NavBackStackEntry, UserId) -> Unit = { from, id ->
        navController.navigateOnce(from, Routes.profile(id))
    }
    // The chat opens after the conversation exists, so the tap has to ask first. A tap from a screen
    // that is no longer the current one, or while the previous request runs, does nothing. The answer
    // opens the chat only if the user is still on the screen that asked: after "back" it would
    // otherwise open over whatever is below.
    val openChatWith: (NavBackStackEntry, UserSummary) -> Unit = { from, user ->
        if (navController.isCurrent(from)) {
            chatOpener.launchIn(scope) {
                dependencies.chats.openConversation(user).onSuccess { id ->
                    navController.navigateOnce(from, Routes.chat(id))
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.Main,
        enterTransition = NavTransitions.enter,
        exitTransition = NavTransitions.exit,
        popEnterTransition = NavTransitions.enter,
        popExitTransition = NavTransitions.exit,
    ) {
        composable(Routes.Main) { entry ->
            val profileBadge = profileTabHasBadge(dependencies.profile)
            MainScaffold(
                badges = if (profileBadge) setOf(TopLevelDestination.Profile) else emptySet(),
            ) { destination, selectTab ->
                when (destination) {
                    TopLevelDestination.Feed -> FeedRoute(
                        dependencies = dependencies.feed,
                        onProfileClick = { selectTab(TopLevelDestination.Profile) },
                        onSearchPeopleClick = { navController.navigateSingleTop(entry, Routes.PeopleSearch) },
                        onSearchProjectsClick = { navController.navigateSingleTop(entry, Routes.ProjectSearch) },
                        onPersonClick = { user -> openProfile(entry, user.id) },
                    )
                    TopLevelDestination.Projects -> MyProjectsRoute(
                        dependencies = dependencies.projects,
                        onCreateProject = { navController.navigateSingleTop(entry, Routes.CreateProject) },
                        onProjectClick = { id -> navController.navigateOnce(entry, Routes.project(id)) },
                    )
                    TopLevelDestination.Chats -> ChatsRoute(
                        dependencies = dependencies.chats,
                        onChatClick = { id -> navController.navigateOnce(entry, Routes.chat(id)) },
                    )
                    TopLevelDestination.Profile -> MyProfileRoute(
                        dependencies = dependencies.profile,
                        onEditClick = { navController.navigateSingleTop(entry, Routes.EditProfile) },
                        onSettingsClick = { navController.navigateSingleTop(entry, Routes.Settings) },
                        onAllWorksClick = { id -> navController.navigateOnce(entry, Routes.profileWorks(id)) },
                        onAllReviewsClick = { id -> navController.navigateOnce(entry, Routes.profileReviews(id)) },
                        onReviewClick = { id -> navController.navigateOnce(entry, Routes.review(id)) },
                    )
                    else -> TabPlaceholder(destination)
                }
            }
        }
        composable(Routes.CreateProject) { entry ->
            CreateProjectRoute(
                dependencies = dependencies.projects,
                onClose = { navController.popBackStackOnce(entry) },
                // The form is replaced by the new project, so "back" returns to the list.
                onPublished = { id ->
                    navController.navigateOnce(entry, Routes.project(id)) {
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
                    onNavigateBack = { navController.popBackStackOnce(entry) },
                    onFindCollaborators = { projectId, roleId ->
                        navController.navigateOnce(entry, Routes.collaborators(projectId, roleId))
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
                    onNavigateBack = { navController.popBackStackOnce(entry) },
                    onCollaboratorClick = { user -> openProfile(entry, user.id) },
                )
            }
        }
        composable(Routes.PeopleSearch) { entry ->
            // Opened from the search bar of the feed: everyone, with the cursor in the search bar.
            CollaboratorSearchRoute(
                dependencies = dependencies.collaborators,
                role = null,
                autoFocusSearch = true,
                onNavigateBack = { navController.popBackStackOnce(entry) },
                onCollaboratorClick = { user -> openProfile(entry, user.id) },
            )
        }
        composable(Routes.ProjectSearch) { entry ->
            // Opened from the search bar of the feed in the "Projects" mode: every published project, with
            // the cursor in the search bar. A card opens the page of the project.
            ProjectSearchRoute(
                dependencies = dependencies.projectSearch,
                autoFocusSearch = true,
                onNavigateBack = { navController.popBackStackOnce(entry) },
                onProjectClick = { id -> navController.navigateOnce(entry, Routes.projectDetails(id)) },
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
                    onNavigateBack = { navController.popBackStackOnce(entry) },
                    onContactInitiator = { user -> openChatWith(entry, user) },
                    onInitiatorClick = { userId -> openProfile(entry, userId) },
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
                    onNavigateBack = { navController.popBackStackOnce(entry) },
                    onPeerClick = { userId ->
                        // A profile opened from this chat leads back here: do not stack the two.
                        val previous = navController.previousBackStackEntry
                        val cameFromTheSameProfile = previous?.destination?.route == Routes.Profile &&
                            previous.arguments?.read { getStringOrNull(Routes.UserArgument) } == userId.value
                        if (cameFromTheSameProfile) {
                            navController.popBackStackOnce(entry)
                        } else {
                            openProfile(entry, userId)
                        }
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
                    onNavigateBack = { navController.popBackStackOnce(entry) },
                    onMessageClick = { user -> openChatWith(entry, user) },
                    onAllWorksClick = { userId -> navController.navigateOnce(entry, Routes.profileWorks(userId)) },
                    onAllReviewsClick = { userId -> navController.navigateOnce(entry, Routes.profileReviews(userId)) },
                    onReviewClick = { reviewId -> navController.navigateOnce(entry, Routes.review(reviewId)) },
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
                    onNavigateBack = { navController.popBackStackOnce(entry) },
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
                    onNavigateBack = { navController.popBackStackOnce(entry) },
                    onReviewClick = { reviewId -> navController.navigateOnce(entry, Routes.review(reviewId)) },
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
                    onNavigateBack = { navController.popBackStackOnce(entry) },
                    onAuthorClick = { userId -> openProfile(entry, userId) },
                )
            }
        }
        composable(Routes.Settings) { entry ->
            SettingsRoute(
                dependencies = dependencies.settings,
                onNavigateBack = { navController.popBackStackOnce(entry) },
                // The root swaps the graphs when the session ends, so no navigation is needed here.
                onSignOut = { signOutLauncher.launchIn(scope) { dependencies.auth.signOut() } },
            )
        }
        composable(Routes.EditProfile) { entry ->
            EditProfileRoute(
                dependencies = dependencies.profile,
                onClose = { navController.popBackStackOnce(entry) },
                onAddWorkClick = { navController.navigateSingleTop(entry, Routes.AddWork) },
                onAddDocumentClick = { navController.navigateSingleTop(entry, Routes.AddDocument) },
            )
        }
        composable(Routes.AddWork) { entry ->
            AddWorkRoute(dependencies = dependencies.profile, onClose = { navController.popBackStackOnce(entry) })
        }
        composable(Routes.AddDocument) { entry ->
            AddDocumentRoute(dependencies = dependencies.profile, onClose = { navController.popBackStackOnce(entry) })
        }
    }
}
