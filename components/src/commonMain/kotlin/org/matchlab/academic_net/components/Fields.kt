package org.matchlab.academic_net.components

import academicnet.components.generated.resources.Res
import academicnet.components.generated.resources.components_add
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource

// In the design the 1dp outline adds to the component size, so the inner padding is 1dp larger
// than the Figma padding (the text stays exactly where Figma puts it).
// The text inputs are built on AppBasicTextField, so the caret and the selection survive rotation.

/**
 * "Search bar": full-round field with a search icon. The caret survives configuration changes
 * (see [AppBasicTextField]).
 */
@Composable
fun AppSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSearch: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    AppBasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        enabled = enabled,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() }),
        decorationBox = { innerTextField ->
            Row(
                Modifier
                    .background(colors.surface, CircleShape)
                    .border(1.dp, colors.outlineVariant, CircleShape)
                    .padding(horizontal = 17.dp, vertical = 13.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(AppIcons.Search, contentDescription = null, size = 20.dp, tint = colors.onSurfaceVariant)
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Placeholder(placeholder)
                    innerTextField()
                }
            }
        },
    )
}

/**
 * "Search bar" that is a button: looks like [AppSearchBar] but does not take input; tapping it
 * opens the search screen (the feed). With `onClick = null` it is a plain, inactive bar.
 */
@Composable
fun AppSearchBarButton(
    placeholder: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(colors.surface, CircleShape)
            .border(1.dp, colors.outlineVariant, CircleShape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 17.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(AppIcons.Search, contentDescription = null, size = 20.dp, tint = colors.onSurfaceVariant)
        Placeholder(placeholder)
    }
}

/**
 * "Text field/Outlined": label above a 12dp-rounded outlined box. The caret survives configuration
 * changes (see [AppBasicTextField]).
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    // Not in the design: error state is the Material 3 one (error outline and a line under the box).
    errorText: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FieldLabel(label)
        AppBasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = singleLine,
            minLines = minLines,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            interactionSource = interactionSource,
            decorationBox = { innerTextField ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .fieldBox(focused, isError = errorText != null)
                        .padding(horizontal = 17.dp, vertical = 15.dp),
                ) {
                    if (value.isEmpty()) Placeholder(placeholder)
                    innerTextField()
                }
            },
        )
        if (errorText != null) FieldError(errorText)
    }
}

/**
 * "Text field/Outlined" that opens a picker instead of taking input (sphere, dates): shows
 * [value], or [placeholder] while nothing is chosen.
 */
@Composable
fun AppSelectField(
    label: String,
    value: String?,
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    errorText: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FieldLabel(label)
        Row(
            Modifier
                .fillMaxWidth()
                .fieldBox(focused = false, isError = errorText != null)
                .clickable(role = Role.DropdownList, onClick = onClick)
                .padding(horizontal = 17.dp, vertical = 15.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (value.isNullOrEmpty()) {
                    Placeholder(placeholder)
                } else {
                    Text(value, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface, maxLines = 1)
                }
            }
            AppIcon(AppIcons.ChevronRight, contentDescription = null, size = 20.dp, tint = colors.outline)
        }
        if (errorText != null) FieldError(errorText)
    }
}

/** "Text field/Tags": entered tags as input chips plus a field to add new ones. */
@Composable
fun AppTagsField(
    label: String,
    tags: List<String>,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    var input by rememberSaveable { mutableStateOf("") }
    val commit = {
        val tag = input.trim()
        if (tag.isNotEmpty()) {
            onAddTag(tag)
            input = ""
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FieldLabel(label)
        Column(
            Modifier
                .fillMaxWidth()
                .fieldBox(focused)
                .padding(horizontal = 17.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    tags.forEach { tag -> AppInputChip(tag, onRemove = { onRemoveTag(tag) }) }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppBasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commit() }),
                    interactionSource = interactionSource,
                    decorationBox = { innerTextField ->
                        Box {
                            if (input.isEmpty()) Placeholder(placeholder)
                            innerTextField()
                        }
                    },
                )
                AppIcon(
                    icon = AppIcons.Plus,
                    contentDescription = stringResource(Res.string.components_add),
                    modifier = Modifier.clickable(onClick = commit),
                    size = 20.dp,
                    tint = colors.primary,
                )
            }
        }
    }
}

@Composable
private fun Modifier.fieldBox(focused: Boolean, isError: Boolean = false): Modifier {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    val outline = when {
        isError -> colors.error
        focused -> colors.primary
        else -> colors.outlineVariant
    }
    return background(colors.surface, shape).border(1.dp, outline, shape)
}

@Composable
private fun FieldError(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Placeholder(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = color, maxLines = 1)
}
