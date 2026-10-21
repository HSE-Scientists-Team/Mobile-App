package org.matchlab.academic_net.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import org.matchlab.academic_net.SessionDependencies
import org.matchlab.academic_net.analytics.ClickTracker
import org.matchlab.academic_net.analytics.ProjectSource
import org.matchlab.academic_net.analytics.UserSource
import org.matchlab.academic_net.avatars.rememberAvatarImages
import org.matchlab.academic_net.components.LocalAvatarImages
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.di.SessionGraphs
import org.matchlab.academic_net.feature.auth.domain.AuthState
import org.matchlab.academic_net.feature.auth.ui.AuthSplash
import org.matchlab.academic_net.feature.auth.ui.AuthUiDependencies
import org.matchlab.academic_net.feature.auth.ui.ConfirmLinkRoute
import org.matchlab.academic_net.feature.chats.domain.ConversationId
import org.matchlab.academic_net.feature.chats.ui.chat.ChatRoute
import org.matchlab.academic_net.feature.chats.ui.list.ChatsRoute
import org.matchlab.academic_net.feature.collaborators.domain.RoleReference
import org.matchlab.academic_net.feature.collaborators.ui.CollaboratorSearchRoute
import org.matchlab.academic_net.feature.complaints.domain.ComplaintTarget
import org.matchlab.academic_net.feature.complaints.ui.ComplaintEntrySheet
import org.matchlab.academic_net.feature.complaints.ui.ComplaintRoute
import org.matchlab.academic_net.feature.feed.ui.FeedRoute
import org.matchlab.academic_net.feature.profile.domain.DocumentId
import org.matchlab.academic_net.feature.profile.domain.ReviewId
import org.matchlab.academic_net.feature.profile.domain.WorkId
import org.matchlab.academic_net.feature.profile.ui.my.AddDocumentRoute
import org.matchlab.academic_net.feature.profile.ui.my.AddWorkRoute
import org.matchlab.academic_net.feature.profile.ui.my.EditDocumentRoute
import org.matchlab.academic_net.feature.profile.ui.my.EditProfileRoute
import org.matchlab.academic_net.feature.profile.ui.my.EditWorkRoute
import org.matchlab.academic_net.feature.profile.ui.my.MyProfileRoute
import org.matchlab.academic_net.feature.profile.ui.my.profileTabHasBadge
import org.matchlab.academic_net.feature.profile.ui.user.ReviewDetailsRoute
import org.matchlab.academic_net.feature.profile.ui.user.UserProfileRoute
import org.matchlab.academic_net.feature.profile.ui.user.UserReviewsRoute
import org.matchlab.academic_net.feature.profile.ui.user.UserWorksRoute
import org.matchlab.academic_net.feature.projectdetails.ui.ProjectDetailsRoute
import org.matchlab.academic_net.feature.projects.ui.create.CreateProjectRoute
import org.matchlab.academic_net.feature.projects.ui.edit.EditProjectRoute
import org.matchlab.academic_net.feature.projects.ui.list.MyProjectsRoute
import org.matchlab.academic_net.feature.projects.ui.management.ProjectManagementRoute
import org.matchlab.academic_net.feature.projectsearch.ui.ProjectSearchRoute
import org.matchlab.academic_net.feature.responses.ui.ApplyToRoleSheet
import org.matchlab.academic_net.feature.responses.ui.InvitationSentBanner
import org.matchlab.academic_net.feature.responses.ui.InvitePreselection
import org.matchlab.academic_net.feature.responses.ui.InviteToProjectSheet
import org.matchlab.academic_net.feature.responses.ui.ProjectResponsesRoute
import org.matchlab.academic_net.feature.responses.ui.ResponsesRoute
import org.matchlab.academic_net.feature.responses.ui.responsesTabHasBadge
import org.matchlab.academic_net.feature.settings.ui.SettingsRoute

