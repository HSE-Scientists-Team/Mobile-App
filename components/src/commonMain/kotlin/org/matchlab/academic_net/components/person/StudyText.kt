package org.matchlab.academic_net.components.person

import academicnet.components.generated.resources.Res
import academicnet.components.generated.resources.components_study_level_year
import academicnet.components.generated.resources.components_study_year
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.common.model.Collaborator
import org.matchlab.academic_net.common.model.Study

/**
 * Where a person is in their studies, as one line: the level and the year of [study].
 *
 * docs/api-values.md: the level is a free text (the API has no list of its values), so it is shown as the person typed it
 * or the server sent it, with the first letter in capitals; only the word "курс" / "year" is the app's own and follows
 * the language of the app. A part that is not given is left out, and nothing is shown when neither is:
 *
 * | level | year | русский | English |
 * |---|---|---|---|
 * | "бакалавриат" | 3 | Бакалавриат, 3 курс | Бакалавриат, year 3 |
 * | "Master" | 2 | Master, 2 курс | Master, year 2 |
 * | "Аспирантура" | `null` | Аспирантура | Аспирантура |
 * | `null` or blank | 3 | 3 курс | Year 3 |
 * | `null` | `null` | `null` | `null` |
 *
 * The level is not translated (the app cannot know what a free text means), so a level in Russian stays in Russian
 * in the English app.
 *
 * @return the line, or `null` when [study] says nothing
 */
@Composable
fun studyText(study: Study): String? {
    val level = studyLevelText(study.level)
    val year = studyYearOrNull(study.year)
    return when {
        level != null && year != null -> stringResource(Res.string.components_study_level_year, level, year)
        level != null -> level
        year != null -> stringResource(Res.string.components_study_year, year)
        else -> null
    }
}

/**
 * The line under the name of a person in the lists and on their profile: the university, the study ([studyText])
 * and the field, joined with " · " and each left out when it is empty: "Stanford · Бакалавриат, 3 курс · Computer
 * Science". The line is empty for a person with none of the three.
 */
@Composable
fun Collaborator.subtitleText(): String = joinSubtitle(university, studyText(study), field)

/**
 * The level of study as it is shown: outer spaces cut, the first letter in capitals ("бакалавриат" is
 * "Бакалавриат"; the rest of the text is left as it is), `null` for a missing or blank level.
 */
internal fun studyLevelText(level: String?): String? =
    level?.trim()?.takeIf { it.isNotEmpty() }?.replaceFirstChar { it.uppercase() }

/** The year of study when it is a year ([Study.year] is 1 or more), otherwise `null`. */
internal fun studyYearOrNull(year: Int?): Int? = year?.takeIf { it > 0 }

/**
 * The parts of the line under a person's name that have something in them, joined with " · ": the [university], the
 * [study] line and the [field]. A blank part is left out.
 */
internal fun joinSubtitle(university: String, study: String?, field: String): String =
    listOf(university, study.orEmpty(), field).filter { it.isNotBlank() }.joinToString(" · ")
