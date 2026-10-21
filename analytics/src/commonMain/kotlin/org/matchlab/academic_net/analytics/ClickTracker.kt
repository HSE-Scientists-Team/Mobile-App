package org.matchlab.academic_net.analytics

/**
 * Builds the events of the clicks and hands them to [analytics]. It is the only place that knows the shape of an event
 * (its name and parameters, see [AnalyticsEvents]); the navigation of the app calls it when a tap has opened a screen,
 * and the screens know nothing about analytics.
 *
 * An event carries only the id of the project or of the person and the place of the tap ([ProjectSource] or
 * [UserSource]); a blank id is not sent, as an event with no object says nothing.
 */
class ClickTracker(private val analytics: Analytics) {
    /** The person tapped the card of the project [projectId] in a list: `project_card_click`. */
    fun projectCardClicked(projectId: String, source: ProjectSource) {
        send(AnalyticsEvents.ProjectCardClick, AnalyticsEvents.ProjectId, projectId, source.code)
    }

    /** The tap opened the page of the project [projectId]: `project_click`. */
    fun projectOpened(projectId: String, source: ProjectSource) {
        send(AnalyticsEvents.ProjectClick, AnalyticsEvents.ProjectId, projectId, source.code)
    }

    /** The person tapped the person [userId] and their profile opened: `user_click`. */
    fun userClicked(userId: String, source: UserSource) {
        send(AnalyticsEvents.UserClick, AnalyticsEvents.UserId, userId, source.code)
    }

    private fun send(event: String, idParameter: String, id: String, source: String) {
        if (id.isBlank()) return
        analytics.track(event, mapOf(idParameter to id, AnalyticsEvents.Source to source))
    }
}
