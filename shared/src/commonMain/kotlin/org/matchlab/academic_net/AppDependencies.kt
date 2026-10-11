package org.matchlab.academic_net

import me.tatarka.inject.annotations.Inject
import org.matchlab.academic_net.di.AppScope
import org.matchlab.academic_net.feature.auth.ui.AuthUiDependencies
import org.matchlab.academic_net.feature.chats.ui.ChatsUiDependencies
import org.matchlab.academic_net.feature.collaborators.ui.CollaboratorsUiDependencies
import org.matchlab.academic_net.feature.feed.ui.FeedUiDependencies
import org.matchlab.academic_net.feature.profile.ui.ProfileUiDependencies
import org.matchlab.academic_net.feature.projectdetails.ui.ProjectDetailsUiDependencies
import org.matchlab.academic_net.feature.projects.ui.ProjectsUiDependencies
import org.matchlab.academic_net.feature.projectsearch.ui.ProjectSearchUiDependencies
import org.matchlab.academic_net.feature.settings.ui.SettingsUiDependencies

/** What every feature's screens need, as `AppNavHost` hands it to them. */
@Inject
@AppScope
class AppDependencies(
    val projects: ProjectsUiDependencies,
    val chats: ChatsUiDependencies,
    val collaborators: CollaboratorsUiDependencies,
    val feed: FeedUiDependencies,
    val projectSearch: ProjectSearchUiDependencies,
    val projectDetails: ProjectDetailsUiDependencies,
    val profile: ProfileUiDependencies,
    val auth: AuthUiDependencies,
    val settings: SettingsUiDependencies,
)
