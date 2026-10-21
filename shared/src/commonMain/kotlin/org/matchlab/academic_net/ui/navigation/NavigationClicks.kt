package org.matchlab.academic_net.ui.navigation

import org.matchlab.academic_net.analytics.ClickTracker
import org.matchlab.academic_net.analytics.ProjectSource
import org.matchlab.academic_net.common.model.ProjectId

/**
 * One tap on the card of a project in a list, counted after the page of the project has opened: the card was tapped
 * (`project_card_click`) and the tap opened the page (`project_click`). They are two different measures of one tap, so
 * they are sent together and always in this order, with the same [id] and [source].
 *
 * The caller sends them only when the navigation was carried out ([navigateOnce] returned `true`): a burst of taps
 * opens one page and counts once. A page that opens by itself (the project that was just published) is not a tap and
 * does not come here.
 *
 * A blank id is dropped by [ClickTracker].
 */
internal fun ClickTracker.projectTapped(id: ProjectId, source: ProjectSource) {
    projectCardClicked(id.value, source)
    projectOpened(id.value, source)
}
