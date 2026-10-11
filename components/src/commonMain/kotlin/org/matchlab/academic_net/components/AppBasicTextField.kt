package org.matchlab.academic_net.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Unstyled text input over a plain [String] that keeps the caret and the selection across
 * configuration changes (rotation, locale, dark theme) and process death.
 *
 * `BasicTextField(value: String)` keeps its selection in a plain `remember`, so after the activity
 * is recreated the text survives (it lives in the caller's state) but the caret jumps to the start.
 * This wrapper holds a [TextFieldValue] in `rememberSaveable(stateSaver = TextFieldValue.Saver)` and
 * synchronises it with [value]:
 *
 * - while the stored text equals [value], the stored value (text, caret, selection, IME composition)
 *   is shown as is;
 * - when [value] differs from the stored text (the caller cleared, trimmed or replaced the text, or
 *   the state was restored from a different source), [value] wins and the caret goes to the end.
 *
 * The public contract is the same as for `BasicTextField(value: String)`: [onValueChange] receives the
 * new text and is called only when the text changes (not for caret-only moves).
 *
 * All the styling (outline, placeholder, padding) is up to the caller via [decorationBox]; use this
 * instead of `BasicTextField(value: String)` for every text input in the app.
 *
 * @param value the text shown in the field.
 * @param onValueChange called with the new text when the user edits it.
 * @param decorationBox draws the field around [innerTextField]; must call it exactly once.
 */
@Composable
fun AppBasicTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    textStyle: TextStyle = TextStyle.Default,
    cursorBrush: Brush = SolidColor(Color.Black),
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    interactionSource: MutableInteractionSource? = null,
    decorationBox: @Composable (innerTextField: @Composable () -> Unit) -> Unit =
        @Composable { innerTextField -> innerTextField() },
) {
    var stored by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }
    // Derived, not written back during composition: while the texts differ, the caller's text wins.
    val shown = if (stored.text == value) stored else TextFieldValue(text = value, selection = TextRange(value.length))
    BasicTextField(
        value = shown,
        onValueChange = { new ->
            val textChanged = new.text != shown.text
            stored = new
            if (textChanged) onValueChange(new.text)
        },
        modifier = modifier,
        enabled = enabled,
        textStyle = textStyle,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        interactionSource = interactionSource,
        cursorBrush = cursorBrush,
        decorationBox = decorationBox,
    )
}
