package org.matchlab.academic_net.feature.projectsearch.domain

import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.Sphere

/**
 * The duration chips of "03 Фильтры проектов": "до 3 мес", "3–6 мес" and "6+ мес". The months of a project's period are
 * counted inclusively (October 2026 to March 2027 is 6 months): [UpTo3Months] is 3 months or fewer, [From3To6Months] is
 * 4 to 6 and [Over6Months] is 7 or more. It is the length of the work, not a date range.
 *
 * The codes are the ones the backend lists for `duration` (`ProjectDurationFilter`, matchlab-backend PR #16): it counts
 * the days between `planned_start` and `planned_end`, up to 92 days, up to 183 and more. The month buckets above are the
 * app's reading of the same three chips (`docs/api-values.md`). The chips are offered only with
 * [ProjectSearchCapabilities.durationFilter]. The parameter holds one value, so a code is sent only when exactly one
 * chip is chosen.
 *
 * @property code the text sent to the server as `duration`: the one place where the table of values lives; a value is
 *  never reused or renamed.
 */
enum class ProjectDuration(val code: String) {
    UpTo3Months("up_to_3m"),
    From3To6Months("3_to_6m"),
    Over6Months("over_6m"),
}

/**
 * The filters of "03 Фильтры проектов". Different groups restrict the result together ("and"); inside a group of chips
 * ([types], [durations]) it is enough to fit one of them ("or"). An empty group, a `null` [sphere] and no [keywords]
 * mean "no restriction".
 */
data class ProjectFilters(
    /**
     * Project types to show; empty means any. Offered only with [ProjectSearchCapabilities.typeFilter]; the server is
     * sent the [ProjectType.code] of the type when exactly one is chosen (`docs/api-values.md`, §3).
     */
    val types: Set<ProjectType> = emptySet(),
    /**
     * The sphere of the project; `null` means any. The server is sent [Sphere.id], the name of the sphere, as `field`
     * (`docs/api-values.md`).
     */
    val sphere: Sphere? = null,
    /**
     * Duration buckets to show; empty means any. Offered only with [ProjectSearchCapabilities.durationFilter]; the server
     * is sent the [ProjectDuration.code] when exactly one is chosen (`docs/api-values.md`, §3). With two chosen it is
     * asked for no duration at all and returns more than was chosen: a known limit of the API, which takes one value.
     */
    val durations: Set<ProjectDuration> = emptySet(),
    /** Words the project is looked for by, sent as tags (`tag=…`, one parameter for each, in this order). */
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
 * What the user asked for. The search itself (matching [text] and [filters] and ordering the projects) is done by the
 * server; the app only sends this and shows the answer.
 */
data class ProjectQuery(
    /** The search bar as typed, blank means every project. API-GAP G53: what the server matches it against is not described. */
    val text: String = "",
    val filters: ProjectFilters = ProjectFilters(),
)

/** A search hit: the project plus the initiator's university for the card's author line. */
data class FoundProject(
    val project: Project,
    /** The university of the owner's profile; blank when unknown (no profile the viewer may see, or none given). */
    val initiatorUniversity: String,
)
