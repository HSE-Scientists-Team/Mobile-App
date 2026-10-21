package org.matchlab.academic_net.avatars

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import okio.Buffer
import okio.FileSystem
import org.matchlab.academic_net.components.AvatarImages
import org.matchlab.academic_net.components.AvatarPicture

/** Reads the picture of an [AvatarPicture] from the [directory]; Coil decodes it for the size the avatar has. */
internal class AvatarFetcher(
    private val picture: AvatarPicture,
    private val directory: AvatarDirectory,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val bytes = directory.picture(picture.userKey)
        return SourceFetchResult(
            source = ImageSource(Buffer().apply { write(bytes) }, FileSystem.SYSTEM),
            mimeType = null,
            dataSource = DataSource.NETWORK,
        )
    }

    class Factory(private val directory: AvatarDirectory) : Fetcher.Factory<AvatarPicture> {
        override fun create(data: AvatarPicture, options: Options, imageLoader: ImageLoader): Fetcher =
            AvatarFetcher(data, directory)
    }
}

/** The key in the memory cache: the person and the revision, so a saved profile is not shown with the old picture. */
internal object AvatarPictureKeyer : Keyer<AvatarPicture> {
    override fun key(data: AvatarPicture, options: Options): String = "avatar:${data.userKey}:${data.revision}"
}

/**
 * The pictures for the avatars of the session: a Coil loader that reads [AvatarPicture]s from the [directory], and the
 * revision that goes up whenever somebody's profile is saved.
 */
@Composable
internal fun rememberAvatarImages(directory: AvatarDirectory): AvatarImages {
    val context = LocalPlatformContext.current
    val revision = remember(directory) { mutableIntStateOf(0) }
    LaunchedEffect(directory) { directory.invalidated.collect { revision.intValue++ } }
    val loader = remember(directory, context) {
        ImageLoader.Builder(context)
            .components {
                add(AvatarPictureKeyer)
                add(AvatarFetcher.Factory(directory))
            }
            .build()
    }
    return remember(loader) { AvatarImages(loader, revision) }
}
