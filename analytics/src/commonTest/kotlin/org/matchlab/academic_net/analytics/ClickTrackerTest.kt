package org.matchlab.academic_net.analytics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.matchlab.academic_net.analytics.RecordingAnalytics.Event

/**
 * [ClickTracker] builds the events of the clicks. Every name below is written out as the word that reaches the console of
 * the analytics, on purpose: the catalog ([AnalyticsEvents], [ProjectSource], [UserSource]) is what the code uses, and a
 * test that used it too could not notice that a word was renamed. A word in the console is a name in the reports of the
 * customer, so a change of one is a decision, and it has to fail a test.
 */
class ClickTrackerTest {
    private val analytics = RecordingAnalytics()
    private val tracker = ClickTracker(analytics)

    private val id = "7c1d6a5e-3b4f-4d58-9a0e-2f6b8c1d9e10"

    // ---- the shape of an event ----

    @Test
    fun theCardOfAProjectIsAProjectCardClickWithTheIdAndThePlace() {
        tracker.projectCardClicked(id, ProjectSource.ProjectSearch)

        assertEquals(
            listOf(Event("project_card_click", mapOf("project_id" to id, "source" to "project_search"))),
            analytics.events,
        )
    }

    @Test
    fun theOpenedPageOfAProjectIsAProjectClickWithTheIdAndThePlace() {
        tracker.projectOpened(id, ProjectSource.FeedRecommendations)

        assertEquals(
            listOf(Event("project_click", mapOf("project_id" to id, "source" to "feed_recommendations"))),
            analytics.events,
        )
    }

    @Test
    fun aTappedPersonIsAUserClickWithTheirIdAndThePlaceNotAProjectId() {
        tracker.userClicked(id, UserSource.PeopleSearch)

        assertEquals(
            listOf(Event("user_click", mapOf("user_id" to id, "source" to "people_search"))),
            analytics.events,
        )
    }

    @Test
    fun anEventCarriesTheIdAndThePlaceAndNothingElse() {
        tracker.projectCardClicked(id, ProjectSource.Responses)
        tracker.projectOpened(id, ProjectSource.Responses)
        tracker.userClicked(id, UserSource.ChatHeader)

        assertEquals(3, analytics.events.size)
        analytics.events.forEach { event ->
            assertEquals(2, event.params.size, "${event.name}: ${event.params}")
            assertEquals(id, event.params.getValue(if (event.name == "user_click") "user_id" else "project_id"))
            assertTrue("source" in event.params, event.name)
        }
    }

    @Test
    fun anIdWithUnusualCharactersIsPassedOnUnchanged() {
        val unusual = "Project-0001_ÅÖ"

        tracker.projectOpened(unusual, ProjectSource.MyProjectsOwner)

        assertEquals(unusual, analytics.events.single().params["project_id"])
    }

    // ---- the places of a tap ----

    @Test
    fun everyPlaceOfAProjectTapIsAFixedWord() {
        val words = mapOf(
            ProjectSource.FeedRecommendations to "feed_recommendations",
            ProjectSource.ProjectSearch to "project_search",
            ProjectSource.MyProjectsOwner to "my_projects_owner",
            ProjectSource.MyProjectsMember to "my_projects_member",
            ProjectSource.Responses to "responses",
        )
        assertEquals(ProjectSource.entries.toSet(), words.keys, "a new place has to be listed here with its word")

        words.forEach { (place, word) ->
            assertEquals(word, place.code, "the code of $place")
            val tracked = RecordingAnalytics()
            ClickTracker(tracked).projectCardClicked(id, place)
            ClickTracker(tracked).projectOpened(id, place)
            assertEquals(listOf(word, word), tracked.events.map { it.params["source"] }, "the source of $place")
        }
    }

