package org.matchlab.academic_net.components.project

import academicnet.components.generated.resources.Res
import academicnet.components.generated.resources.components_project_roles_count
import academicnet.components.generated.resources.components_project_status_recruiting
import academicnet.components.generated.resources.components_project_status_unpublished
import academicnet.components.generated.resources.components_project_type_practical
import academicnet.components.generated.resources.components_project_type_scientific
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.common.model.ProjectPeriod
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.components.AppAvatars
import org.matchlab.academic_net.components.ProjectCardUiModel
import org.matchlab.academic_net.components.format.monthRangeText

/** Name of the project type in the app language: "Научный" | "Scientific". */
@Composable
fun ProjectType.label(): String = stringResource(
    when (this) {
        ProjectType.Scientific -> Res.string.components_project_type_scientific
        ProjectType.Practical -> Res.string.components_project_type_practical
    },
)

/** Name of the project status in the app language: "Поиск соавторов" | "Recruiting". */
@Composable
fun ProjectStatus.label(): String = stringResource(
    when (this) {
        ProjectStatus.Recruiting -> Res.string.components_project_status_recruiting
        ProjectStatus.Unpublished -> Res.string.components_project_status_unpublished
    },
)

/** Team size with the right plural form: "1 роль", "2 роли", "5 ролей" | "1 role", "5 roles". */
@Composable
fun rolesCountText(count: Int): String =
    pluralStringResource(Res.plurals.components_project_roles_count, count, count)

/**
 * The last line of a project card: the team size and the months the project runs, e.g.
 * "4 роли · окт 2026 — мар 2027". Dates follow the app language, see `monthRangeText`.
 */
@Composable
fun projectCardFooter(rolesCount: Int, period: ProjectPeriod): String =
    "${rolesCountText(rolesCount)} · ${monthRangeText(period.start, period.end)}"

/**
 * Builds the [ProjectCardUiModel] every list of projects shows, so the chips and the footer look
 * the same in "Мои проекты" and in the search: the type chip first, then [status] and [sphere]
 * as tags.
 *
 * @param author the author line, e.g. "Проф. Елена Васкес · Stanford" or "Вы инициатор"
 * @param avatarKey stands in for the author's picture until profiles carry images
 *  (see [AppAvatars.forKey])
 */
@Composable
fun projectCardUiModel(
    title: String,
    author: String,
    avatarKey: String,
    isAuthorVerified: Boolean,
    type: ProjectType,
    status: ProjectStatus,
    sphere: String,
    rolesCount: Int,
    period: ProjectPeriod,
): ProjectCardUiModel = ProjectCardUiModel(
    title = title,
    author = author,
    authorAvatar = AppAvatars.forKey(avatarKey),
    type = type.label(),
    footer = projectCardFooter(rolesCount, period),
    tags = listOf(status.label(), sphere),
    isAuthorVerified = isAuthorVerified,
)
