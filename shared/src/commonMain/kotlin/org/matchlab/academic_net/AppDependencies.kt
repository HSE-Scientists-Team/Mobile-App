package org.matchlab.academic_net

import me.tatarka.inject.annotations.Inject
import org.matchlab.academic_net.di.AppScope
import org.matchlab.academic_net.feature.chats.ui.ChatsUiDependencies
import org.matchlab.academic_net.feature.collaborators.ui.CollaboratorsUiDependencies
import org.matchlab.academic_net.feature.feed.ui.FeedUiDependencies
import org.matchlab.academic_net.feature.projects.ui.ProjectsUiDependencies

/** What every feature's screens need, as `AppNavHost` hands it to them. */
@Inject
@AppScope
class AppDependencies(
    val projects: ProjectsUiDependencies,
    val chats: ChatsUiDependencies,
    val collaborators: CollaboratorsUiDependencies,
    val feed: FeedUiDependencies,
)
