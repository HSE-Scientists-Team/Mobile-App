package org.matchlab.academic_net.components.interaction

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.semantics.Role
import kotlin.time.Duration

/**
 * Wraps [onClick] so that a burst of clicks runs it once: the first click goes through, the
 * following ones within [window] are dropped (see [ClickDebouncer]).
 *
 * Use it for every action that must not be repeated at once: opening a screen, sending, creating,
 * closing. The element keeps one debouncer for as long as it stays in the composition, and the
 * returned lambda is stable: it always calls the [onClick] of the latest composition. Do not use it
 * for what is meant to be tapped quickly, e.g. a stepper or a counter.
 *
 * For a plain clickable element prefer [onceClickable]; this is for components that hand the
 * click to something other than `Modifier.clickable` (a Material button, a custom indication).
 */
@Composable
fun rememberOnceClick(
    onClick: () -> Unit,
    window: Duration = ClickDebouncer.DefaultWindow,
): () -> Unit {
    val debouncer = remember(window) { ClickDebouncer(window) }
    val latest by rememberUpdatedState(onClick)
    return remember(debouncer) { { if (debouncer.tryAccept()) latest() } }
}

/**
 * `Modifier.clickable` whose click runs once per burst of taps: a second tap within
 * [ClickDebouncer.DefaultWindow] of the first is dropped. The accessibility click action goes
 * through the same guard.
 *
 * Meant for the elements that open a screen or start work. The state of the element (pressed,
 * ripple) still follows every touch; only the call of [onClick] is guarded.
 *
 * @param enabled when `false` the element is not clickable and the click is not counted
 * @param onClickLabel what a screen reader says about the action
 * @param role the role of the element for accessibility
 */
fun Modifier.onceClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onClick: () -> Unit,
): Modifier = composed {
    clickable(
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        onClick = rememberOnceClick(onClick),
    )
}
