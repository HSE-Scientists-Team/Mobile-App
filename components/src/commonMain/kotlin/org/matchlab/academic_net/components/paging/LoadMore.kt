package org.matchlab.academic_net.components.paging

import academicnet.components.generated.resources.Res
import academicnet.components.generated.resources.components_load_failed
import academicnet.components.generated.resources.components_loading
import academicnet.components.generated.resources.components_retry
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.components.AppButton
import org.matchlab.academic_net.components.AppButtonVariant
import org.matchlab.academic_net.components.InlineSpinner

/**
 * Where a list that loads the next page as it is scrolled stands at its end. The screen keeps one of
 * these next to the list; [LoadMoreFooter] shows it and [LoadMoreEffect] asks for the next page by it.
 */
enum class LoadMoreState {
    /** More is there to load and nothing is loading now: no footer. [LoadMoreEffect] asks when the end is near. */
    Idle,

    /** The next page is on its way: the footer says "Загрузка…" with a spinner. */
    Loading,

    /** The next page did not load: "Не удалось загрузить" and "Повторить"; nothing is asked for until the retry. */
    Failed,

    /** The last page is loaded: no footer, nothing more to ask for. */
    End,
}

/**
 * The end of a list that loads more as it is scrolled, as the design draws it ("Card/…" list of reviews,
 * frames 09b and 09f). Put it in the last `item {}` of the list; it draws what [state] says:
 *
 * - [LoadMoreState.Loading]: the spinner (20dp, 2dp stroke) and "Загрузка…" (`bodyMedium`, `onSurfaceVariant`),
 *   8dp apart and centered, 16dp above and below (52dp tall);
 * - [LoadMoreState.Failed]: "Не удалось загрузить" (`bodyMedium`, `onSurfaceVariant`) and the text button
 *   "Повторить" that runs [onRetry], 8dp apart and centered (48dp tall: the 40dp button of the design with
 *   4dp above and below, which is what the 48dp touch target of the button of Material 3 adds to it);
 * - [LoadMoreState.Idle] and [LoadMoreState.End]: nothing (the item is empty, but still counts as one more item
 *   of the list for [LoadMoreEffect]).
 *
 * A burst of taps on "Повторить" runs [onRetry] once, like every button (see [AppButton]).
 *
 * @param onRetry the user asks for the page that failed once more; the screen asks for it and moves [state]
 * to [LoadMoreState.Loading]
 */
@Composable
fun LoadMoreFooter(
    state: LoadMoreState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    when (state) {
        LoadMoreState.Loading -> Row(
            modifier = modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The spinner is drawn in a 20dp box of its own, as in the design.
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { InlineSpinner(color = colors.primary) }
            Text(
                text = stringResource(Res.string.components_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }
        LoadMoreState.Failed -> Row(
            // No padding of its own: the 4dp of the design above and below the 40dp button are the rest of
            // its 48dp touch target.
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Takes what the button leaves: on a narrow screen or with a large font the text wraps.
            Text(
                text = stringResource(Res.string.components_load_failed),
                modifier = Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            AppButton(
                text = stringResource(Res.string.components_retry),
                onClick = onRetry,
                variant = AppButtonVariant.Text,
            )
        }
        LoadMoreState.Idle, LoadMoreState.End -> Unit
    }
}

/**
 * Asks for the next page when the user is near the end of the list: calls [onLoadMore] when no more than
 * [threshold] items are left after the last visible one and [state] is [LoadMoreState.Idle]. With any other
 * state nothing is asked for: a page is already on its way, has failed (the user retries with the footer)
 * or there is nothing more.
 *
 * The check starts anew on every change of [state]. When a page has loaded and [state] is back to
 * [LoadMoreState.Idle], a list that is still short (the page did not fill the screen) is near its end
 * at once, and the next page is asked for without waiting for a scroll. The call is meant to move [state]
 * to [LoadMoreState.Loading], which ends the wait. While [state] stays [LoadMoreState.Idle] the call is made
 * again each time the end becomes near and each time the list changes its size near the end, so a call that
 * the screen had to ignore (a new search was running) is not lost; that is why [onLoadMore] must guard itself
 * against a repeat. It is not called on every frame of a scroll.
 *
 * The list has to be on the screen: the decision is made by what the list has measured last, and a list that
 * is not shown (it is below the visible part of a scrolled parent, or under another screen) keeps the layout of
 * the time it was shown.
 *
 * The footer of the list ([LoadMoreFooter]) and any header count as items of the list: a list of 20 reviews
 * with a summary above and the footer below has 22.
 *
 * @param listState the state of the `LazyColumn` that shows the list
 * @param state where the end of the list stands, the one that [LoadMoreFooter] shows
 * @param onLoadMore asks for the next page; a call while a page is already on its way does nothing
 * @param threshold how many items before the end the next page is asked for; a larger one loads earlier
 */
@Composable
fun LoadMoreEffect(
    listState: LazyListState,
    state: LoadMoreState,
    onLoadMore: () -> Unit,
    threshold: Int = 5,
) {
    val latestOnLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(listState, state, threshold) {
        if (state != LoadMoreState.Idle) return@LaunchedEffect
        snapshotFlow {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: NoItem
            ListEnd(isNearEnd(lastVisible, layout.totalItemsCount, threshold), layout.totalItemsCount)
        }.collect { end -> if (end.isNear) latestOnLoadMore() }
    }
}

/** What [LoadMoreEffect] watches: whether the end is near, and how long the list is (it grows when a page arrives). */
private data class ListEnd(val isNear: Boolean, val itemCount: Int)

/** The index of the last visible item of a list that shows none. */
private const val NoItem = -1

/**
 * Whether a list of [itemCount] items whose last visible item is [lastVisibleIndex] is near its end: no more
 * than [threshold] items are left after the last visible one. A list with no items has no end to be near.
 *
 * With [threshold] 5 and 20 items the end is near from the item with index 14 (5 items are left after it),
 * not from 13. A negative [threshold] counts as 0: the end is near only when the last item is visible.
 * [lastVisibleIndex] is -1 when no item is visible, which is near only for a list no longer than [threshold].
 */
internal fun isNearEnd(lastVisibleIndex: Int, itemCount: Int, threshold: Int): Boolean {
    if (itemCount <= 0) return false
    // In Long: the difference of two extreme Ints does not fit into an Int.
    val itemsLeft = itemCount.toLong() - 1 - lastVisibleIndex
    return itemsLeft <= threshold.coerceAtLeast(0)
}
