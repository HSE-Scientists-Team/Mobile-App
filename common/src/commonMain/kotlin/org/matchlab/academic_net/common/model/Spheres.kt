package org.matchlab.academic_net.common.model

/**
 * The spheres a project can belong to: the app's own list of 12, the same for every viewer.
 *
 * The API has no list of fields (the `field` of a project and of the search is a plain string), so the app defines it and
 * sends the name as text (`docs/api-values.md`, decision of 2026-10-19). [Sphere.id] is the text sent to the server and is
 * the same as [Sphere.name], the label the screens show. This list is the one place of the table of values.
 */
object Spheres {
    /** The spheres in the order the pickers list them; each name once. */
    val all: List<Sphere> = listOf(
        Sphere(id = "Computer Science", name = "Computer Science"),
        Sphere(id = "Data Science", name = "Data Science"),
        Sphere(id = "Инженерия", name = "Инженерия"),
        Sphere(id = "Математика", name = "Математика"),
        Sphere(id = "Физика", name = "Физика"),
        Sphere(id = "Химия", name = "Химия"),
        Sphere(id = "Биология", name = "Биология"),
        Sphere(id = "Медицина", name = "Медицина"),
        Sphere(id = "Экономика", name = "Экономика"),
        Sphere(id = "Социология", name = "Социология"),
        Sphere(id = "Лингвистика", name = "Лингвистика"),
        Sphere(id = "Дизайн", name = "Дизайн"),
    )

    /**
     * The sphere whose [Sphere.id] is [raw]: an exact match once the outer spaces are cut (" Инженерия " is "Инженерия"),
     * the case is not changed. Anything else is "unknown", `null`: `null`, blank, a name that is not in the list,
     * "биология".
     */
    fun fromCode(raw: String?): Sphere? {
        val code = raw?.trim() ?: return null
        return all.firstOrNull { it.id == code }
    }
}
