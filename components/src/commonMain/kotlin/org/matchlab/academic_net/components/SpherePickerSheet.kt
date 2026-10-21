package org.matchlab.academic_net.components

import academicnet.components.generated.resources.Res
import academicnet.components.generated.resources.components_retry
import academicnet.components.generated.resources.components_sphere_picker_error
import academicnet.components.generated.resources.components_sphere_picker_title
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

/**
 * Sphere choice as a list of radio rows in a sheet. The design shows the sphere as a plain text
 * field; picking from the server's list is what "выбрать сферу проекта" asks for.
 *
 * The list comes from the server, so the sheet also shows the two other states: a progress
 * indicator while [spheres] is empty and a message with "Повторить" when [loadFailed].
 *
 * @param selected the chosen sphere; `null` means [anyOptionLabel] is the chosen row
 * @param onSelect a sphere row was tapped
 * @param anyOptionLabel when not `null` a first row with this text ("Любая сфера") is shown for a
 *  filter that can stay unset; tapping it calls [onSelectAny]. A form that needs a sphere leaves it `null`.
 */
@Composable
fun SpherePickerSheet(
    spheres: List<Sphere>,
    selected: Sphere?,
    loadFailed: Boolean,
    onSelect: (Sphere) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    anyOptionLabel: String? = null,
    onSelectAny: () -> Unit = {},
) {
    AppBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        SheetHeader(stringResource(Res.string.components_sphere_picker_title))
        when {
            spheres.isNotEmpty() -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (anyOptionLabel != null) {
                    item(key = AnyOptionKey) {
                        RadioListItem(text = anyOptionLabel, selected = selected == null, onClick = onSelectAny)
                    }
                }
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
                    text = stringResource(Res.string.components_sphere_picker_error),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AppButton(
                    text = stringResource(Res.string.components_retry),
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

// Not a sphere id: the ids are the server's, this one holds a character they do not use.
private const val AnyOptionKey = "\u0000any"
