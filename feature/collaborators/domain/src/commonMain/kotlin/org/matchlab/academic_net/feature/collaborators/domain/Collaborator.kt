package org.matchlab.academic_net.feature.collaborators.domain

import org.matchlab.academic_net.common.model.Collaborator
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.RoleId

/** The filters of "08b Фильтры коллабораторов". Everything off means "no restriction". */
data class CollaboratorFilters(
    val verifiedProfile: Boolean = false,
    val hasVerifiedProjects: Boolean = false,
    val hasPublications: Boolean = false,
    val workTypes: Set<ProjectType> = emptySet(),
    /** Part of the university's name; blank means any. */
    val university: String = "",
    val keywords: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = this == CollaboratorFilters()

    /** How many filters restrict the result: the dot on the filter button. */
    val activeCount: Int
        get() = listOf(
            verifiedProfile,
            hasVerifiedProjects,
            hasPublications,
            workTypes.isNotEmpty(),
            university.isNotBlank(),
            keywords.isNotEmpty(),
        ).count { it }
}

/** The role a search is done for: the server scores the people by how well they fit it. */
data class RoleReference(
    val projectId: ProjectId,
    val roleId: RoleId,
)

/**
 * What the user asked for. The search itself (matching [text] and [filters], scoring and ordering
 * the people) is done by the server; the app only sends this and shows the answer.
 */
data class CollaboratorQuery(
    /** The search bar as typed. */
    val text: String = "",
    val filters: CollaboratorFilters = CollaboratorFilters(),
    val role: RoleReference? = null,
)

/**
 * A search result. [matchPercent] is how well the person fits the role (0..100) as the server
 * counted it, `null` when the search was not done for a role.
 */
data class ScoredCollaborator(
    val collaborator: Collaborator,
    val matchPercent: Int?,
)

/** What a role asks for: the search bar of the people search starts from its skills. */
data class RoleRequirement(
    val projectTitle: String,
    val roleTitle: String,
    val skills: List<String>,
)

class RoleNotFoundException(val projectId: ProjectId, val roleId: RoleId) :
    NoSuchElementException("Role ${roleId.value} of project ${projectId.value} not found")
