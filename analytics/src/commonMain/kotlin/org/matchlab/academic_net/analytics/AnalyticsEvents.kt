package org.matchlab.academic_net.analytics

/**
 * Where a project was tapped: the `source` parameter of the project events. [code] is the word that is sent; it is
 * a name in the reports of the analytics console, so a value is never renamed or reused for something else.
 */
enum class ProjectSource(val code: String) {
    /** The card of a recommended project on the feed. */
    FeedRecommendations("feed_recommendations"),

    /** A result of the project search. */
    ProjectSearch("project_search"),

    /** "My projects", the tab "I am the initiator": opens the management page of the project. */
    MyProjectsOwner("my_projects_owner"),

    /** "My projects", the tab "I am a member": opens the page of the project. */
    MyProjectsMember("my_projects_member"),

    /** The card of a project on the tab of responses and invitations. */
    Responses("responses"),
}

/**
 * Where a person was tapped: the `source` parameter of the `user_click` event. [code] is the word that is sent (see
 * [ProjectSource] on why it never changes).
 */
enum class UserSource(val code: String) {
    /** A recommended person on the feed. */
    FeedPeople("feed_people"),

    /** A result of the people search. */
    PeopleSearch("people_search"),

    /** The initiator on the page of a project. */
    ProjectInitiator("project_initiator"),

    /** A team member on the page of a project. */
    ProjectTeam("project_team"),

    /** A team member on the management page of a project. */
    ProjectManageTeam("project_manage_team"),

    /** A candidate among the responses to a project. */
    ProjectCandidates("project_candidates"),

    /** The author of a review: in the profile, in the list of reviews and on the page of a review. */
    ReviewAuthor("review_author"),

    /** The other person in the header of a chat. */
    ChatHeader("chat_header"),
}

/**
 * The names of the events and of their parameters, in one place: the shared code uses these constants and never writes
 * the words by hand. The tests write the words out, so that a rename cannot pass unnoticed. They are names in the
 * reports of the analytics console, so a value is not renamed.
 */
object AnalyticsEvents {
    /** The person tapped the card of a project in a list. Parameters: [ProjectId], [Source] (a [ProjectSource]). */
    const val ProjectCardClick = "project_card_click"

    /** The tap opened the page of a project. Parameters: [ProjectId], [Source] (a [ProjectSource]). */
    const val ProjectClick = "project_click"

    /** The person tapped another person and their profile opened. Parameters: [UserId], [Source] (a [UserSource]). */
    const val UserClick = "user_click"

    /** The parameter with the id of the project (the UUID the server gave it). */
    const val ProjectId = "project_id"

    /** The parameter with the id of the person (the UUID the server gave the account). */
    const val UserId = "user_id"

    /** The parameter with the place of the tap: the `code` of a [ProjectSource] or of a [UserSource]. */
    const val Source = "source"
}
