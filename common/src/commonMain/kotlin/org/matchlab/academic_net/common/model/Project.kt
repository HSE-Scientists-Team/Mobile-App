package org.matchlab.academic_net.common.model

import kotlinx.datetime.YearMonth

/**
 * Field of science or practice a project belongs to ("Computer Science", "Биология", ...).
 *
 * API-GAP G46: the API names a field by its text (`field` of a project and of the search) and has no list of fields,
 * so a sphere read from the server or picked from the app's list has that text as both [id] and [name].
 */
data class Sphere(
    val id: String,
    val name: String,
)

/** The kind of a project. API-GAP G40: the server's `type` has no list of values, so the Gateway never gives one. */
enum class ProjectType {
    /** "Научный": research, ends with a publication. */
    Scientific,

    /** "Проектный": practical work, ends with a product. */
    Practical,
}

/** Where a project is in its life. API-GAP G41: the server's `status` has no list of values, so the Gateway never gives one. */
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

/**
 * A position in the project's team: [slots] places, [occupied] of them taken.
 *
 * @property slots how many people the role is for, at least 1. The app creates roles for one person (API-GAP G49).
 * @property members the people on the role the app knows. API-GAP G52: the server's members are not read, so on the
 *  Gateway the list is empty even when [occupied] is more than 0.
 * @property occupied places taken as the server counts them; never negative, may be more than [members].size.
 */
data class Role(
    val id: RoleId,
    val title: String,
    val skills: List<String> = emptyList(),
    val slots: Int = 1,
    val members: List<RoleMember> = emptyList(),
    val occupied: Int = members.size,
) {
    init {
        require(slots >= 1) { "A role is for at least one person: slots = $slots" }
        require(occupied >= 0) { "Taken places cannot be negative: occupied = $occupied" }
    }

    /** A place is free: the initiator may look for people for the role. */
    val isVacant: Boolean get() = occupied < slots

    /** Free places; 0 when the server counts as many taken places as there are, or more. */
    val vacantPlaces: Int get() = (slots - occupied).coerceAtLeast(0)
}

/** The months a project runs, from [start] to [end] inclusive; [end] is not before [start]. */
data class ProjectPeriod(
    val start: YearMonth,
    val end: YearMonth,
)

/**
 * A project as the app shows it. What the API does not define is `null` ("unknown"), never a guess: the screens
 * leave out what they cannot show.
 *
 * @property type API-GAP G40: `null` when unknown, which is always so on the Gateway.
 * @property status API-GAP G41: `null` when unknown, always so on the Gateway; no management action is offered then.
 * @property period API-GAP G43: `null` when unknown, always so on the Gateway (the format of `planned_*` is not defined).
 * @property viewerIsInitiator the user who asked is the project's owner (`owner_user_id`). Only the initiator manages
 *  the project and looks for people for its roles.
 */
data class Project(
    val id: ProjectId,
    val title: String,
    val type: ProjectType?,
    val sphere: Sphere,
    /** What problem the project solves. */
    val problem: String,
    /** What the project should produce. */
    val goal: String,
    val tags: List<String>,
    val initiator: UserSummary,
    val roles: List<Role>,
    val status: ProjectStatus?,
    val period: ProjectPeriod?,
    val viewerIsInitiator: Boolean,
) {
    /** The roles with a free place, in the order of [roles]. */
    val vacantRoles: List<Role> get() = roles.filter { it.isVacant }

    /** Taken places of all roles, a role counted up to its [Role.slots]: the "2" of "2 из 4 занято". */
    val occupiedPlaces: Int get() = roles.sumOf { minOf(it.occupied, it.slots) }

    /** Places of all roles: the "4" of "2 из 4 занято". */
    val totalPlaces: Int get() = roles.sumOf { it.slots }
}
