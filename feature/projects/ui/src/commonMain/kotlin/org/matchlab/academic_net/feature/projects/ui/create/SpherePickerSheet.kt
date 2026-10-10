package org.matchlab.academic_net.feature.projects.ui.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.common.model.Sphere
import org.matchlab.academic_net.components.AppBottomSheet
import org.matchlab.academic_net.components.AppButton
import org.matchlab.academic_net.components.AppButtonVariant
import org.matchlab.academic_net.components.RadioListItem
import org.matchlab.academic_net.components.SheetHeader
import org.matchlab.academic_net.feature.projects.ui.generated.resources.Res
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_retry
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_sphere_sheet_title
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_spheres_load_error

/**
 * Sphere choice as a list of radio rows in a sheet. The design shows the sphere as a plain text
 * field; picking from the server's list is what "выбрать сферу проекта" asks for.
 */
@Composable
internal fun SpherePickerSheet(
    spheres: List<Sphere>,
    selected: Sphere?,
    loadFailed: Boolean,
    onSelect: (Sphere) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        SheetHeader(stringResource(Res.string.projects_sphere_sheet_title))
        when {
            spheres.isNotEmpty() -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(spheres, key = { it.id }) { sphere ->
                    RadioListItem(
                        text = sphere.name,
                        selected = sphere.id == selected?.id,
                        onClick = { onSelect(sphere) },
                    )
                }
            }
            loadFailed -> Column(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(Res.string.projects_spheres_load_error),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AppButton(
                    text = stringResource(Res.string.projects_retry),
                    onClick = onRetry,
                    variant = AppButtonVariant.Outlined,
                )
            }
            else -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
