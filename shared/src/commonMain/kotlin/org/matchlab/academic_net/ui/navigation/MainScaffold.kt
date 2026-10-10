package org.matchlab.academic_net.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.components.AppNavigationBar
import org.matchlab.academic_net.components.AppNavigationBarItem

// Tab switch is a fade through: the old tab fades out, then the new one fades in, so two
// screens are never drawn on top of each other (Material timing for bottom navigation).
private const val TabFadeOutMillis = 90
private const val TabFadeInMillis = 210

/** Root screen: the selected tab's [content] above the bottom navigation bar. */
@Composable
fun MainScaffold(content: @Composable (TopLevelDestination) -> Unit) {
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
                    )
                }
            }
        },
    ) { innerPadding ->
        AnimatedContent(
            targetState = currentDestination,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            transitionSpec = {
                fadeIn(tween(TabFadeInMillis, delayMillis = TabFadeOutMillis)) togetherWith
                    fadeOut(tween(TabFadeOutMillis))
            },
        ) { destination ->
            tabStateHolder.SaveableStateProvider(destination.name) {
                content(destination)
            }
        }
    }
}

/** Background of each tab's root screen in the design. */
@Composable
private fun TopLevelDestination.screenBackground(): Color = when (this) {
    TopLevelDestination.Chats -> MaterialTheme.colorScheme.surface
    else -> MaterialTheme.colorScheme.surfaceContainerLow
}

// Stand-in until the tab screens are implemented.
@Composable
internal fun TabPlaceholder(destination: TopLevelDestination) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(destination.label),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
