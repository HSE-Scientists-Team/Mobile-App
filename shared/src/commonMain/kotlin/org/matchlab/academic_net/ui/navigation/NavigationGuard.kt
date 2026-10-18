package org.matchlab.academic_net.ui.navigation

import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder

/**
 * Whether the screen of the back stack entry [fromId] is the one the user is on, given the id of
 * the entry on top of the back stack ([currentId], `null` for an empty back stack).
 *
 * This is the rule behind [navigateOnce], [navigateSingleTop] and [popBackStackOnce]: a navigation
 * that a screen asks for is carried out only while that screen is the current one.
 *
 * - The first navigation of a burst of taps makes another entry current, so the later taps on the
 *   old screen, which is still on the display while the next one slides in, find that they are
 *   not from the current screen and are dropped: N taps open one screen, and N taps on a back
 *   arrow leave one.
 * - A request of the screen the user is on is never dropped, whenever it comes: an event of a form
 *   that was saved while the user was on another screen is delivered when the form is composed
 *   again, and one that arrives while the app is paused is delivered as well.
 * - A request of a screen the user has already left is dropped: a save that finishes after the
 *   cross of the form was tapped must not close one more screen.
 * - An empty back stack has no current screen, so nothing is carried out.
 *
 * Why not the lifecycle state of the top entry (only `RESUMED` may navigate)? Navigation Compose
 * keeps both entries of a transition, the one leaving and the one entering, below `RESUMED` until
 * the animation ends, and never lifts an entry above the state of its host. A guard on the state
 * therefore also dropped what must not be lost (an event of a screen that has just become current
 * again, composed for the first time with its flag already set; an event that arrives while the host
 * is paused), and nothing retried it: the form stayed open with a dead "Сохранить".
 */
internal fun isCurrentEntry(fromId: String, currentId: String?): Boolean =
    currentId != null && currentId == fromId

/**
 * Whether the screen of the entry [fromId] may be popped: it is the current one ([isCurrentEntry])
 * and there is a screen below it ([hasEntryBelow]). The root has nothing to go back to, and popping
 * it would leave an empty window.
 */
internal fun mayPopEntry(fromId: String, currentId: String?, hasEntryBelow: Boolean): Boolean =
    hasEntryBelow && isCurrentEntry(fromId, currentId)

/** `true` while [entry] is the entry on top of the back stack: its screen is the one the user is on. */
internal fun NavController.isCurrent(entry: NavBackStackEntry): Boolean =
    isCurrentEntry(entry.id, currentBackStackEntry?.id)

/**
 * Navigates to [route] on behalf of the screen of [from] and returns whether it did; see
 * [isCurrentEntry] for when it does not.
 *
 * Every navigation of the app goes through here, not through `navigate`; [from] is the entry the
 * screen was composed for, which `composable(...) { entry -> ... }` hands over. [builder] is
 * `navigate`'s own. Use [navigateSingleTop] for a route without arguments that must never be put on
 * top of itself; a route with an argument (a profile, a project) is not single-top, because a
 * profile opened from a profile is a new screen.
 */
internal fun NavController.navigateOnce(
    from: NavBackStackEntry,
    route: String,
    builder: NavOptionsBuilder.() -> Unit = {},
): Boolean {
    if (!isCurrent(from)) return false
    navigate(route, builder)
    return true
}

/** [navigateOnce] to a route without arguments, which is never stacked on top of itself. */
internal fun NavController.navigateSingleTop(from: NavBackStackEntry, route: String): Boolean =
    navigateOnce(from, route) { launchSingleTop = true }

/**
 * Pops the screen of [from] and returns whether it did; see [mayPopEntry] for when it does not.
 *
 * Two taps on a back arrow leave one screen, not two, and the root is never popped.
 */
internal fun NavController.popBackStackOnce(from: NavBackStackEntry): Boolean {
    if (!mayPopEntry(from.id, currentBackStackEntry?.id, hasEntryBelow = previousBackStackEntry != null)) {
        return false
    }
    return popBackStack()
}
