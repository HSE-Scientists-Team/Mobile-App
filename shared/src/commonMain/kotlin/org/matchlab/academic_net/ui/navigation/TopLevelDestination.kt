package org.matchlab.academic_net.ui.navigation

import academicnet.shared.generated.resources.Res
import academicnet.shared.generated.resources.ic_briefcase
import academicnet.shared.generated.resources.ic_file_text
import academicnet.shared.generated.resources.ic_message
import academicnet.shared.generated.resources.ic_rss
import academicnet.shared.generated.resources.ic_user
import academicnet.shared.generated.resources.nav_chats
import academicnet.shared.generated.resources.nav_feed
import academicnet.shared.generated.resources.nav_profile
import academicnet.shared.generated.resources.nav_projects
import academicnet.shared.generated.resources.nav_responses
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

/** Tabs of the bottom navigation bar, in display order. */
enum class TopLevelDestination(
    val label: StringResource,
    val icon: DrawableResource,
) {
    Feed(Res.string.nav_feed, Res.drawable.ic_rss),
    Projects(Res.string.nav_projects, Res.drawable.ic_briefcase),
    Responses(Res.string.nav_responses, Res.drawable.ic_file_text),
    Chats(Res.string.nav_chats, Res.drawable.ic_message),
    Profile(Res.string.nav_profile, Res.drawable.ic_user),
}
