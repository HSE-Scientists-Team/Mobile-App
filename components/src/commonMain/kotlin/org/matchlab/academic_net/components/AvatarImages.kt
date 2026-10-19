package org.matchlab.academic_net.components

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf
import coil3.ImageLoader

/**
 * What Coil gets as the model of the picture of a person: the person's [userKey] (the user id) and a [revision] that
 * changes when the picture may have changed, so the old one is not taken from the memory cache.
 */
data class AvatarPicture(val userKey: String, val revision: Int)

/**
 * The pictures that people uploaded, as [Avatar] asks for them. The components do not know where they come from: the app
 * gives them an [imageLoader] that can read an [AvatarPicture] (a Coil loader with a fetcher of the app) through
 * [LocalAvatarImages]. Without one, or for a person with no picture, [Avatar] draws the illustration.
 */
@Stable
class AvatarImages(
    val imageLoader: ImageLoader,
    private val revisionState: State<Int>,
) {
    /** Changes whenever the picture of somebody may have changed (a profile was saved), so the avatars on the screen ask again. */
    val revision: Int get() = revisionState.value
}

/** The pictures of the app; `null` where nothing provides them (previews, the catalog): the illustrations are shown. */
val LocalAvatarImages = staticCompositionLocalOf<AvatarImages?> { null }
