package org.matchlab.academic_net.feature.collaborators.domain

import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.RoleId

/** The filters of "08b Фильтры коллабораторов". Everything off means "no restriction". */
data class CollaboratorFilters(
    val verifiedProfile: Boolean = false,
    /** "Открыт к сотрудничеству" of the group "Статус": sent as `open_only=true`. */
    val openOnly: Boolean = false,
    val hasVerifiedProjects: Boolean = false,
    val hasPublications: Boolean = false,
    /**
     * The types of the person's works. Only a source whose [CollaboratorsCapabilities.projectTypeFilter] is on applies
     * this, and the filters sheet offers it only then. The server is sent the `ProjectType.code` when exactly one type is
     * chosen (`docs/api-values.md`, §3): `project_type` holds one value, and two of two mean "any".
     */
    val workTypes: Set<ProjectType> = emptySet(),
    /** The university as typed; blank means any. How it is matched is the server's. */
    val university: String = "",
    /** "Ключевые слова": sent as the skills the server looks for. */
    val keywords: List<String> = emptyList(),
) {
    /**
     * No filter restricts the result: the filter button has no dot. It is not "equal to the default filters": a
     * university of blanks restricts nothing (it is not sent, see [activeCount]) and the filters are empty then too.
     */
    val isEmpty: Boolean get() = activeCount == 0

    /** How many filters restrict the result: the dot on the filter button. */
    val activeCount: Int
        get() = listOf(
            verifiedProfile,
            openOnly,
            hasVerifiedProjects,
            hasPublications,
            workTypes.isNotEmpty(),
            university.isNotBlank(),
            keywords.isNotEmpty(),
        ).count { it }
}

/**
 * The role a search is started for: the title of the screen and the first text of the search bar (the role's skills)
 * come from it. API-GAP G31: the server does not take a role, so the search itself is by the text and the filters only.
 */
data class RoleReference(
    val projectId: ProjectId,
    val roleId: RoleId,
)

/**
 * What the user asked for. Matching [text] and [filters] and ordering the people is the server's; the app sends this as
 * it is and shows the answer page by page.
 */
data class CollaboratorQuery(
    /** The search bar as typed. */
    val text: String = "",
    val filters: CollaboratorFilters = CollaboratorFilters(),
)

/** What a role asks for: the search bar of the people search starts from its skills. */
data class RoleRequirement(
    val projectTitle: String,
    val roleTitle: String,
    val skills: List<String>,
)

class RoleNotFoundException(val projectId: ProjectId, val roleId: RoleId) :
    NoSuchElementException("Role ${roleId.value} of project ${projectId.value} not found")

/**
 * What the people search can do with the server it talks to. The filters sheet reads the flags and hides what the server
 * cannot do, instead of offering a filter without effect.
 *
 * @property projectTypeFilter the filter by the types of a person's works ([CollaboratorFilters.workTypes]) is offered in
 * the group "Тип работы" of "08b" and sent as `project_type`: the API has no list of its values, so the app defines the
 * codes (`docs/api-values.md`, decision of 2026-10-19). When `false` the group is hidden and the types are never sent.
 */
data class CollaboratorsCapabilities(
    val projectTypeFilter: Boolean,
)
