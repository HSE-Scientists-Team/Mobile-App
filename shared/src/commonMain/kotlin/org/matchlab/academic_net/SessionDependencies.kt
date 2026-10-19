package org.matchlab.academic_net

import me.tatarka.inject.annotations.Inject
import org.matchlab.academic_net.di.SessionScope
import org.matchlab.academic_net.feature.chats.ui.ChatsUiDependencies
import org.matchlab.academic_net.feature.collaborators.ui.CollaboratorsUiDependencies
import org.matchlab.academic_net.feature.complaints.ui.ComplaintsUiDependencies
import org.matchlab.academic_net.feature.feed.ui.FeedUiDependencies
import org.matchlab.academic_net.feature.profile.ui.ProfileUiDependencies
import org.matchlab.academic_net.feature.projectdetails.ui.ProjectDetailsUiDependencies
import org.matchlab.academic_net.feature.projects.ui.ProjectsUiDependencies
import org.matchlab.academic_net.feature.projectsearch.ui.ProjectSearchUiDependencies
import org.matchlab.academic_net.feature.responses.ui.ResponsesUiDependencies
import org.matchlab.academic_net.feature.settings.ui.SettingsUiDependencies

/**
 * What every feature's screens need while a user is signed in, as `AppNavHost` hands it to them: the tabs and
 * everything opened above them. It is made once per session, by the [org.matchlab.academic_net.di.SessionComponent]
 * of that session, and thrown away with it, so it holds nothing of the account that signed out.
 *
 * The sign-in screens are not here: they exist while there is no session and take [org.matchlab.academic_net.feature.auth.ui.AuthUiDependencies]
 * from the process-wide graph.
 */
@Inject
@SessionScope
internal class SessionDependencies(
    val projects: ProjectsUiDependencies,
    val chats: ChatsUiDependencies,
    val collaborators: CollaboratorsUiDependencies,
    val feed: FeedUiDependencies,
    val projectSearch: ProjectSearchUiDependencies,
    val projectDetails: ProjectDetailsUiDependencies,
    val profile: ProfileUiDependencies,
    val settings: SettingsUiDependencies,
    val responses: ResponsesUiDependencies,
    val complaints: ComplaintsUiDependencies,
)
