package org.matchlab.academic_net.common.model

/**
 * Where a person is in their studies: the level and the year, either of which may be unknown.
 *
 * docs/api-values.md: the API's `study_level` is a string without a list of values, so [level] is the text as the
 * person typed it or as the server sent it ("Бакалавриат", "Master"), never an enum. The app shows it as
 * it is, with the year ("Бакалавриат, 3 курс"); how that line is written and translated is the UI's job.
 *
 * @property level free text of the level; `null` when the person gave none (a blank text is `null` too).
 * @property year the year of study, 1 or more; `null` when it is unknown or not given.
 */
data class Study(
    val level: String? = null,
    val year: Int? = null,
)
