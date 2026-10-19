package org.matchlab.academic_net.feature.profile.ui.my

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.ImageFormat
import io.github.vinceglb.filekit.compressImage
import io.github.vinceglb.filekit.readBytes
import org.matchlab.academic_net.feature.profile.domain.UploadFile

/** The longest side of the photo that is uploaded: the avatar is shown at 80dp at most, so this is plenty. */
internal const val AvatarMaxSidePixels = 512

private const val AvatarJpegQuality = 85

/**
 * The photo [file] chosen in the gallery as a file ready to upload: a JPEG whose longer side is at most
 * [AvatarMaxSidePixels]. The library turns it upright by its EXIF orientation and shrinks it; the backend takes only
 * JPEG and PNG for an avatar.
 */
internal suspend fun avatarPhotoOf(file: PlatformFile): UploadFile {
    val jpeg = FileKit.compressImage(
        bytes = file.readBytes(),
        quality = AvatarJpegQuality,
        maxWidth = AvatarMaxSidePixels,
        maxHeight = AvatarMaxSidePixels,
        imageFormat = ImageFormat.JPEG,
    )
    return UploadFile(jpeg, contentType = "image/jpeg", fileName = "avatar.jpg")
}
