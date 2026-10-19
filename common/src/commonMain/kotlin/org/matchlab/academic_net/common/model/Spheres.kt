package org.matchlab.academic_net.common.model

/**
 * The spheres a project can belong to: the app's own list of 12, the same for every viewer.
 *
 * The API has no list of fields (the `field` of a project and of the search is a plain string), so the app defines it and
 * sends the number (`docs/api-values.md`, decision of 2026-10-19). [Sphere.id] is the code "1".."12" and [Sphere.name] the
 * label the screens show. This list is the one place of the table of codes: a code is never reused or renumbered.
 */
object Spheres {
    /** The spheres in the order the pickers list them; each code once. */
    val all: List<Sphere> = listOf(
        Sphere(id = "1", name = "Computer Science"),
        Sphere(id = "2", name = "Data Science"),
        Sphere(id = "3", name = "Инженерия"),
        Sphere(id = "4", name = "Математика"),
        Sphere(id = "5", name = "Физика"),
        Sphere(id = "6", name = "Химия"),
        Sphere(id = "7", name = "Биология"),
        Sphere(id = "8", name = "Медицина"),
        Sphere(id = "9", name = "Экономика"),
        Sphere(id = "10", name = "Социология"),
        Sphere(id = "11", name = "Лингвистика"),
        Sphere(id = "12", name = "Дизайн"),
    )

    /**
     * The sphere whose [Sphere.id] is [raw]: an exact match once the outer spaces are cut (" 3 " is "Инженерия"). Anything
     * else is "unknown", `null`: `null`, blank, a number outside 1..12, "03", a name such as "Биология".
     */
    fun fromCode(raw: String?): Sphere? {
        val code = raw?.trim() ?: return null
        return all.firstOrNull { it.id == code }
    }
}