private object Routes {
    const val Main = "main"
    const val CreateProject = "create_project"

    const val ProjectArgument = "projectId"
    const val RoleArgument = "roleId"
    const val ConversationArgument = "conversationId"
    const val UserArgument = "userId"
    const val ReviewArgument = "reviewId"
    const val WorkArgument = "workId"
    const val DocumentArgument = "documentId"

    // The profile opened from the search for a role (08 with a role): the role to invite to, both or none.
    const val InviteProjectArgument = "inviteProjectId"
    const val InviteRoleArgument = "inviteRoleId"

    const val Project = "project/{$ProjectArgument}"
    const val ProjectResponses = "project/{$ProjectArgument}/responses"
    const val UserComplaint = "complaint/user/{$UserArgument}"
    const val ProjectComplaint = "complaint/project/{$ProjectArgument}"
    const val ProjectDetails = "project_details/{$ProjectArgument}"
    const val EditProject = "edit_project/{$ProjectArgument}"
    const val Collaborators = "collaborators/{$ProjectArgument}/{$RoleArgument}"
    const val Chat = "chat/{$ConversationArgument}"
    const val PeopleSearch = "people_search"
    const val ProjectSearch = "project_search"
    const val Profile =
        "profile/{$UserArgument}?$InviteProjectArgument={$InviteProjectArgument}&$InviteRoleArgument={$InviteRoleArgument}"
    const val ProfileWorks = "profile/{$UserArgument}/works"
    const val ProfileReviews = "profile/{$UserArgument}/reviews"
    const val ProfileReview = "profile/{$UserArgument}/reviews/{$ReviewArgument}"
    const val EditProfile = "my_profile/edit"
    const val Settings = "settings"
    const val AddWork = "my_profile/add_work"
    const val AddDocument = "my_profile/add_document"
    const val EditWork = "my_profile/works/{$WorkArgument}"
    const val EditDocument = "my_profile/documents/{$DocumentArgument}"

    fun project(id: ProjectId) = "project/${id.value}"
    fun projectDetails(id: ProjectId) = "project_details/${id.value}"
    fun editProject(id: ProjectId) = "edit_project/${id.value}"
    fun collaborators(projectId: ProjectId, roleId: RoleId) = "collaborators/${projectId.value}/${roleId.value}"
    fun chat(id: ConversationId) = "chat/${id.value}"
    fun profile(id: UserId) = "profile/${id.value}"
    fun profileForRole(id: UserId, projectId: ProjectId, roleId: RoleId) =
        "profile/${id.value}?$InviteProjectArgument=${projectId.value}&$InviteRoleArgument=${roleId.value}"
    fun projectResponses(id: ProjectId) = "project/${id.value}/responses"
    fun userComplaint(id: UserId) = "complaint/user/${id.value}"
    fun projectComplaint(id: ProjectId) = "complaint/project/${id.value}"
    fun profileWorks(id: UserId) = "profile/${id.value}/works"
    fun profileReviews(id: UserId) = "profile/${id.value}/reviews"
    fun profileReview(userId: UserId, reviewId: ReviewId) = "profile/${userId.value}/reviews/${reviewId.value}"
    fun editWork(id: WorkId) = "my_profile/works/${id.value}"
    fun editDocument(id: DocumentId) = "my_profile/documents/${id.value}"
}

private val StringArgument = NavType.StringType

/** A string argument of a route that may be left out (a query argument, `null` when it is). */
private fun optionalStringArgument(name: String) = navArgument(name) {
    type = StringArgument
    nullable = true
    defaultValue = null
}

