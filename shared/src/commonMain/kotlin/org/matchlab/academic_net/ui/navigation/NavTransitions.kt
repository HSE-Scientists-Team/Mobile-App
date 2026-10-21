package org.matchlab.academic_net.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.navigation.NavBackStackEntry

/**
 * The transition between two screens of either graph: a cross fade of [DurationMillis], the same
 * for going forward and back.
 *
 * Navigation Compose has its own default: on Android the same cross fade, but 700 ms long (on iOS a
 * 500 ms slide). That length mattered little while a second tap was harmless; now that the taps on
 * the screen that is going away are dropped ([navigateOnce]), that screen is on the display and
 * dead for the whole transition, and 700 ms feels like a freeze. 300 ms is what the tab switch of
 * [MainScaffold] takes (90 ms out, 210 ms in), so a screen opens as fast as a tab changes.
 *
 * The predictive back gesture keeps the default of the platform.
 */
internal object NavTransitions {
    const val DurationMillis = 300

    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        fadeIn(tween(DurationMillis))
    }

    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        fadeOut(tween(DurationMillis))
    }
}
