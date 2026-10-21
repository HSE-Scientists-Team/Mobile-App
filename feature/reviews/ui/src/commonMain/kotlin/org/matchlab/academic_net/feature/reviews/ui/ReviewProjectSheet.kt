package org.matchlab.academic_net.feature.reviews.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.components.AppBottomSheet
import org.matchlab.academic_net.components.AppButton
import org.matchlab.academic_net.components.RadioListItem
import org.matchlab.academic_net.components.SheetHeader
import org.matchlab.academic_net.feature.reviews.ui.generated.resources.Res
import org.matchlab.academic_net.feature.reviews.ui.generated.resources.reviews_picker_empty_text
import org.matchlab.academic_net.feature.reviews.ui.generated.resources.reviews_picker_empty_title
import org.matchlab.academic_net.feature.reviews.ui.generated.resources.reviews_picker_error_text
import org.matchlab.academic_net.feature.reviews.ui.generated.resources.reviews_picker_error_title
import org.matchlab.academic_net.feature.reviews.ui.generated.resources.reviews_picker_loading
import org.matchlab.academic_net.feature.reviews.ui.generated.resources.reviews_picker_ok
import org.matchlab.academic_net.feature.reviews.ui.generated.resources.reviews_picker_retry
import org.matchlab.academic_net.feature.reviews.ui.generated.resources.reviews_picker_select
import org.matchlab.academic_net.feature.reviews.ui.generated.resources.reviews_picker_title

/**
 * 16c-16f "Общий проект": the sheet that picks the project of the review. It shows what the read of the common projects
 * is at the moment: the progress (16d), the list as radio rows with the date of the completion under the title and
 * "Выбрать" (16c), "Нет общих завершённых проектов" with "Понятно" (16e), or "Не удалось загрузить проекты" with
 * "Повторить" (16f).
 *
 * A row marks the project in the sheet only; "Выбрать" takes it into the form. The sheet starts with the project of the
 * form marked, or with the first one when none is chosen yet (as the design draws it), so "Выбрать" always has something to
 * take.
 *
 * @param projects what the form has read
 * @param selected the project the form has now
 * @param onSelect "Выбрать": the project that was marked
 * @param onRetry "Повторить" after a failed read
 */
@Composable
internal fun ReviewProjectSheet(
    projects: ReviewProjects,
    selected: ProjectId?,
    onSelect: (ProjectId) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        SheetHeader(title = stringResource(Res.string.reviews_picker_title))
        when (projects) {
            ReviewProjects.Loading -> PickerLoading()
            is ReviewProjects.Failed -> PickerMessage(
                title = stringResource(Res.string.reviews_picker_error_title),
                text = stringResource(Res.string.reviews_picker_error_text),
                actionText = stringResource(Res.string.reviews_picker_retry),
                onAction = onRetry,
            )
            is ReviewProjects.Loaded -> if (projects.projects.isEmpty()) {
                PickerMessage(
                    title = stringResource(Res.string.reviews_picker_empty_title),
                    text = stringResource(Res.string.reviews_picker_empty_text),
                    actionText = stringResource(Res.string.reviews_picker_ok),
                    onAction = onDismiss,
                    // The design keeps the text of this state narrower than the sheet: it wraps in two lines.
                    textMaxWidth = EmptyTextWidth,
                )
            } else {
                PickerList(projects, selected, onSelect)
            }
        }
    }
}

/** The width the text of "Нет общих завершённых проектов" wraps at. */
private val EmptyTextWidth = 290.dp

/** 16d: the progress next to "Загрузка…", where the list will be. */
@Composable
private fun PickerLoading() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp)
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 2.dp,
            strokeCap = StrokeCap.Round,
        )
        Text(
            text = stringResource(Res.string.reviews_picker_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 16e and 16f: a title, a line under it and one button at the foot of the sheet. */
@Composable
private fun ColumnScope.PickerMessage(
    title: String,
    text: String,
    actionText: String,
    onAction: () -> Unit,
    textMaxWidth: Dp = Dp.Unspecified,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = text,
            modifier = Modifier.widthIn(max = textMaxWidth),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
    SheetFooter { AppButton(text = actionText, onClick = onAction, modifier = Modifier.fillMaxWidth()) }
}

/** 16c: the common projects as radio rows, and "Выбрать". */
@Composable
private fun ColumnScope.PickerList(
    projects: ReviewProjects.Loaded,
    selected: ProjectId?,
    onSelect: (ProjectId) -> Unit,
) {
    // What is marked in the sheet; nothing until a row is tapped, then the project of the form, or the first one.
    var marked by rememberSaveable { mutableStateOf<String?>(null) }
    val effective = projects.projects.firstOrNull { it.id.value == marked }?.id
        ?: projects.projects.firstOrNull { it.id == selected }?.id
        ?: projects.projects.first().id
    Column(
        Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        projects.projects.forEach { project ->
            RadioListItem(
                text = project.title,
                selected = project.id == effective,
                onClick = { marked = project.id.value },
                subtitle = completedCaption(project),
            )
        }
    }
    SheetFooter {
        AppButton(
            text = stringResource(Res.string.reviews_picker_select),
            onClick = { onSelect(effective) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The foot of the sheet with its button: 12dp above it and 24dp under it, 16dp at the sides. */
@Composable
private fun SheetFooter(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp)) { content() }
}
