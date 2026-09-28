package org.matchlab.academic_net.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** "Badge/Dot": 6dp primary dot in a 2dp surface ring (10dp overall). */
@Composable
fun AppBadgeDot(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .size(10.dp)
            .background(colors.surface, CircleShape)
            .padding(2.dp)
            .background(colors.primary, CircleShape),
    )
}

/** "Badge/Count": pill with a counter. */
@Composable
fun AppBadgeCount(count: Int, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .background(colors.primary, CircleShape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(count.toString(), style = MaterialTheme.typography.labelSmall, color = colors.onPrimary)
    }
}

/** "Avatar": round photo; the design uses it at 24, 32, 40, 48 and 64dp. */
@Composable
fun Avatar(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    Image(
        painter = painter,
        contentDescription = contentDescription,
        modifier = modifier.size(size).clip(CircleShape),
        contentScale = ContentScale.Crop,
    )
}

/** [Avatar] for an image from resources, as UI models carry it. */
@Composable
fun Avatar(
    image: DrawableResource,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    Avatar(painterResource(image), contentDescription, modifier, size)
}

/** "Banner/Info": note inside a screen, e.g. a service rule. */
@Composable
fun InfoBanner(
    text: String,
    modifier: Modifier = Modifier,
    icon: DrawableResource = AppIcons.ShieldCheck,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.primaryContainer, MaterialTheme.shapes.medium)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AppIcon(icon, contentDescription = null, size = 20.dp, tint = colors.onPrimaryContainer)
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onPrimaryContainer,
        )
    }
}

/** "Block/Text": section title with body text under it. */
@Composable
fun TextBlock(title: String, text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BlockTitle(title)
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "Block/Tags": section title with a wrapping set of tags. */
@Composable
fun TagsBlock(title: String, tags: List<String>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BlockTitle(title)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tags.forEach { AppTagChip(it) }
        }
    }
}

@Composable
private fun BlockTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
}
