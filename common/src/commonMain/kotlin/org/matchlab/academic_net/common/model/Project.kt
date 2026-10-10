package org.matchlab.academic_net.common.model

/** Field of science or practice a project belongs to ("Computer Science", "Биология", ...). */
data class Sphere(
    val id: String,
    val name: String,
)

enum class ProjectType {
    /** "Научный": research, ends with a publication. */
    Scientific,

    /** "Проектный": practical work, ends with a product. */
    Practical,
}

enum class ProjectStatus {
    /** Published and looking for people for its vacant roles. */
    Recruiting,

    /** Taken off publication by the initiator: hidden from search, responses are paused. */
    Unpublished,
}

/** Person who works on a [Role], with the numbers shown on the project's role list. */
data class RoleMember(
    val user: UserSummary,
    /** Average of reviews, `null` while there are none. */
    val rating: Double? = null,
    val completedProjects: Int = 0,
)

/** A position in the project's team, vacant while [member] is `null`. */
data class Role(
    val id: RoleId,
    val title: String,
    val skills: List<String> = emptyList(),
    val member: RoleMember? = null,
) {
    val isVacant: Boolean get() = member == null
}

data class Project(
    val id: ProjectId,
    val title: String,
    val type: ProjectType,
    val sphere: Sphere,
    /** What problem the project solves. */
    val problem: String,
    /** What the project should produce. */
    val goal: String,
    val tags: List<String>,
    val initiator: UserSummary,
    val roles: List<Role>,
    val status: ProjectStatus,
    /**
     * The user who asked for the project is its initiator (the server knows who asks). Only the
     * initiator manages the project and looks for people for its roles.
     */
    val viewerIsInitiator: Boolean,
) {
    val vacantRoles: List<Role> get() = roles.filter { it.isVacant }
    val occupiedRolesCount: Int get() = roles.count { !it.isVacant }
}