/**
 * The root of the app. Which graph is on the screen follows the state of the session ([toRootScreen]): the splash while
 * the saved session is read, the sign-in flow ([AuthNavHost]) while there is none, the tabs and everything above them
 * ([MainNavHost]) while there is one. The graphs replace each other, and what belongs to the one that goes away is let
 * go of ([GraphViewModelStores.release]): the view models of its screens are cleared and, when the session is over, the
 * session graph of the account is closed, so after signing out nothing of it (chats, their drafts, the running work,
 * the Gateway session) stays alive.
 *
 * **A signed-in user is not signed out by accident.** The saved session is read by the graph of the process as soon as it
 * is built (`AppGraphs.start`), not by this composition, so a rotation cannot interrupt the reading or the check that
 * follows it. [AuthState.SignedIn] opens the tabs at once, without waiting for the network, so the sign-in screen never
 * flashes: until the answer is in, the splash stands in its place. The graph of a session is looked up by the session
 * ([SessionGraphs.open] gives the same graph for the same session), so a rotation or a recreated activity builds nothing
 * anew and signs nobody out. Only [AuthSplash] and the sign-in flow exist without a session, and the session ends only
 * when the user signs out or the server refuses the token (API-GAP G03: the API says nothing about the token's lifetime
 * or its renewal, so a token is good until the server says otherwise).
 *
 * @param auth the sign-in of the process: the state of the session and what the sign-in screens need
 * @param sessions the graph of the signed-in user, which the root opens and closes with the state
 * @param clicks counts the taps that open the page of a project or the profile of a person ([MainNavHost]); the screens
 * know nothing about analytics, so the counting is done here, where the place of the tap (its `source`) is known
 * @param onStatisticsChosen the person's choice "send usage statistics" made on the registration form ([AuthNavHost]):
 * the app keeps it as the setting of the device
 * @param incomingLink the link the system opened in the app, if any (an App Link on Android, a universal link on iOS).
 * Only the link of the confirmation letter is taken, and only from a user who is signed out ([incomingLinkAction]):
 * the root shows the confirmation at once; if the app is signed in by it the tabs follow, otherwise the sign-in screen
 * says that the address is confirmed
 * @param onIncomingLinkHandled the root has taken [incomingLink] or dropped it: the platform forgets it, so that it is
 * not handled again
 */
