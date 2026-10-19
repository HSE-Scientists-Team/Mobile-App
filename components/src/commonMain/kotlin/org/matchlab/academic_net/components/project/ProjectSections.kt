package org.matchlab.academic_net.components.project

import academicnet.components.generated.resources.Res
import academicnet.components.generated.resources.components_project_block_goal
import academicnet.components.generated.resources.components_project_block_problem
import academicnet.components.generated.resources.components_project_block_tags
import academicnet.components.generated.resources.components_project_roles_occupied
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.common.model.ProjectPeriod
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.components.AppIcons
import org.matchlab.academic_net.components.AppTagChip
import org.matchlab.academic_net.components.InfoCard
import org.matchlab.academic_net.components.TagsBlock
import org.matchlab.academic_net.components.TextBlock
import org.matchlab.academic_net.components.format.monthRangeText

/**
 * The top of a project page, shared by "07 Управление проектом" and "04 Проект": the title with
 * the chips of the project [type], [status] and [sphere] under it. A chip is drawn only for what is known (a value the
 * app does not recognize is `null`, see `docs/api-values.md`): `null` leaves the chip out, a blank [sphere] too, and a
 * project with no chip at all has no row under the title.
 *
 * @param type the project type, `null` when unknown
 * @param status the project status, `null` when unknown
 * @param sphere the name of the project's sphere, `null` when unknown
 */
@Composable
fun ProjectHeader(
    title: String,
    type: ProjectType?,
    status: ProjectStatus?,
    sphere: String?,
    modifier: Modifier = Modifier,
) {
    val sphereName = sphere?.takeIf { it.isNotBlank() }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (type != null || status != null || sphereName != null) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (type != null) AppTagChip(type.label())
                if (status != null) AppTagChip(status.label())
                if (sphereName != null) AppTagChip(sphereName)
            }
        }
    }
}

/**
 * The description of a project as consecutive blocks of the parent column: the problem, the goal
 * and, when there are any [tags], the tags. The blocks are spaced by the arrangement of the
 * parent, the same as the rest of the project page.
 */
@Composable
fun ColumnScope.ProjectDescriptionBlocks(problem: String, goal: String, tags: List<String>) {
    TextBlock(stringResource(Res.string.components_project_block_problem), problem)
    TextBlock(stringResource(Res.string.components_project_block_goal), goal)
    if (tags.isNotEmpty()) {
        TagsBlock(stringResource(Res.string.components_project_block_tags), tags)
    }
}

/** "Card/Info" with the calendar icon and the months the project runs: "окт 2026 — мар 2027". */
@Composable
fun ProjectPeriodCard(period: ProjectPeriod, modifier: Modifier = Modifier) {
    InfoCard(
        text = monthRangeText(period.start, period.end),
        icon = AppIcons.Calendar,
        modifier = modifier,
    )
}

/**
 * The heading of the roles of a project with the counter of the filled places on the right:
 * "Вакантные роли" and "2 из 4 занято". The [title] is passed in because the pages call the list
 * differently ("Роли" for the initiator, "Вакантные роли" for the others).
 *
 * The counter counts places, not roles, as a role can be for several people (API-GAP G49): pass
 * `Project.occupiedPlaces` and `Project.totalPlaces`.
 *
 * @param occupiedCount the places of all roles that are taken
 * @param totalCount the places of all roles
 */
@Composable
fun RolesSectionHeader(
    title: String,
    occupiedCount: Int,
    totalCount: Int,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            color = colors.onSurface,
        )
        Text(
            text = stringResource(Res.string.components_project_roles_occupied, occupiedCount, totalCount),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
        )
    }
}
