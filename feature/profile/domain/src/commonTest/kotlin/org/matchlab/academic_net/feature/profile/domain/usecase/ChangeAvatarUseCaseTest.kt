package org.matchlab.academic_net.feature.profile.domain.usecase

import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.feature.profile.domain.FakeMyProfileRepository
import org.matchlab.academic_net.feature.profile.domain.ProfileRequiredException
import org.matchlab.academic_net.feature.profile.domain.UploadFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ChangeAvatarUseCaseTest {
    private val repository = FakeMyProfileRepository()
    private val change = ChangeAvatarUseCase(repository)
    private val photo = UploadFile(byteArrayOf(1), "image/jpeg", "avatar.jpg")

    @Test
    fun thePhotoGoesToTheRepositoryAndTheResultIsASuccess() = runTest {
        val result = change(photo)

        assertTrue(result.isSuccess)
        assertSame(photo, repository.changedAvatars.single())
    }

    @Test
    fun aFailureIsTheResultAndNotThrown() = runTest {
        repository.onChangeAvatar = { throw ProfileRequiredException() }

        val failure = change(photo).exceptionOrNull()

        assertIs<ProfileRequiredException>(failure)
    }

    @Test
    fun theFileDoesNotPrintItsBytes() {
        assertEquals("UploadFile(avatar.jpg, 1 bytes, image/jpeg)", photo.toString())
    }
}
