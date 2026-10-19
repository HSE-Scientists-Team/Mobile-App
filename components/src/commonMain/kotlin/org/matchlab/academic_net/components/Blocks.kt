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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.matchlab.academic_net.theme.extendedColors

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

/**
 * [Avatar] of a person: the picture they uploaded when the app has it ([LocalAvatarImages]), otherwise the
 * illustration of [source]. The illustration is shown while the picture loads and when there is none or it fails.
 */
@Composable
fun Avatar(
    source: AvatarSource,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val illustration = painterResource(source.illustration)
    val images = LocalAvatarImages.current
    if (images == null) {
        Avatar(illustration, contentDescription, modifier, size)
        return
    }
    AsyncImage(
        model = AvatarPicture(source.key, images.revision),
        contentDescription = contentDescription,
        imageLoader = images.imageLoader,
        modifier = modifier.size(size).clip(CircleShape),
        placeholder = illustration,
        error = illustration,
        fallback = illustration,
        contentScale = ContentScale.Crop,
    )
}

/** [Avatar] for an image from resources: the illustrations of the catalog. */
@Composable
fun Avatar(
    image: DrawableResource,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    Avatar(painterResource(image), contentDescription, modifier, size)
}

/**
 * "Avatar" (`person=unknown`) of a person the app knows nothing about (no readable profile, no picture): a round
 * `surfaceContainerHigh` disc with the [AppIcons.User] icon in `onSurfaceVariant`, the icon half the size of the disc.
 * The design draws it at 64dp (`md`) and 40dp (`sm`).
 */
@Composable
fun UnknownAvatar(modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(colors.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(AppIcons.User, contentDescription = null, size = size / 2, tint = colors.onSurfaceVariant)
    }
}

/** How an [InfoBanner] speaks: a quiet note, or a problem that the user has to deal with. */
enum class InfoBannerTone {
    /** A note or a rule of the service: `primaryContainer`. */
    Info,

    /** Something went wrong and the user has to act, e.g. "Запись изменилась. Обновите экран": `errorContainer`. */
    Error,
}

/**
 * "Banner/Info": note inside a screen, e.g. a service rule. A 12dp rounded box with a 20dp [icon] and the text
 * in `bodySmall`, 12dp inside and between.
 *
 * With [InfoBannerTone.Error] ("Banner/Info (ошибка)") the box is `errorContainer` and the icon and the text are
 * `onErrorContainer`.
 *
 * @param icon the icon at the start. When `null`: the shield with a check for [InfoBannerTone.Info], the
 * crossed circle ([AppIcons.XCircle]) for [InfoBannerTone.Error]. A banner with a meaning of its own passes
 * its own, e.g. [AppIcons.Clock] for "Сеанс завершён. Войдите снова"
 * @param tone what the banner is about; it only decides the colors (and the default icon)
 */
@Composable
fun InfoBanner(
    text: String,
    modifier: Modifier = Modifier,
    icon: DrawableResource? = null,
    tone: InfoBannerTone = InfoBannerTone.Info,
) {
    val colors = MaterialTheme.colorScheme
    val isError = tone == InfoBannerTone.Error
    val contentColor = if (isError) colors.onErrorContainer else colors.onPrimaryContainer
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (isError) colors.errorContainer else colors.primaryContainer, MaterialTheme.shapes.medium)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AppIcon(
            icon = icon ?: if (isError) AppIcons.XCircle else AppIcons.ShieldCheck,
            contentDescription = null,
            size = 20.dp,
            tint = contentColor,
        )
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = contentColor,
        )
    }
}

/** What a moderator has decided about a work or a document, as [StatusBanner] tells it. */
enum class StatusBannerStatus {
    /** Not confirmed: there is no proof, or nobody has looked at it. Neutral colors, the crossed shield. */
    Unverified,

    /** Being checked (only its owner sees this). Warning colors, the clock. */
    Pending,

    /** Confirmed. Green, the shield with a check. */
    Verified,

    /** Turned down by the moderator, who may have left a comment. Error colors, the crossed shield. */
    Rejected,
}

/**
 * "Banner/Status": the verification status of a work or a document at the top of the form that edits it.
 * A 12dp rounded box, 12dp inside and between, with a 20dp icon, the [title] in `titleSmall` and under it
 * (2dp apart) the optional [text] in `bodySmall`. The colors and the icon follow [status]:
 *
 * | [status] | box / text and icon | icon |
 * |---|---|---|
 * | [Unverified][StatusBannerStatus.Unverified] | `surfaceContainer` / `onSurfaceVariant` | crossed shield |
 * | [Pending][StatusBannerStatus.Pending] | `warningContainer` / `onWarningContainer` | clock |
 * | [Verified][StatusBannerStatus.Verified] | `tertiaryContainer` / `onTertiaryContainer` | shield with a check |
 * | [Rejected][StatusBannerStatus.Rejected] | `errorContainer` / `onErrorContainer` | crossed shield |
 *
 * The banner does not know the words: the screen passes [title] ("Отклонено") and, for a rejected work, the
 * [text] ("Комментарий модератора: Нужны доказательства"). A screen reader reads them as one phrase.
 *
 * @param title the name of the status
 * @param text the comment under the title, e.g. of the moderator; `null` for none
 */
@Composable
fun StatusBanner(
    status: StatusBannerStatus,
    title: String,
    modifier: Modifier = Modifier,
    text: String? = null,
) {
    val style = status.style()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(style.container, MaterialTheme.shapes.medium)
            .padding(12.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AppIcon(style.icon, contentDescription = null, size = 20.dp, tint = style.content)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = style.content)
            if (text != null) {
                Text(text, style = MaterialTheme.typography.bodySmall, color = style.content)
            }
        }
    }
}

private class StatusBannerStyle(val container: Color, val content: Color, val icon: DrawableResource)

@Composable
private fun StatusBannerStatus.style(): StatusBannerStyle {
    val colors = MaterialTheme.colorScheme
    val extended = MaterialTheme.extendedColors
    return when (this) {
        StatusBannerStatus.Unverified ->
            StatusBannerStyle(colors.surfaceContainer, colors.onSurfaceVariant, AppIcons.ShieldX)
        StatusBannerStatus.Pending ->
            StatusBannerStyle(extended.warningContainer, extended.onWarningContainer, AppIcons.Clock)
        StatusBannerStatus.Verified ->
            StatusBannerStyle(colors.tertiaryContainer, colors.onTertiaryContainer, AppIcons.ShieldCheck)
        StatusBannerStatus.Rejected ->
            StatusBannerStyle(colors.errorContainer, colors.onErrorContainer, AppIcons.ShieldX)
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
