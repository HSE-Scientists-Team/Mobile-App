package org.matchlab.academic_net.avatars

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.gateway.MediaApi
import org.matchlab.academic_net.gateway.UserDirectory
import org.matchlab.academic_net.gateway.dto.CreateMediaUploadDto
import org.matchlab.academic_net.gateway.dto.MediaDownloadDto
import org.matchlab.academic_net.gateway.dto.MediaFileDto
import org.matchlab.academic_net.gateway.dto.MediaUploadDto
import org.matchlab.academic_net.gateway.dto.UserProfileDto
import org.matchlab.academic_net.gateway.dto.UserProfileInputDto
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private class ScriptedMedia : MediaApi {
    val calls = mutableListOf<String>()
    var failure: Throwable? = null

    override suspend fun createUpload(input: CreateMediaUploadDto): MediaUploadDto = error("not expected")

    override suspend fun putContent(upload: MediaUploadDto, bytes: ByteArray) = error("not expected")

    override suspend fun complete(fileId: String): MediaFileDto = error("not expected")

    override suspend fun downloadUrl(fileId: String): MediaDownloadDto {
        calls += "downloadUrl $fileId"
        failure?.let { throw it }
        return MediaDownloadDto("https://storage.example/$fileId", 1)
    }

    override suspend fun fetch(url: String): ByteArray {
        calls += "fetch $url"
        return byteArrayOf(9, 8, 7)
    }

    override suspend fun delete(fileId: String) = error("not expected")
}

@OptIn(ExperimentalCoroutinesApi::class)
class AvatarDirectoryTest {
    private val media = ScriptedMedia()
    private val profiles = mutableMapOf<String, UserProfileDto>()
    private var profileReads = 0

    private fun TestScope.directory(): Pair<AvatarDirectory, UserDirectory> {
        val users = UserDirectory(
            fetch = { id ->
                profileReads++
                profiles[id] ?: throw RemoteFailure.Http(404)
            },
            scope = backgroundScope,
        )
        return AvatarDirectory(users, media) to users
    }

    private fun profile(id: String, avatar: String?) =
        UserProfileDto(userId = id, details = UserProfileInputDto(displayName = "Имя", avatarFileId = avatar))

    @Test
    fun thePictureIsReadByTheFileIdOfTheProfileThroughATemporaryLink() = runTest {
        profiles["u-1"] = profile("u-1", "file-1")

        val bytes = directory().first.picture("u-1")

        assertContentEquals(byteArrayOf(9, 8, 7), bytes)
        assertEquals(listOf("downloadUrl file-1", "fetch https://storage.example/file-1"), media.calls)
    }

    @Test
    fun aPersonWithoutAPictureOrWithoutAProfileHasNoAvatar() = runTest {
        profiles["u-1"] = profile("u-1", null)
        profiles["u-2"] = profile("u-2", "  ")
        val avatars = directory().first

        for (id in listOf("u-1", "u-2", "u-404")) {
            assertFailsWith<NoAvatarException>(id) { avatars.picture(id) }
        }
        assertEquals(emptyList(), media.calls, "no file, no request to the storage")
    }

    @Test
    fun aFailureOfTheStorageIsThrownAsItIs() = runTest {
        profiles["u-1"] = profile("u-1", "file-1")
        media.failure = RemoteFailure.Http(503)

        assertEquals(503, assertFailsWith<RemoteFailure.Http> { directory().first.picture("u-1") }.status)
    }

    @Test
    fun theProfileIsAskedOnceForTwoPicturesOfTheSamePerson() = runTest {
        profiles["u-1"] = profile("u-1", "file-1")
        val avatars = directory().first

        avatars.picture("u-1")
        avatars.picture("u-1")

        assertEquals(1, profileReads)
    }

    @Test
    fun aSavedProfileIsAnnouncedAsInvalidatedAndIsReadAgain() = runTest {
        profiles["u-1"] = profile("u-1", "file-1")
        val (avatars, users) = directory()
        val seen = mutableListOf<String>()
        backgroundScope.launch { avatars.invalidated.collect { seen += it } }
        runCurrent()
        avatars.picture("u-1")

        users.invalidate("u-1")
        profiles["u-1"] = profile("u-1", "file-2")
        runCurrent()
        avatars.picture("u-1")

        assertEquals(listOf("u-1"), seen)
        assertEquals(2, profileReads)
        assertEquals("downloadUrl file-2", media.calls.filter { it.startsWith("downloadUrl") }.last())
    }
}
