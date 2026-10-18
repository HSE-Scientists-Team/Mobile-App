package org.matchlab.academic_net.feature.projects.data

import org.matchlab.academic_net.common.model.Sphere

/**
 * The spheres a project can belong to: the app's own list of 12, the same for every viewer. API-GAP G46: the API names a
 * field by its text and has no list of fields, so a sphere's [Sphere.id] is its [Sphere.name] and the name is what the
 * server is sent (`field`).
 */
internal object SphereCatalog {
    val spheres: List<Sphere> = listOf(
        "Computer Science",
        "Data Science",
        "Инженерия",
        "Математика",
        "Физика",
        "Химия",
        "Биология",
        "Медицина",
        "Экономика",
        "Социология",
        "Лингвистика",
        "Дизайн",
    ).map { Sphere(id = it, name = it) }
}
