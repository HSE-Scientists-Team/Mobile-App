package org.matchlab.academic_net.feature.projects.domain

import kotlinx.datetime.YearMonth
import org.matchlab.academic_net.common.model.ProjectPeriod
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.Sphere
import org.matchlab.academic_net.utils.normalizeTag
import org.matchlab.academic_net.utils.normalizeTags

object ProjectLimits {
    const val MaxTitleLength = 100
    const val MaxRoleTitleLength = 60
}

enum class RoleDraftError {
    TitleEmpty,
    TitleTooLong,
}

/**
 * A role as filled in the form; what is still missing is reported by [validate]. A role is for one person: the place
 * count is not asked, the data source sends one place (API-GAP G49).
 */
data class RoleDraft(
    val title: String = "",
    val skills: List<String> = emptyList(),
) {
    fun normalized(): RoleDraft = RoleDraft(title.normalizeTag(), skills.normalizeTags())

    /** Errors of the draft as typed (call on [normalized] to ignore stray spaces). */
    fun validate(): Set<RoleDraftError> = buildSet {
        if (title.isBlank()) add(RoleDraftError.TitleEmpty)
        if (title.length > ProjectLimits.MaxRoleTitleLength) add(RoleDraftError.TitleTooLong)
    }
}

enum class ProjectDraftError {
    TitleEmpty,
    TitleTooLong,
    TypeMissing,
    SphereMissing,
    ProblemEmpty,
    GoalEmpty,

    /** One of the declared roles has no title. */
    RoleInvalid,
    StartMissing,
    EndMissing,

    /** The project would end in an earlier month than it starts. */
    EndBeforeStart,
}

/**
 * The "new project" form as the user has filled it so far.
 *
 * The form asks for everything `POST /projects` requires. The values the API leaves open are defined by the app and sent
 * as codes (`docs/api-values.md`): [type] as [ProjectType.code], [sphere] as [Sphere.id], the months as the first day of
 * [start] and the last day of [end]. Creating is on in [ProjectsCapabilities.canCreate] for the Gateway.
 */
data class ProjectDraft(
    val title: String = "",
    val type: ProjectType? = null,
    val sphere: Sphere? = null,
    val problem: String = "",
    val goal: String = "",
    val tags: List<String> = emptyList(),
    val roles: List<RoleDraft> = emptyList(),
    /** First month of the project. */
    val start: YearMonth? = null,
    /** Last month of the project. */
    val end: YearMonth? = null,
) {
    fun normalized(): ProjectDraft = copy(
        title = title.normalizeTag(),
        problem = problem.trim(),
        goal = goal.trim(),
        tags = tags.normalizeTags(),
        roles = roles.map { it.normalized() },
    )

    fun validate(): Set<ProjectDraftError> = buildSet {
        if (title.isBlank()) add(ProjectDraftError.TitleEmpty)
        if (title.length > ProjectLimits.MaxTitleLength) add(ProjectDraftError.TitleTooLong)
        if (type == null) add(ProjectDraftError.TypeMissing)
        if (sphere == null) add(ProjectDraftError.SphereMissing)
        if (problem.isBlank()) add(ProjectDraftError.ProblemEmpty)
        if (goal.isBlank()) add(ProjectDraftError.GoalEmpty)
        if (roles.any { it.validate().isNotEmpty() }) add(ProjectDraftError.RoleInvalid)
        if (start == null) add(ProjectDraftError.StartMissing)
        if (end == null) add(ProjectDraftError.EndMissing)
        if (start != null && end != null && end < start) add(ProjectDraftError.EndBeforeStart)
    }
}

/** A project that passed validation and is ready to be sent to the server (see [ProjectDraft] for how it is sent). */
data class NewProject(
    val title: String,
    val type: ProjectType,
    val sphere: Sphere,
    val problem: String,
    val goal: String,
    val tags: List<String>,
    val roles: List<RoleDraft>,
    val period: ProjectPeriod,
)

/** The draft as a [NewProject], or `null` while something that is required is not chosen. */
internal fun ProjectDraft.toNewProject(): NewProject? {
    val type = type ?: return null
    val sphere = sphere ?: return null
    val start = start ?: return null
    val end = end ?: return null
    return NewProject(title, type, sphere, problem, goal, tags, roles, ProjectPeriod(start, end))
}