    @Test
    fun everyPlaceOfAPersonTapIsAFixedWord() {
        val words = mapOf(
            UserSource.FeedPeople to "feed_people",
            UserSource.PeopleSearch to "people_search",
            UserSource.ProjectInitiator to "project_initiator",
            UserSource.ProjectTeam to "project_team",
            UserSource.ProjectManageTeam to "project_manage_team",
            UserSource.ProjectCandidates to "project_candidates",
            UserSource.ReviewAuthor to "review_author",
            UserSource.ChatHeader to "chat_header",
        )
        assertEquals(UserSource.entries.toSet(), words.keys, "a new place has to be listed here with its word")

        words.forEach { (place, word) ->
            assertEquals(word, place.code, "the code of $place")
            val tracked = RecordingAnalytics()
            ClickTracker(tracked).userClicked(id, place)
            assertEquals(word, tracked.events.single().params["source"], "the source of $place")
        }
    }

    @Test
    fun noWordIsUsedForTwoPlaces() {
        val projectCodes = ProjectSource.entries.map { it.code }
        val userCodes = UserSource.entries.map { it.code }

        assertEquals(projectCodes.size, projectCodes.toSet().size, "twice in the places of a project: $projectCodes")
        assertEquals(userCodes.size, userCodes.toSet().size, "twice in the places of a person: $userCodes")
        // It is one parameter, `source`, in the console, so one word names one place whichever event it comes with.
        val all = projectCodes + userCodes
        assertEquals(all.size, all.toSet().size, "a word is used by a project place and a person place: $all")
    }

    @Test
    fun everyWordIsLowerCaseWithUnderscores() {
        (ProjectSource.entries.map { it.code } + UserSource.entries.map { it.code }).forEach { code ->
            assertTrue(Regex("[a-z]+(_[a-z]+)*").matches(code), "'$code' is not a snake_case word")
        }
    }

    // ---- no object, no event ----

    @Test
    fun aBlankIdIsNeverSent() {
        listOf("", " ", "   ", "\t", "\n", " \t\n ").forEach { blank ->
            tracker.projectCardClicked(blank, ProjectSource.ProjectSearch)
            tracker.projectOpened(blank, ProjectSource.ProjectSearch)
            tracker.userClicked(blank, UserSource.PeopleSearch)
        }

        assertEquals(emptyList(), analytics.events)
    }

    @Test
    fun aBlankIdIsNoReasonToDropTheNextRealClick() {
        tracker.userClicked("", UserSource.FeedPeople)
        tracker.userClicked(id, UserSource.FeedPeople)

        assertEquals(listOf(Event("user_click", mapOf("user_id" to id, "source" to "feed_people"))), analytics.events)
    }

    // ---- the tracker as a whole ----

    @Test
    fun eachCallIsOneEventInTheOrderOfTheCalls() {
        tracker.projectCardClicked("project-1", ProjectSource.MyProjectsOwner)
        tracker.userClicked("user-1", UserSource.ProjectTeam)
        tracker.projectOpened("project-1", ProjectSource.MyProjectsOwner)
        tracker.projectCardClicked("project-2", ProjectSource.Responses)

        assertEquals(
            listOf("project_card_click", "user_click", "project_click", "project_card_click"),
            analytics.events.map { it.name },
        )
        assertEquals(
            listOf("project-1", "user-1", "project-1", "project-2"),
            analytics.events.map { it.params["project_id"] ?: it.params["user_id"] },
        )
    }

    @Test
    fun aClickNeverChangesTheProfileOrTheSwitchOfTheAnalytics() {
        tracker.projectCardClicked(id, ProjectSource.ProjectSearch)
        tracker.projectOpened(id, ProjectSource.ProjectSearch)
        tracker.userClicked(id, UserSource.PeopleSearch)

        assertEquals(emptyList(), analytics.profileIds)
        assertEquals(emptyList(), analytics.enabledCalls)
    }

    @Test
    fun aTrackerOverTheEmptyAnalyticsThrowsNothing() {
        val tracker = ClickTracker(NoOpAnalytics)

        tracker.projectCardClicked(id, ProjectSource.ProjectSearch)
        tracker.projectOpened(id, ProjectSource.ProjectSearch)
        tracker.userClicked(id, UserSource.PeopleSearch)
        tracker.userClicked("", UserSource.PeopleSearch)
    }
}
