package org.matchlab.academic_net.ui.navigation

import academicnet.shared.generated.resources.Res
import academicnet.shared.generated.resources.nav_chats
import academicnet.shared.generated.resources.nav_feed
import academicnet.shared.generated.resources.nav_profile
import academicnet.shared.generated.resources.nav_projects
import academicnet.shared.generated.resources.nav_responses
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.matchlab.academic_net.components.AppIcons

/** Tabs of the bottom navigation bar, in display order. */
enum class TopLevelDestination(
    val label: StringResource,
    val icon: DrawableResource,
) {
    Feed(Res.string.nav_feed, AppIcons.Rss),
    Projects(Res.string.nav_projects, AppIcons.Briefcase),
    Responses(Res.string.nav_responses, AppIcons.FileText),
    Chats(Res.string.nav_chats, AppIcons.Message),
    Profile(Res.string.nav_profile, AppIcons.User),
}
