package org.matchlab.academic_net.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.matchlab.academic_net.components.interaction.blockPointerInput

/**
 * A list that is being loaded: the progress indicator in the middle of the free space.
 *
 * The space takes the touches ([blockPointerInput]): while a screen slides in with its content
 * still loading, a tap on the empty space must not land on the screen that is going away.
 */
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().blockPointerInput(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

/**
 * A screen or a list with nothing to show: an empty result, an error. The [title] and the optional
 * explaining [text] are centered in the free space; with [actionText] and [onAction] an outlined
 * button below offers the way out ("Повторить", "Сбросить фильтры").
 *
 * The space takes the touches ([blockPointerInput]), like [LoadingState]; the button inside works
 * as usual.
 */
@Composable
fun MessageState(
    title: String,
    modifier: Modifier = Modifier,
    text: String? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxSize().blockPointerInput().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (text != null) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (actionText != null && onAction != null) {
            AppButton(
                text = actionText,
                onClick = onAction,
                modifier = Modifier.padding(top = 4.dp),
                variant = AppButtonVariant.Outlined,
            )
        }
    }
}