@Composable
internal fun AppNavHost(
    auth: AuthUiDependencies,
    sessions: SessionGraphs,
    clicks: ClickTracker,
    onStatisticsChosen: (Boolean) -> Unit,
    incomingLink: String? = null,
    onIncomingLinkHandled: () -> Unit = {},
) {
    val authState by remember { auth.observeAuthState() }.collectAsStateWithLifecycle()
    // The link being confirmed, and whether it ended with a confirmed address and no session; kept across a rotation.
    var linkToConfirm by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmedByLink by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(incomingLink, authState) {
        val link = incomingLink ?: return@LaunchedEffect
        when (val action = incomingLinkAction(link, authState)) {
            IncomingLinkAction.Wait -> Unit
            IncomingLinkAction.Ignore -> onIncomingLinkHandled()
            is IncomingLinkAction.Confirm -> {
                confirmedByLink = false
                linkToConfirm = action.link
                onIncomingLinkHandled()
            }
        }
    }
    // A session ends the confirmation, and a later sign-out must not bring it back.
    LaunchedEffect(authState is AuthState.SignedIn) {
        if (authState is AuthState.SignedIn) {
            linkToConfirm = null
            confirmedByLink = false
        }
    }
    val stores = viewModel { GraphViewModelStores() }

    // Let go of a graph's state when the session says that graph is over.
    LaunchedEffect(authState is AuthState.SignedIn, authState is AuthState.SignedOut) {
        stores.release(authState, sessions)
    }

    when (val screen = authState.toRootScreen()) {
        // The stored session is being read; nothing to show but the name of the app.
        RootScreen.Splash -> AuthSplash()
        is RootScreen.SignIn -> CompositionLocalProvider(LocalViewModelStoreOwner provides stores.auth) {
            val link = linkToConfirm
            if (link != null) {
                ConfirmLinkRoute(
                    dependencies = auth,
                    email = screen.start.email,
                    link = link,
                    onConfirmed = {
                        confirmedByLink = true
                        linkToConfirm = null
                    },
                    onClose = { linkToConfirm = null },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface).safeDrawingPadding(),
                )
            } else {
                AuthNavHost(auth, screen.start, onStatisticsChosen, emailConfirmed = confirmedByLink)
            }
        }
        is RootScreen.Main -> key(screen.session) {
            val dependencies = remember(screen.session) { sessions.open(screen.session) }
            CompositionLocalProvider(
                LocalViewModelStoreOwner provides stores.main,
                LocalAvatarImages provides rememberAvatarImages(dependencies.avatars),
            ) {
                MainNavHost(
                    dependencies = dependencies,
                    clicks = clicks,
                    signOut = { auth.signOut() },
                )
            }
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
 *
 * **Taps are counted here** ([clicks]), by the place the tap came from (`source`): a tap on the card of a project in a
 * list, which opens the page of the project, is two events ([projectTapped]); a tap on a person, which opens their
 * profile, is one. An event is sent only when the navigation was carried out, so a burst of taps counts once and a tap
 * of a screen the user has already left counts nothing. A page that opens by itself (the project that was just
 * published) is not a tap and is not counted.
 *
 * @param dependencies what the screens of the signed-in user need, from the graph of that session
 * @param clicks counts the taps that open a project or a profile, see above
 * @param signOut ends the session ("Выйти из аккаунта" of the settings); the root swaps the graphs when the state of
 * the session changes, so the screen needs no navigation of its own
 */
@Composable
private fun MainNavHost(dependencies: SessionDependencies, clicks: ClickTracker, signOut: suspend () -> Unit) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val chatOpener = remember { ExclusiveLauncher() }
    val signOutLauncher = remember { ExclusiveLauncher() }

    // API-GAP G55: the server may leave out the id of a person (the author of a review). Such a person has no page,
    // and the route "profile/" matches nothing, so navigating to it would crash. The screens do not offer the tap for
    // them; this is the last guard, for every profile that is opened from another screen. The tap is counted
    // (`user_click` with the source of the tap) only when the profile did open.
    val openProfile: (NavBackStackEntry, UserId, UserSource) -> Unit = { from, id, source ->
        if (id.value.isNotBlank() && navController.navigateOnce(from, Routes.profile(id))) {
            clicks.userClicked(id.value, source)
        }
    }
    // A project card tapped in a list opens the page of the project: 04 for a project of somebody else (or the viewer
    // as a member), 07 for the viewer's own. Counted (`project_card_click` and `project_click` with the source of the
    // tap) only when the page did open.
    val openProjectPage: (NavBackStackEntry, ProjectId, ProjectSource) -> Unit = { from, id, source ->
        if (navController.navigateOnce(from, Routes.projectDetails(id))) clicks.projectTapped(id, source)
    }
    val openManagedProject: (NavBackStackEntry, ProjectId, ProjectSource) -> Unit = { from, id, source ->
        if (navController.navigateOnce(from, Routes.project(id))) clicks.projectTapped(id, source)
    }
    // The chat opens after the conversation exists, so the tap has to ask first. A tap from a screen
    // that is no longer the current one, or while the previous request runs, does nothing. The answer
    // opens the chat only if the user is still on the screen that asked: after "back" it would
    // otherwise open over whatever is below. A person with no id (API-GAP G55) has nobody to talk to.
    val openChatWith: (NavBackStackEntry, UserSummary) -> Unit = { from, user ->
        if (user.id.value.isNotBlank() && navController.isCurrent(from)) {
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
            val responsesBadge = responsesTabHasBadge(dependencies.responses)
            MainScaffold(
                badges = buildSet {
                    if (profileBadge) add(TopLevelDestination.Profile)
                    if (responsesBadge) add(TopLevelDestination.Responses)
                },
            ) { destination, selectTab ->
                when (destination) {
                    TopLevelDestination.Feed -> FeedRoute(
                        dependencies = dependencies.feed,
                        onProfileClick = { selectTab(TopLevelDestination.Profile) },
                        onSearchPeopleClick = { navController.navigateSingleTop(entry, Routes.PeopleSearch) },
                        onSearchProjectsClick = { navController.navigateSingleTop(entry, Routes.ProjectSearch) },
                        onPersonClick = { user -> openProfile(entry, user.id, UserSource.FeedPeople) },
                        // A recommended project is somebody else's (or the viewer's own, which the page 04 shows as theirs).
                        onProjectClick = { id -> openProjectPage(entry, id, ProjectSource.FeedRecommendations) },
                    )
                    TopLevelDestination.Projects -> MyProjectsRoute(
                        dependencies = dependencies.projects,
                        onCreateProject = { navController.navigateSingleTop(entry, Routes.CreateProject) },
                        // "Я инициатор": the page of management of the project (07).
                        onProjectClick = { id -> openManagedProject(entry, id, ProjectSource.MyProjectsOwner) },
                        // A project the viewer is a member of opens as the page of the project (04 as a member, 04c).
                        onParticipatingProjectClick = { id ->
                            openProjectPage(entry, id, ProjectSource.MyProjectsMember)
                        },
                    )
                    TopLevelDestination.Responses -> ResponsesRoute(
                        dependencies = dependencies.responses,
                        onProjectClick = { id -> openProjectPage(entry, id, ProjectSource.Responses) },
                        onContactInitiator = { user -> openChatWith(entry, user) },
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
                        onReviewClick = { userId, reviewId ->
                            navController.navigateOnce(entry, Routes.profileReview(userId, reviewId))
                        },
                        onAuthorClick = { userId -> openProfile(entry, userId, UserSource.ReviewAuthor) },
                        onAuthorChatClick = { user -> openChatWith(entry, user) },
                        onEditWork = { id -> navController.navigateOnce(entry, Routes.editWork(id)) },
                        onEditDocument = { id -> navController.navigateOnce(entry, Routes.editDocument(id)) },
                    )
                }
            }
        }
        composable(Routes.CreateProject) { entry ->
            CreateProjectRoute(
                dependencies = dependencies.projects,
                onClose = { navController.popBackStackOnce(entry) },
                // The form is replaced by the new project, so "back" returns to the list. The page opens by itself,
                // not by a tap on a card, so it is not counted as a click.
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
                    onEditProject = { projectId -> navController.navigateOnce(entry, Routes.editProject(projectId)) },
                    onMemberClick = { userId -> openProfile(entry, userId, UserSource.ProjectManageTeam) },
                    onContactMember = { user -> openChatWith(entry, user) },
                    onResponsesClick = { projectId ->
                        navController.navigateOnce(entry, Routes.projectResponses(projectId))
                    },
                )
            }
        }
        composable(
            route = Routes.ProjectResponses,
            arguments = listOf(navArgument(Routes.ProjectArgument) { type = StringArgument }),
        ) { entry ->
            val id = entry.arguments?.read { getStringOrNull(Routes.ProjectArgument) }
            if (id != null) {
                ProjectResponsesRoute(
                    dependencies = dependencies.responses,
                    projectId = ProjectId(id),
                    onNavigateBack = { navController.popBackStackOnce(entry) },
                    onCandidateClick = { userId -> openProfile(entry, userId, UserSource.ProjectCandidates) },
                    onContactCandidate = { user -> openChatWith(entry, user) },
                )
            }
        }
        composable(
            route = Routes.EditProject,
            arguments = listOf(navArgument(Routes.ProjectArgument) { type = StringArgument }),
        ) { entry ->
            val id = entry.arguments?.read { getStringOrNull(Routes.ProjectArgument) }
            if (id != null) {
                // Saving closes the form; 07 below shows the changes from the cache.
                EditProjectRoute(
                    dependencies = dependencies.projects,
                    projectId = ProjectId(id),
                    onClose = { navController.popBackStackOnce(entry) },
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
                    // The profile opens with the role, so "Пригласить в проект" starts with it chosen. It is a result of
                    // the search for people like any other, so it is counted as one (`people_search`).
                    onCollaboratorClick = { user ->
                        if (user.id.value.isNotBlank() &&
                            navController.navigateOnce(
                                entry,
                                Routes.profileForRole(user.id, ProjectId(projectId), RoleId(roleId)),
                            )
                        ) {
                            clicks.userClicked(user.id.value, UserSource.PeopleSearch)
                        }
                    },
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
                onCollaboratorClick = { user -> openProfile(entry, user.id, UserSource.PeopleSearch) },
            )
        }
        composable(Routes.ProjectSearch) { entry ->
            // Opened from the search bar of the feed in the "Projects" mode: every published project, with
            // the cursor in the search bar. A card opens the page of the project.
            ProjectSearchRoute(
                dependencies = dependencies.projectSearch,
                autoFocusSearch = true,
                onNavigateBack = { navController.popBackStackOnce(entry) },
                onProjectClick = { id -> openProjectPage(entry, id, ProjectSource.ProjectSearch) },
            )
        }
        composable(
            route = Routes.ProjectDetails,
            arguments = listOf(navArgument(Routes.ProjectArgument) { type = StringArgument }),
        ) { entry ->
            val id = entry.arguments?.read { getStringOrNull(Routes.ProjectArgument) }
            if (id != null) {
                // The role of the open sheet "Откликнуться" and a counter that gives each opening a fresh sheet; the
                // sheet for a report. Strings and numbers, so they survive the recreation of the activity.
                var applyRoleId by rememberSaveable { mutableStateOf<String?>(null) }
                var applyRequest by rememberSaveable { mutableIntStateOf(0) }
                var report by rememberSaveable { mutableStateOf(false) }
                ProjectDetailsRoute(
                    dependencies = dependencies.projectDetails,
                    projectId = ProjectId(id),
                    onNavigateBack = { navController.popBackStackOnce(entry) },
                    onContactInitiator = { user -> openChatWith(entry, user) },
                    onInitiatorClick = { userId -> openProfile(entry, userId, UserSource.ProjectInitiator) },
                    onMemberClick = { userId -> openProfile(entry, userId, UserSource.ProjectTeam) },
                    onContactMember = { user -> openChatWith(entry, user) },
                    onApplyClick = { roleId ->
                        applyRequest += 1
                        applyRoleId = roleId.value
                    },
                    onReportClick = { report = true },
                )
                applyRoleId?.let { roleId ->
                    ApplyToRoleSheet(
                        dependencies = dependencies.responses,
                        projectId = ProjectId(id),
                        roleId = RoleId(roleId),
                        requestId = applyRequest,
                        onDismiss = { applyRoleId = null },
                        // The application is made: the sheet closes, and the page shows "Вы откликнулись".
                        onApplied = { applyRoleId = null },
                    )
                }
                if (report) {
                    ComplaintEntrySheet(
                        target = ComplaintTarget.Project(ProjectId(id)),
                        onReport = {
                            report = false
                            navController.navigateOnce(entry, Routes.projectComplaint(ProjectId(id)))
                        },
                        onDismiss = { report = false },
                    )
                }
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
                            // The profile is below, so going back shows it. It is the same tap on the person, so it
                            // is counted all the same, once the screen was left.
                            if (navController.popBackStackOnce(entry)) {
                                clicks.userClicked(userId.value, UserSource.ChatHeader)
                            }
                        } else {
                            openProfile(entry, userId, UserSource.ChatHeader)
                        }
                    },
                    onReportPeer = { userId -> navController.navigateOnce(entry, Routes.userComplaint(userId)) },
                )
            }
        }
        composable(
            route = Routes.Profile,
            arguments = listOf(
                navArgument(Routes.UserArgument) { type = StringArgument },
                optionalStringArgument(Routes.InviteProjectArgument),
                optionalStringArgument(Routes.InviteRoleArgument),
            ),
        ) { entry ->
            val id = entry.arguments?.read { getStringOrNull(Routes.UserArgument) }
            if (id != null) {
                // The role the profile was opened for (08 with a role): the sheet "Пригласить в проект" starts with it.
                val preselected = entry.arguments?.read {
                    val projectId = getStringOrNull(Routes.InviteProjectArgument)
                    val roleId = getStringOrNull(Routes.InviteRoleArgument)
                    if (projectId != null && roleId != null) InvitePreselection(ProjectId(projectId), RoleId(roleId)) else null
                }
                // The open sheet of the invitation (the number of its opening, `null` while closed), the banner 09q
                // and the sheet of a report: strings and numbers, so they survive the recreation of the activity.
                var inviteRequest by rememberSaveable { mutableStateOf<Int?>(null) }
                var inviteRequests by rememberSaveable { mutableIntStateOf(0) }
                var invited by rememberSaveable { mutableStateOf(false) }
                var report by rememberSaveable { mutableStateOf(false) }
                UserProfileRoute(
                    dependencies = dependencies.profile,
                    userId = UserId(id),
                    onNavigateBack = { navController.popBackStackOnce(entry) },
                    onMessageClick = { user -> openChatWith(entry, user) },
                    onAllWorksClick = { userId -> navController.navigateOnce(entry, Routes.profileWorks(userId)) },
                    onAllReviewsClick = { userId -> navController.navigateOnce(entry, Routes.profileReviews(userId)) },
                    onReviewClick = { userId, reviewId ->
                        navController.navigateOnce(entry, Routes.profileReview(userId, reviewId))
                    },
                    onAuthorClick = { authorId -> openProfile(entry, authorId, UserSource.ReviewAuthor) },
                    onAuthorChatClick = { author -> openChatWith(entry, author) },
                    onInviteClick = {
                        inviteRequests += 1
                        inviteRequest = inviteRequests
                    },
                    onReportClick = { report = true },
                    topBanner = if (invited) ({ InvitationSentBanner() }) else null,
                )
                inviteRequest?.let { request ->
                    InviteToProjectSheet(
                        dependencies = dependencies.responses,
                        candidateId = UserId(id),
                        preselected = preselected,
                        requestId = request,
                        onDismiss = { inviteRequest = null },
                        onInvited = {
                            invited = true
                            inviteRequest = null
                        },
                        onCreateProject = {
                            inviteRequest = null
                            navController.navigateSingleTop(entry, Routes.CreateProject)
                        },
                    )
                }
                if (report) {
                    ComplaintEntrySheet(
                        target = ComplaintTarget.User(UserId(id)),
                        onReport = {
                            report = false
                            navController.navigateOnce(entry, Routes.userComplaint(UserId(id)))
                        },
                        onDismiss = { report = false },
                    )
                }
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
                    // The viewer's own list: a work opens for editing.
                    onEditWork = { workId -> navController.navigateOnce(entry, Routes.editWork(workId)) },
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
                    onReviewClick = { userId, reviewId ->
                        navController.navigateOnce(entry, Routes.profileReview(userId, reviewId))
                    },
                    onAuthorClick = { authorId -> openProfile(entry, authorId, UserSource.ReviewAuthor) },
                    onAuthorChatClick = { author -> openChatWith(entry, author) },
                )
            }
        }
        composable(
            route = Routes.ProfileReview,
            arguments = listOf(
                navArgument(Routes.UserArgument) { type = StringArgument },
                navArgument(Routes.ReviewArgument) { type = StringArgument },
            ),
        ) { entry ->
            val userId = entry.arguments?.read { getStringOrNull(Routes.UserArgument) }
            val reviewId = entry.arguments?.read { getStringOrNull(Routes.ReviewArgument) }
            if (userId != null && reviewId != null) {
                ReviewDetailsRoute(
                    dependencies = dependencies.profile,
                    userId = UserId(userId),
                    reviewId = ReviewId(reviewId),
                    onNavigateBack = { navController.popBackStackOnce(entry) },
                    onAuthorClick = { authorId -> openProfile(entry, authorId, UserSource.ReviewAuthor) },
                    onAuthorChatClick = { author -> openChatWith(entry, author) },
                )
            }
        }
        composable(
            route = Routes.UserComplaint,
            arguments = listOf(navArgument(Routes.UserArgument) { type = StringArgument }),
        ) { entry ->
            val id = entry.arguments?.read { getStringOrNull(Routes.UserArgument) }
            if (id != null) {
                ComplaintRoute(
                    dependencies = dependencies.complaints,
                    target = ComplaintTarget.User(UserId(id)),
                    onNavigateBack = { navController.popBackStackOnce(entry) },
                    onDone = { navController.popBackStackOnce(entry) },
                )
            }
        }
        composable(
            route = Routes.ProjectComplaint,
            arguments = listOf(navArgument(Routes.ProjectArgument) { type = StringArgument }),
        ) { entry ->
            val id = entry.arguments?.read { getStringOrNull(Routes.ProjectArgument) }
            if (id != null) {
                ComplaintRoute(
                    dependencies = dependencies.complaints,
                    target = ComplaintTarget.Project(ProjectId(id)),
                    onNavigateBack = { navController.popBackStackOnce(entry) },
                    onDone = { navController.popBackStackOnce(entry) },
                )
            }
        }
        composable(Routes.Settings) { entry ->
            SettingsRoute(
                dependencies = dependencies.settings,
                onNavigateBack = { navController.popBackStackOnce(entry) },
                // The root swaps the graphs when the session ends, so no navigation is needed here.
                onSignOut = { signOutLauncher.launchIn(scope) { signOut() } },
            )
        }
        composable(Routes.EditProfile) { entry ->
            EditProfileRoute(
                dependencies = dependencies.profile,
                onClose = { navController.popBackStackOnce(entry) },
                onAddWorkClick = { navController.navigateSingleTop(entry, Routes.AddWork) },
                onAddDocumentClick = { navController.navigateSingleTop(entry, Routes.AddDocument) },
                onEditWork = { id -> navController.navigateOnce(entry, Routes.editWork(id)) },
                onEditDocument = { id -> navController.navigateOnce(entry, Routes.editDocument(id)) },
            )
        }
        composable(Routes.AddWork) { entry ->
            AddWorkRoute(dependencies = dependencies.profile, onClose = { navController.popBackStackOnce(entry) })
        }
        composable(Routes.AddDocument) { entry ->
            AddDocumentRoute(dependencies = dependencies.profile, onClose = { navController.popBackStackOnce(entry) })
        }
        composable(
            route = Routes.EditWork,
            arguments = listOf(navArgument(Routes.WorkArgument) { type = StringArgument }),
        ) { entry ->
            val id = entry.arguments?.read { getStringOrNull(Routes.WorkArgument) }
            if (id != null) {
                EditWorkRoute(
                    dependencies = dependencies.profile,
                    workId = WorkId(id),
                    onClose = { navController.popBackStackOnce(entry) },
                )
            }
        }
        composable(
            route = Routes.EditDocument,
            arguments = listOf(navArgument(Routes.DocumentArgument) { type = StringArgument }),
        ) { entry ->
            val id = entry.arguments?.read { getStringOrNull(Routes.DocumentArgument) }
            if (id != null) {
                EditDocumentRoute(
                    dependencies = dependencies.profile,
                    documentId = DocumentId(id),
                    onClose = { navController.popBackStackOnce(entry) },
                )
            }
        }
    }
}
