package org.matchlab.academic_net.components.interaction

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Makes the element take the touches that land on it, so they do not reach what is drawn under it.
 *
 * Compose hands a touch to the topmost element at that point that listens for pointer input; an
 * element that does not listen (a plain `Box`, a `Column`, a `Text`) is transparent to touches. The
 * old screen stays in the composition while the next one slides in, and under a loading
 * indicator or a message it is still there: a tap on the empty part of the new screen lands on a
 * button of the old one. This modifier makes the empty part solid.
 *
 * It does not consume the events. Children still get theirs first (a retry button inside keeps
 * working), and a scrolling parent still scrolls when the drag starts on this element.
 */
fun Modifier.blockPointerInput(): Modifier = pointerInput(Unit) {
    // Nothing to do with the events: listening for pointer input is what makes the element a target.
}
