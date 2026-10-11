package org.matchlab.academic_net.common.model

/**
 * A person offered as a possible collaborator: a row of the people search and of the feed's
 * recommendations. What a search looks at (skills, works, ...) stays on the server.
 */
data class Collaborator(
    val user: UserSummary,
    val university: String,
    /** Year and level, or status: "3 курс бакалавриата", "аспирантка". */
    val studyInfo: String,
    /** Field of study: "Computer Science". */
    val field: String,
) {
    /** The line under the name in lists: "Stanford · 3 курс бакалавриата · Computer Science". */
    val subtitle: String
        // `this.field`: a bare `field` in a getter is the property's own backing field.
        get() = listOf(university, studyInfo, this.field).filter { it.isNotBlank() }.joinToString(" · ")
}
