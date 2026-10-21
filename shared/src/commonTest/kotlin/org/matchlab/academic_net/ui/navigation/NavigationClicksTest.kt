package org.matchlab.academic_net.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import org.matchlab.academic_net.RecordingAnalytics
import org.matchlab.academic_net.RecordingAnalytics.Event
import org.matchlab.academic_net.analytics.ClickTracker
import org.matchlab.academic_net.analytics.ProjectSource
import org.matchlab.academic_net.common.model.ProjectId

/**
 * [projectTapped]: one tap on the card of a project is two events, in a fixed order, with the same id and source. The words
 * are written out here on purpose: they are names in the reports of the analytics console, and a test that used the
 * constants of the catalog could not notice that one was renamed.
 */
class NavigationClicksTest {
    private val analytics = RecordingAnalytics()
    private val clicks = ClickTracker(analytics)

    private val projectId = "7c1d6a5e-3b4f-4d58-9a0e-2f6b8c1d9e10"

    private fun event(name: String, id: String, source: String) =
        Event(name, mapOf("project_id" to id, "source" to source))

    @Test
    fun oneTapIsTwoEventsTheCardFirstThenTheOpenedPage() {
        clicks.projectTapped(ProjectId(projectId), ProjectSource.ProjectSearch)

        assertEquals(listOf("project_card_click", "project_click"), analytics.events.map { it.name })
    }

    @Test
    fun bothEventsCarryTheSameIdAndTheSourceAndNothingElse() {
        clicks.projectTapped(ProjectId(projectId), ProjectSource.FeedRecommendations)

        assertEquals(
            listOf(
                event("project_card_click", projectId, "feed_recommendations"),
                event("project_click", projectId, "feed_recommendations"),
            ),
            analytics.events,
        )
    }

    @Test
    fun theSourceIsTheWordOfThePlaceOfTheTap() {
        val words = mapOf(
            ProjectSource.FeedRecommendations to "feed_recommendations",
            ProjectSource.ProjectSearch to "project_search",
            ProjectSource.MyProjectsOwner to "my_projects_owner",
            ProjectSource.MyProjectsMember to "my_projects_member",
            ProjectSource.Responses to "responses",
        )
        assertEquals(ProjectSource.entries.toSet(), words.keys, "a new place of the tap has to be listed here")

        words.forEach { (source, word) ->
            val tracked = RecordingAnalytics()

            ClickTracker(tracked).projectTapped(ProjectId(projectId), source)

            assertEquals(
                listOf(event("project_card_click", projectId, word), event("project_click", projectId, word)),
                tracked.events,
                "the tap of $source",
            )
        }
    }

    @Test
    fun aBlankIdGivesNoEventsAtAll() {
        listOf("", " ", "   ", "\t", "\n", " \t\n ").forEach { blank ->
            clicks.projectTapped(ProjectId(blank), ProjectSource.Responses)
        }

        assertEquals(emptyList(), analytics.events)
    }

    @Test
    fun aBlankIdIsNoReasonToDropTheNextRealTap() {
        clicks.projectTapped(ProjectId(""), ProjectSource.MyProjectsOwner)
        clicks.projectTapped(ProjectId(projectId), ProjectSource.MyProjectsOwner)

        assertEquals(
            listOf(
                event("project_card_click", projectId, "my_projects_owner"),
                event("project_click", projectId, "my_projects_owner"),
            ),
            analytics.events,
        )
    }

    @Test
    fun everyTapIsCountedAndTheIdsAndSourcesStayWithTheirOwnTap() {
        clicks.projectTapped(ProjectId("project-a"), ProjectSource.MyProjectsOwner)
        clicks.projectTapped(ProjectId("project-b"), ProjectSource.MyProjectsMember)
        clicks.projectTapped(ProjectId("project-a"), ProjectSource.MyProjectsOwner)

        // The guard against a burst of taps is the navigation's (it carries out the first one only), not the tracker's.
        assertEquals(
            listOf(
                event("project_card_click", "project-a", "my_projects_owner"),
                event("project_click", "project-a", "my_projects_owner"),
                event("project_card_click", "project-b", "my_projects_member"),
                event("project_click", "project-b", "my_projects_member"),
                event("project_card_click", "project-a", "my_projects_owner"),
                event("project_click", "project-a", "my_projects_owner"),
            ),
            analytics.events,
        )
    }

    @Test
    fun aTapNeverChangesTheProfileOrTheSwitchOfTheAnalytics() {
        clicks.projectTapped(ProjectId(projectId), ProjectSource.Responses)

        assertEquals(emptyList(), analytics.profileIds)
        assertEquals(emptyList(), analytics.enabledCalls)
    }
}
