package org.matchlab.academic_net.feature.projects.ui

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.feature.projects.ui.generated.resources.Res
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_status_recruiting
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_status_unpublished
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_type_practical
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_type_scientific

@Composable
internal fun ProjectType.label(): String = stringResource(
    when (this) {
        ProjectType.Scientific -> Res.string.projects_type_scientific
        ProjectType.Practical -> Res.string.projects_type_practical
    },
)

@Composable
internal fun ProjectStatus.label(): String = stringResource(
    when (this) {
        ProjectStatus.Recruiting -> Res.string.projects_status_recruiting
        ProjectStatus.Unpublished -> Res.string.projects_status_unpublished
    },
)
