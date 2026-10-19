package org.matchlab.academic_net.avatars

import kotlinx.coroutines.flow.SharedFlow
import org.matchlab.academic_net.gateway.MediaApi
import org.matchlab.academic_net.gateway.UserDirectory

/** The person has no picture (or it cannot be told): the avatar keeps the illustration. */
internal class NoAvatarException(userKey: String) : NoSuchElementException("No picture for $userKey")

/**
 * Where the pictures of people come from: the id of a picture is `avatar_file_id` of the public profile, read through
 * the session's [users] directory (which keeps profiles for a few minutes and asks once per person); the content is read
 * from the file storage with a temporary link of [MediaApi.downloadUrl]. Decoding, resizing and caching in memory are
 * the job of Coil (see [AvatarFetcher]).
 */
internal class AvatarDirectory(
    private val users: UserDirectory,
    private val media: MediaApi,
) {
    /** The ids of the people whose profile was saved: what is known of them is out of date. */
    val invalidated: SharedFlow<String> get() = users.invalidated

    /**
     * The bytes of the picture (JPEG or PNG) of the person with [userKey].
     *
     * @throws NoAvatarException the person does not exist for the viewer or has no picture
     * @throws org.matchlab.academic_net.common.result.RemoteFailure the profile or the file could not be read
     */
    suspend fun picture(userKey: String): ByteArray {
        val fileId = users.profile(userKey)?.details?.avatarFileId?.takeIf { it.isNotBlank() }
            ?: throw NoAvatarException(userKey)
        val link = media.downloadUrl(fileId)
        return media.fetch(link.downloadUrl)
    }
}
