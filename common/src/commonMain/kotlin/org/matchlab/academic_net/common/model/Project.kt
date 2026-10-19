package org.matchlab.academic_net.common.model

import kotlinx.datetime.YearMonth

/**
 * Field of science or practice a project belongs to ("Computer Science", "Биология", ...).
 *
 * The API has no list of fields (`field` is a plain string), so the app defines it and sends the number
 * (`docs/api-values.md`, decision of 2026-10-19): the spheres are listed in [Spheres], and a sphere read from the server
 * is the one whose code the server's `field` holds.
 *
 * @property id the code of the sphere, a string of digits ("1".."12"): what the server stores in `field` and what is
 *  sent as `field` when creating a project and searching.
 * @property name the label the screens show.
 */
data class Sphere(
    val id: String,
    val name: String,
)

/**
 * The kind of a project. The API has no list of values for it, so the app defines the codes (`docs/api-values.md`,
 * decision of 2026-10-19) and sends them as `type` of a project, as the `type` filter of the project search, as the
 * `project_type` filter of the people search and in the `project_types` of a profile.
 *
 * @property code the string of digits the server stores: the one place where the table of codes lives; a code is never
 *  reused or renumbered.
 */
enum class ProjectType(val code: String) {
    /** "Научный": research, ends with a publication. */
    Scientific("1"),

    /** "Проектный": practical work, ends with a product. */
    Practical("2"),
    ;

    companion object {
        /**
         * The type whose [code] is [raw]: an exact match once the outer spaces are cut (" 1 " is [Scientific]). Anything
         * else is "unknown", `null`: `null`, blank, another number, "01", "1.0".
         */
        fun fromCode(raw: String?): ProjectType? {
            val code = raw?.trim() ?: return null
            return entries.firstOrNull { it.code == code }
        }
    }
}

/**
 * Where a project is in its life. The API has no list of values for the server's `status`, so the app defines the codes it
 * reads (`docs/api-values.md`, decision of 2026-10-19); the status is only read, never sent: the app moves a project by
 * the `action` of a transition.
 *
 * **Not confirmed by the backend:** the server writes the status itself, so these codes are the app's reading of it until
 * the backend says otherwise; a status the app has no code for is "unknown" (`null` in `Project.status`).
 *
 * @property code the string of digits the app takes the status from; the one place where the table of codes lives.
 */
enum class ProjectStatus(val code: String) {
    /** Published and looking for people for its vacant roles. */
    Recruiting("1"),

    /** Taken off publication by the initiator: hidden from search, responses are paused. */
    Unpublished("2"),
    ;

    companion object {
        /**
         * The status whose [code] is [raw]: an exact match once the outer spaces are cut. Anything else (`null`, blank,
         * a number the app has no status for) is "unknown", `null`: the project is shown all the same.
         */
        fun fromCode(raw: String?): ProjectStatus? {
            val code = raw?.trim() ?: return null
            return entries.firstOrNull { it.code == code }
        }
    }
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
 * A project as the app shows it. What the server does not tell, or tells in a value the app does not know, is `null`
 * ("unknown", `docs/api-values.md`, §4), never a guess: the screens leave out what they cannot show, and the project
 * itself is not hidden because of it.
 *
 * @property type `null` when the server's `type` is not the [ProjectType.code] of any type.
 * @property sphere `null` when the server's `field` is not the [Sphere.id] of any sphere of [Spheres].
 * @property status `null` when the server's `status` is not the [ProjectStatus.code] of any status. An unknown status does
 *  not hide the project, nor the action "unpublish" of the initiator: only [ProjectStatus.Unpublished] does.
 * @property period `null` when the server's `planned_start` and `planned_end` cannot be read as two months, the end not
 *  before the start.
 * @property viewerIsInitiator the user who asked is the project's owner (`owner_user_id`). Only the initiator manages
 *  the project and looks for people for its roles.
 */
data class Project(
    val id: ProjectId,
    val title: String,
    val type: ProjectType?,
    val sphere: Sphere?,
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
