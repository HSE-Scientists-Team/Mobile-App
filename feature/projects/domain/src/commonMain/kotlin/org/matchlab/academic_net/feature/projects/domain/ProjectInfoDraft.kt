package org.matchlab.academic_net.feature.projects.domain

import kotlinx.datetime.YearMonth
import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectPeriod
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.Sphere
import org.matchlab.academic_net.utils.normalizeTag
import org.matchlab.academic_net.utils.normalizeTags

/**
 * The description of a published project as the edit form (04d) holds it. Validated like [ProjectDraft] (the same
 * [ProjectDraftError]s, never [ProjectDraftError.RoleInvalid]); roles are changed by their own calls.
 */
data class ProjectInfoDraft(
    val title: String = "",
    val type: ProjectType? = null,
    val sphere: Sphere? = null,
    val problem: String = "",
    val goal: String = "",
    val tags: List<String> = emptyList(),
    val start: YearMonth? = null,
    val end: YearMonth? = null,
) {
    /** The title like a tag, the texts trimmed, the tags normalized: as [ProjectDraft.normalized]. */
    fun normalized(): ProjectInfoDraft = copy(
        title = title.normalizeTag(),
        problem = problem.trim(),
        goal = goal.trim(),
        tags = tags.normalizeTags(),
    )

    /** Errors of the draft as typed (call on [normalized] to ignore stray spaces). */
    fun validate(): Set<ProjectDraftError> = buildSet {
        if (title.isBlank()) add(ProjectDraftError.TitleEmpty)
        if (title.length > ProjectLimits.MaxTitleLength) add(ProjectDraftError.TitleTooLong)
        if (type == null) add(ProjectDraftError.TypeMissing)
        if (sphere == null) add(ProjectDraftError.SphereMissing)
        if (problem.isBlank()) add(ProjectDraftError.ProblemEmpty)
        if (goal.isBlank()) add(ProjectDraftError.GoalEmpty)
        if (start == null) add(ProjectDraftError.StartMissing)
        if (end == null) add(ProjectDraftError.EndMissing)
        if (start != null && end != null && end < start) add(ProjectDraftError.EndBeforeStart)
    }

    companion object {
        /** The form filled with what [project] has; what is unknown there (`null`) is empty and must be chosen. */
        fun of(project: Project): ProjectInfoDraft = ProjectInfoDraft(
            title = project.title,
            type = project.type,
            sphere = project.sphere,
            problem = project.problem,
            goal = project.goal,
            tags = project.tags,
            start = project.period?.start,
            end = project.period?.end,
        )
    }
}

/** A description that passed validation, ready to be sent (`PUT /projects/{id}`). */
data class ProjectInfo(
    val title: String,
    val type: ProjectType,
    val sphere: Sphere,
    val problem: String,
    val goal: String,
    val tags: List<String>,
    val period: ProjectPeriod,
)

/** The draft as a [ProjectInfo], or `null` while something that is required is not chosen. */
internal fun ProjectInfoDraft.toProjectInfo(): ProjectInfo? {
    val type = type ?: return null
    val sphere = sphere ?: return null
    val start = start ?: return null
    val end = end ?: return null
    return ProjectInfo(title, type, sphere, problem, goal, tags, ProjectPeriod(start, end))
}
