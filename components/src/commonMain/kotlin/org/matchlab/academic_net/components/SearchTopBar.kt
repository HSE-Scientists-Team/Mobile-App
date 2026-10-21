package org.matchlab.academic_net.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp

/**
 * The header of a search screen ("app-bar" of the project search): the back arrow, a [AppSearchBar]
 * that takes the free width and the [FiltersButton], all in one 62dp row. The white background
 * extends under the status bar.
 *
 * @param onSearch the "search" key of the keyboard
 * @param filtersActive some filters are applied: the filters button shows the dot
 * @param autoFocus puts the cursor into the search bar when the bar first appears (opened from
 *  the feed's search field)
 * @param windowInsets the insets the bar keeps clear of; pass `WindowInsets(0)` when something
 *  above it already handles the status bar
 */
@Composable
fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    onNavigateBack: () -> Unit,
    backContentDescription: String,
    filtersActive: Boolean,
    onFiltersClick: () -> Unit,
    filtersContentDescription: String,
    modifier: Modifier = Modifier,
    onSearch: (() -> Unit)? = null,
    autoFocus: Boolean = false,
    windowInsets: WindowInsets = WindowInsets.statusBars,
) {
    val focusRequester = remember { FocusRequester() }
    // Once per screen, not per composition: after a rotation the cursor and the keyboard stay as the
    // user left them.
    var focusRequested by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(autoFocus) {
        if (autoFocus && !focusRequested) {
            focusRequested = true
            focusRequester.requestFocus()
        }
    }
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(windowInsets)
            .fillMaxWidth()
            .padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconButton(
            icon = AppIcons.ArrowLeft,
            contentDescription = backContentDescription,
            onClick = onNavigateBack,
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
        AppSearchBar(
            query = query,
            onQueryChange = onQueryChange,
            placeholder = placeholder,
            modifier = Modifier.weight(1f).focusRequester(focusRequester),
            onSearch = onSearch,
        )
        FiltersButton(
            active = filtersActive,
            onClick = onFiltersClick,
            contentDescription = filtersContentDescription,
        )
    }
}
