package org.matchlab.academic_net.feature.projects.ui

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.feature.projects.ui.generated.resources.Res
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_completed_count_few
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_completed_count_many
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_completed_count_one
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_roles_count_few
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_roles_count_many
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_roles_count_one
import org.matchlab.academic_net.utils.RussianPluralForm
import org.matchlab.academic_net.utils.russianPluralForm

/** "1 роль", "2 роли", "5 ролей". */
@Composable
internal fun rolesCountText(count: Int): String = stringResource(
    when (russianPluralForm(count)) {
        RussianPluralForm.One -> Res.string.projects_roles_count_one
        RussianPluralForm.Few -> Res.string.projects_roles_count_few
        RussianPluralForm.Many -> Res.string.projects_roles_count_many
    },
    count,
)

/** "1 завершённый проект", "3 завершённых проекта", "5 завершённых проектов". */
@Composable
internal fun completedProjectsText(count: Int): String = stringResource(
    when (russianPluralForm(count)) {
        RussianPluralForm.One -> Res.string.projects_completed_count_one
        RussianPluralForm.Few -> Res.string.projects_completed_count_few
        RussianPluralForm.Many -> Res.string.projects_completed_count_many
    },
    count,
)
