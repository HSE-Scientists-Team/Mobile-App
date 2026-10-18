package org.matchlab.academic_net.common.model

/**
 * A person offered as a possible collaborator: a row of the people search and of the feed's
 * recommendations, and the person of a profile page. What a search looks at (skills, works, ...)
 * stays on the server.
 *
 * The line under the name ("Stanford · Бакалавриат, 3 курс · Computer Science") is not made here: the study
 * is translated and written by the UI from [study].
 */
data class Collaborator(
    val user: UserSummary,
    val university: String,
    /** The level and the year of study; either may be unknown. */
    val study: Study,
    /** Field of study: "Computer Science". */
    val field: String,
)
