package org.matchlab.academic_net.feature.projectsearch.domain

import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.Sphere

/**
 * The duration chips of "03 Фильтры проектов": "до 3 мес", "3–6 мес" and "6+ мес". The server
 * counts the months of a project's period inclusively (October 2026 to March 2027 is 6 months):
 * [UpTo3Months] is 3 months or fewer, [From3To6Months] is 4 to 6 and [Over6Months] is 7 or more.
 * It is the length of the work, not a date range.
 */
enum class ProjectDuration { UpTo3Months, From3To6Months, Over6Months }

/**
 * The filters of "03 Фильтры проектов". Different groups restrict the result together ("and");
 * inside a group of chips ([types], [durations]) it is enough to fit one of them ("or"). An empty
 * group, a `null` [sphere] and no [keywords] mean "no restriction".
 */
data class ProjectFilters(
    /** Project types to show; empty means any. */
    val types: Set<ProjectType> = emptySet(),
    /** The sphere of the project; `null` means any. The server is sent [Sphere.id]. */
    val sphere: Sphere? = null,
    /** Duration buckets to show; empty means any. */
    val durations: Set<ProjectDuration> = emptySet(),
    /** Every keyword has to occur in the project. */
    val keywords: List<String> = emptyList(),
) {
    /** `true` when no filter restricts the result. */
    val isEmpty: Boolean get() = this == ProjectFilters()

    /** How many groups restrict the result: the dot on the filter button. */
    val activeCount: Int
        get() = listOf(
            types.isNotEmpty(),
            sphere != null,
            durations.isNotEmpty(),
            keywords.isNotEmpty(),
        ).count { it }
}

/**
 * What the user asked for. The search itself (matching [text] and [filters] and ordering the
 * projects) is done by the server; the app only sends this and shows the answer.
 */
data class ProjectQuery(
    /** The search bar as typed; comma-separated terms, blank means every project. */
    val text: String = "",
    val filters: ProjectFilters = ProjectFilters(),
)

/** A search hit: the project plus the initiator's university for the card's author line. */
data class FoundProject(
    val project: Project,
    val initiatorUniversity: String,
)
