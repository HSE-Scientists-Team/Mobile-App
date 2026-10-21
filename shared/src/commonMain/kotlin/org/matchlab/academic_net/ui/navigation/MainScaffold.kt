package org.matchlab.academic_net.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.components.AppNavigationBar
import org.matchlab.academic_net.components.AppNavigationBarItem
import org.matchlab.academic_net.components.LocalNavigationBarOverlap
import org.matchlab.academic_net.components.NavigationBarOverlap

// Tab switch is a fade through: the old tab fades out, then the new one fades in, so two
// screens are never drawn on top of each other (Material timing for bottom navigation).
private const val TabFadeOutMillis = 90
private const val TabFadeInMillis = 210

/**
 * Root screen: the selected tab's [content] above the bottom navigation bar. The content gets
 * `selectTab` to switch to another tab (e.g. the feed's avatar opens the profile). Tabs in [badges]
 * show the dot.
 */
@Composable
fun MainScaffold(
    badges: Set<TopLevelDestination> = emptySet(),
    content: @Composable (destination: TopLevelDestination, selectTab: (TopLevelDestination) -> Unit) -> Unit,
) {
    var currentDestination by rememberSaveable { mutableStateOf(TopLevelDestination.Feed) }
    // Keeps each tab's saveable state (e.g. scroll position) while another tab is shown.
    val tabStateHolder = rememberSaveableStateHolder()
    val containerColor by animateColorAsState(
        targetValue = currentDestination.screenBackground(),
        animationSpec = tween(TabFadeOutMillis + TabFadeInMillis),
    )

    Scaffold(
        containerColor = containerColor,
        bottomBar = {
            AppNavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    AppNavigationBarItem(
                        selected = destination == currentDestination,
                        onClick = { currentDestination = destination },
                        icon = destination.icon,
                        label = stringResource(destination.label),
                        badge = destination in badges,
                    )
                }
            }
        },
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        AnimatedContent(
            targetState = currentDestination,
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    top = innerPadding.calculateTopPadding(),
                    end = innerPadding.calculateEndPadding(layoutDirection),
                )
                .consumeWindowInsets(innerPadding),
            transitionSpec = {
                fadeIn(tween(TabFadeInMillis, delayMillis = TabFadeOutMillis)) togetherWith
                    fadeOut(tween(TabFadeOutMillis))
            },
        ) { destination ->
            // The overlap is the tab's own, not the selected one's: a tab that fades out keeps its padding.
            val overlap = if (destination.scrollsUnderBar) NavigationBarOverlap else 0.dp
            Box(Modifier.fillMaxSize().padding(bottom = innerPadding.calculateBottomPadding() - overlap)) {
                tabStateHolder.SaveableStateProvider(destination.name) {
                    // A tab that scrolls under the bar reaches its middle; its list adds the same to its bottom padding.
                    CompositionLocalProvider(LocalNavigationBarOverlap provides overlap) {
                        content(destination) { currentDestination = it }
                    }
                }
            }
        }
    }
}

/** The feed is the tab whose cards scroll under the bottom bar, down to its middle. */
private val TopLevelDestination.scrollsUnderBar: Boolean get() = this == TopLevelDestination.Feed

/** Background of each tab's root screen in the design. */
@Composable
private fun TopLevelDestination.screenBackground(): Color = when (this) {
    TopLevelDestination.Chats -> MaterialTheme.colorScheme.surface
    else -> MaterialTheme.colorScheme.surfaceContainerLow
}
