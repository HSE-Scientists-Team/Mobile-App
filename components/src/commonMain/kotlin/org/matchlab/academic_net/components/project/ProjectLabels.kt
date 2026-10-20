package org.matchlab.academic_net.components.project

import academicnet.components.generated.resources.Res
import academicnet.components.generated.resources.components_project_roles_count
import academicnet.components.generated.resources.components_project_status_cancelled
import academicnet.components.generated.resources.components_project_status_chip_recruiting
import academicnet.components.generated.resources.components_project_status_finished
import academicnet.components.generated.resources.components_project_status_recruiting
import academicnet.components.generated.resources.components_project_status_started
import academicnet.components.generated.resources.components_project_status_unpublished
import academicnet.components.generated.resources.components_project_type_practical
import academicnet.components.generated.resources.components_project_type_scientific
import academicnet.components.generated.resources.components_role_places
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.common.model.ProjectPeriod
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.components.AppStatusChipTone
import org.matchlab.academic_net.components.AvatarSource
import org.matchlab.academic_net.components.ProjectCardUiModel
import org.matchlab.academic_net.components.StatusChipUiModel
import org.matchlab.academic_net.components.format.monthRangeText

/** Name of the project type in the app language: "Научный" | "Scientific". */
@Composable
fun ProjectType.label(): String = stringResource(
    when (this) {
        ProjectType.Scientific -> Res.string.components_project_type_scientific
        ProjectType.Practical -> Res.string.components_project_type_practical
    },
)

/**
 * Name of the project status in the app language: "Поиск соавторов" | "Recruiting", "Снят с публикации", "В работе",
 * "Завершён", "Отменён". It is the text of the tag in the header of a project page.
 */
@Composable
fun ProjectStatus.label(): String = stringResource(
    when (this) {
        ProjectStatus.Recruiting -> Res.string.components_project_status_recruiting
        ProjectStatus.Unpublished -> Res.string.components_project_status_unpublished
        ProjectStatus.Started -> Res.string.components_project_status_started
        ProjectStatus.Finished -> Res.string.components_project_status_finished
        ProjectStatus.Cancelled -> Res.string.components_project_status_cancelled
    },
)

/**
 * The status as a "Chip/Status" (the status of "Мои проекты" and of 07c): the recruiting project is "Опубликован" |
 * "Published" here, the others are named by [label]. Colors: published and finished — success, unpublished — off,
 * started — progress, cancelled — cancelled. The chip is drawn without an icon.
 */
@Composable
fun ProjectStatus.chip(): StatusChipUiModel = StatusChipUiModel(
    text = if (this == ProjectStatus.Recruiting) {
        stringResource(Res.string.components_project_status_chip_recruiting)
    } else {
        label()
    },
    tone = when (this) {
        ProjectStatus.Recruiting, ProjectStatus.Finished -> AppStatusChipTone.Success
        ProjectStatus.Unpublished -> AppStatusChipTone.Off
        ProjectStatus.Started -> AppStatusChipTone.Progress
        ProjectStatus.Cancelled -> AppStatusChipTone.Cancelled
    },
)

/** Team size with the right plural form: "1 роль", "2 роли", "5 ролей" | "1 role", "5 roles". */
@Composable
fun rolesCountText(count: Int): String =
    pluralStringResource(Res.plurals.components_project_roles_count, count, count)

/**
 * The last line of a project card: the team size and, when the months the project runs are known, the months, e.g.
 * "4 роли · окт 2026 — мар 2027". Without a [period] (the server's dates could not be read, see `docs/api-values.md`)
 * it is the team size alone: "4 роли". Dates follow the app language, see `monthRangeText`.
 *
 * @param rolesCount how many roles the project has
 * @param period the months the project runs, `null` while they are unknown
 */
@Composable
fun projectCardFooter(rolesCount: Int, period: ProjectPeriod?): String {
    val roles = rolesCountText(rolesCount)
    return if (period == null) roles else "$roles · ${monthRangeText(period.start, period.end)}"
}

/**
 * Builds the [ProjectCardUiModel] every list of projects shows, so the chips and the footer look
 * the same in "Мои проекты" and in the search: the type chip first, then the [status] and the [sphere]
 * as tags. A value the app does not know is left out, not guessed (`docs/api-values.md`): no [type] means no type
 * chip, no [status] no status tag, a `null` or blank [sphere] no sphere tag, no [period] no months in the footer.
 * The project itself is always drawn.
 *
 * @param author the author line, e.g. "Проф. Елена Васкес · Stanford" or "Вы инициатор"
 * @param avatarKey the key of the author's picture (the user id)
 * @param avatarName the author's name as the server has it, for the initials shown until the picture is there; not the
 *  text that stands in for a missing name
 * @param type the project type, `null` when unknown
 * @param status the project status, `null` when unknown
 * @param sphere the name of the project's sphere, `null` when unknown
 * @param period the months the project runs, `null` when unknown
 * @param statusAsChip the status is a "Chip/Status" right after the type ([chip], "Мои проекты"), not a tag among the
 *  others
 */
@Composable
fun projectCardUiModel(
    title: String,
    author: String,
    avatarKey: String,
    avatarName: String,
    type: ProjectType?,
    status: ProjectStatus?,
    sphere: String?,
    rolesCount: Int,
    period: ProjectPeriod?,
    statusAsChip: Boolean = false,
): ProjectCardUiModel = ProjectCardUiModel(
    title = title,
    author = author,
    authorAvatar = AvatarSource(avatarKey, avatarName),
    type = type?.label(),
    footer = projectCardFooter(rolesCount, period),
    statusChip = if (statusAsChip) status?.chip() else null,
    tags = listOfNotNull(if (statusAsChip) null else status?.label(), sphere?.takeIf { it.isNotBlank() }),
)

/**
 * How many places of a role are taken, with the plural form of the places: "1 из 3 мест занято", "0 из 1 места занято"
 * | "1 of 3 spots filled", "0 of 1 spot filled". The form follows [slots], the number the places are counted in.
 *
 * @param occupied places of the role that are taken
 * @param slots places of the role
 */
@Composable
fun rolePlacesText(occupied: Int, slots: Int): String =
    pluralStringResource(Res.plurals.components_role_places, slots, occupied, slots)
