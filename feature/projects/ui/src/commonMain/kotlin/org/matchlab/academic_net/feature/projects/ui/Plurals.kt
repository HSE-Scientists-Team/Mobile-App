package org.matchlab.academic_net.feature.projects.ui

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.pluralStringResource
import org.matchlab.academic_net.feature.projects.ui.generated.resources.Res
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_completed_count
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_roles_count

/** "1 роль", "2 роли", "5 ролей" | "1 role", "5 roles". */
@Composable
internal fun rolesCountText(count: Int): String =
    pluralStringResource(Res.plurals.projects_roles_count, count, count)

/** "1 завершённый проект", "3 завершённых проекта", "5 завершённых проектов" | "3 completed projects". */
@Composable
internal fun completedProjectsText(count: Int): String =
    pluralStringResource(Res.plurals.projects_completed_count, count, count)
