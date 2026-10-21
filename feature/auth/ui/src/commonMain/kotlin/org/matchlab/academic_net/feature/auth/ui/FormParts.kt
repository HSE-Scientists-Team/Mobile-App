package org.matchlab.academic_net.feature.auth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.matchlab.academic_net.components.AppButton
import org.matchlab.academic_net.components.AppButtonVariant

/**
 * The numbers of the sign-in, registration and confirmation screens in the design (frames 19–22b of
 * `docs/figma-handoff.md`, phase 2): what lies between what. A group of the form is a set of fields, a message and
 * its button, or a set of buttons; the groups of a form are [GroupGap] apart.
 */
internal object FormMetrics {
    /** The padding of the form around its groups. */
    val Inset: Dp = 16.dp

    /** Between the groups of the form. */
    val GroupGap: Dp = 24.dp

    /** Between the parts of a group of fields, and between the message and the button under it. */
    val PartGap: Dp = 16.dp

    /** Between the field of the link and its button. */
    val LinkGap: Dp = 12.dp

    /** Between the buttons of a group, and between a button and the line of text under it. */
    val ButtonGap: Dp = 8.dp
}

/**
 * The frame of the sign-in, registration and confirmation screens: [topBar], then the form under it, which
 * scrolls when it does not fit (the keyboard takes the space). The background is `surfaceContainerLow` as in the
 * design (the bar on it is white), and the inner padding is [FormMetrics.Inset] with [FormMetrics.GroupGap] between
 * the groups of the form ([content]); the groups are [FormGroup]s.
 *
 * The screen with the fields stands above the keyboard (`imePadding()`) and the form keeps clear of the
 * navigation bar.
 */
@Composable
internal fun AuthFormScreen(
    topBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .imePadding(),
    ) {
        topBar()
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(FormMetrics.Inset),
            verticalArrangement = Arrangement.spacedBy(FormMetrics.GroupGap),
            content = content,
        )
    }
}

/**
 * [AppButton] that is laid out 40dp tall, as the design draws it. Material 3 lays a button out 48dp tall (the
 * touch target) around the 40dp one it draws, which makes the gaps of a form 4dp wider on each side of a button
 * than the 8, 12 and 24 that the design has. Here the button is as tall as it looks, and the numbers of the
 * design apply as they are. What is lost is the 4dp of touch target above and below a button that was never
 * drawn; the buttons of the forms are as wide as the screen, so they are easy to hit all the same.
 *
 * Only for the buttons of the forms. The switch is for the button and not for the screen, as the other parts keep
 * their sizes (the eye of the password field is an icon button of 48dp, which puts the icon where the design has it).
 */
@Composable
internal fun FormButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: AppButtonVariant = AppButtonVariant.Filled,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        AppButton(text, onClick, modifier, variant, enabled, loading)
    }
}

/**
 * A group of the form: its parts, [spacing] apart ([FormMetrics]), as wide as the form.
 */
@Composable
internal fun FormGroup(spacing: Dp, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing), content = content)
}

/**
 * A line of `labelSmall` in `error` for a problem that has no field to stand under: what came of a button
 * ("Нет связи с сервером" under "Я подтвердил(а) почту"). A screen reader announces it when it appears.
 */
@Composable
internal fun FormErrorText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.error,
    )
}

/**
 * The keyboard of the field of an e-mail address: the one for e-mail, without auto-correction (an address is not a
 * word), and "Next" moves on to the next field.
 */
internal val EmailKeyboardOptions = KeyboardOptions(
    keyboardType = KeyboardType.Email,
    imeAction = ImeAction.Next,
    autoCorrectEnabled = false,
)
