package org.matchlab.academic_net.components

import academicnet.components.generated.resources.Res
import academicnet.components.generated.resources.components_add
import academicnet.components.generated.resources.components_hide_password
import academicnet.components.generated.resources.components_show_password
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
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.components.interaction.onceClickable

// In the design the 1dp outline adds to the component size, so the inner padding is 1dp larger
// than the Figma padding (the text stays exactly where Figma puts it).
// The text inputs are built on AppBasicTextField, so the caret and the selection survive rotation
// (the password field is the one that deliberately does not, see AppPasswordField; so is a text field
// that is given `saveState = false`).

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
 * opens the search screen (the feed). With `onClick = null` it is a plain, inactive bar. A burst
 * of taps runs [onClick] once.
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
            .then(if (onClick != null) Modifier.onceClickable(role = Role.Button, onClick = onClick) else Modifier)
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
 *
 * With [errorText] the outline is `error` and a line of `labelSmall` in `error` stands under the box (the
 * design draws this state as an override of the field, see the registration screens).
 *
 * @param saveState whether the caret, the selection and the text survive a recreation of the activity and the
 * death of the process (see [AppBasicTextField]); `true` for a name or a title. Pass `false` for a field that
 * takes a secret that must not reach the `Bundle` the system writes to disk, such as the one-time token of the
 * confirmation letter ([AppPasswordField] does it for a password). After a recreation the text is whatever [value]
 * is and the caret is at its end. It must not change while the field is shown.
 * @param errorText the reason the text is not accepted, or `null`
 * @param errorAction a button at the end of the error line, for the way out of that very problem: "Войти" next
 * to "Аккаунт с этой почтой уже есть". It is drawn only together with [errorText], which takes the width that
 * is left and wraps. Pass a text button; a 40dp one keeps the line as tall as the design's (a Material 3 button
 * is laid out 48dp tall unless the screen turns that off).
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
    saveState: Boolean = true,
    errorText: String? = null,
    errorAction: (@Composable () -> Unit)? = null,
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
            saveState = saveState,
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
        if (errorText != null) {
            if (errorAction == null) {
                FieldError(errorText)
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FieldError(errorText, Modifier.weight(1f))
                    errorAction()
                }
            }
        }
    }
}

/**
 * "Input/Password": the text field of [AppTextField] for a password. The text is shown as dots, and the
 * eye at the end of the box (24dp, `onSurfaceVariant`) shows it and hides it again: the open eye
 * ([AppIcons.Eye]) while the password is hidden, the crossed one ([AppIcons.EyeOff]) while it is shown, and
 * a screen reader says "Показать пароль" / "Скрыть пароль" for them. The box is the same 50dp tall, 12dp
 * rounded outlined box as that of [AppTextField] (the primary outline while focused, `error` with
 * [errorText]); the eye is a button of 48dp that fits into the box.
 *
 * What a password needs, and the other fields do not get:
 *
 * - the field keeps nothing in the saved state of the system (`saveState = false` of [AppBasicTextField]),
 *   so the password is not written to the `Bundle` that the system puts on disk. After the activity is
 *   recreated the text is whatever [value] is, the caret is at its end and the password is hidden again
 *   (whether it is shown is a plain `remember`, not `rememberSaveable`);
 * - the keyboard is the password one, with no auto-correction and no suggestions.
 *
 * Under the box there is one line of `labelSmall`: the [errorText] in `error` or, without an error, the
 * grey [hint] ("От 8 символов"). An error replaces the hint instead of standing beside it.
 *
 * @param value the password; the field is as secret as the caller keeps it, so do not log it or put it into
 * a saved state either
 * @param label the caption above the box
 * @param placeholder what the empty box says; empty by default, as in the design
 * @param hint the quiet line under the box, or `null` for none
 * @param errorText the reason the password is not accepted, or `null`; replaces [hint]
 * @param imeAction the action button of the keyboard: Done for the last field of a form, Next for the
 * password that is followed by another field
 * @param keyboardActions what the action button does, e.g. submits the form on Done
 */
@Composable
fun AppPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    hint: String? = null,
    errorText: String? = null,
    imeAction: ImeAction = ImeAction.Done,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val colors = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    // A plain remember: a password that was shown is hidden again after the activity is recreated.
    var visible by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FieldLabel(label)
        AppBasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { if (errorText != null) error(errorText) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Password,
                imeAction = imeAction,
            ),
            keyboardActions = keyboardActions,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            interactionSource = interactionSource,
            saveState = false,
            decorationBox = { innerTextField ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .fieldBox(focused, isError = errorText != null)
                        // The eye is 24dp, 13dp (12 and the outline) from the edge: its button of 48dp
                        // brings 12dp of that on its own side, and 1dp is left to add.
                        .padding(end = 1.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f).padding(start = 17.dp, top = 15.dp, bottom = 15.dp)) {
                        if (value.isEmpty()) Placeholder(placeholder)
                        innerTextField()
                    }
                    // Tapped quickly several times it just toggles that many times: no debounce.
                    AppIconButton(
                        icon = if (visible) AppIcons.EyeOff else AppIcons.Eye,
                        contentDescription = stringResource(
                            if (visible) Res.string.components_hide_password else Res.string.components_show_password,
                        ),
                        onClick = { visible = !visible },
                        repeatable = true,
                    )
                }
            },
        )
        when {
            errorText != null -> FieldError(errorText)
            hint != null -> FieldHint(hint)
        }
    }
}

/**
 * "Text field/Outlined" that opens a picker instead of taking input (sphere, dates): shows
 * [value], or [placeholder] while nothing is chosen. A burst of taps runs [onClick] once.
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
                .onceClickable(role = Role.DropdownList, onClick = onClick)
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

/**
 * "Text field/Tags": entered tags as input chips plus a field to add new ones.
 *
 * @param onAddTag the trimmed text that was committed (the keyboard's Done, or the plus)
 * @param onRemoveTag the tag whose chip was tapped
 * @param enabled `false` while the form that holds the field is busy (a save is on its way): the input takes no
 * text, the plus adds nothing and the chips cannot be removed. The look is unchanged, as with [AppTextField]
 */
@Composable
fun AppTagsField(
    label: String,
    tags: List<String>,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
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
                    tags.forEach { tag -> AppInputChip(tag, onRemove = { onRemoveTag(tag) }, enabled = enabled) }
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
                    enabled = enabled,
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
                    modifier = Modifier.clickable(enabled = enabled, onClick = commit),
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
private fun FieldError(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
}

@Composable
private fun FieldHint(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Placeholder(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = color, maxLines = 1)
}
