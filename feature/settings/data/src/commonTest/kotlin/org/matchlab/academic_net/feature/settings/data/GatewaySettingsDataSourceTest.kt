package org.matchlab.academic_net.feature.settings.data

import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.feature.settings.domain.ProfileMissingException
import org.matchlab.academic_net.gateway.ProfilesApi
import org.matchlab.academic_net.gateway.dto.UserProfileDto
import org.matchlab.academic_net.gateway.dto.UserProfileInputDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * The switch "Открыт к сотрудничеству" is a field of the profile: reading it, and writing it through the
 * read-modify-write of the profile so that nothing else in the profile changes.
 */
class GatewaySettingsDataSourceTest {
    /**
     * [ProfilesApi] over one stored profile, `null` for an account that has none (a 404 on the read, an empty profile to
     * start from on the update). An update lays the change over the stored `details`, as the real call does.
     */
    private class FakeProfilesApi(var stored: UserProfileDto? = null) : ProfilesApi {
        var readFailure: Throwable? = null
        var writeFailure: Throwable? = null

        /** What an update answers with instead of the stored profile with the change. */
        var answer: UserProfileDto? = null
        var reads = 0
        val writes = mutableListOf<UserProfileInputDto>()

        override suspend fun myProfile(): UserProfileDto {
            reads++
            readFailure?.let { throw it }
            return stored ?: throw RemoteFailure.Http(404)
        }

        override suspend fun updateMyProfile(change: (UserProfileInputDto) -> UserProfileInputDto): UserProfileDto {
            val current = stored ?: UserProfileDto()
            val changed = change(current.details)
            writeFailure?.let { throw it }
            writes += changed
            return answer ?: current.copy(details = changed).also { stored = it }
        }
    }

    private fun profile(name: String = "Арджун", open: Boolean? = false) = UserProfileDto(
        userId = "u1",
        details = UserProfileInputDto(
            displayName = name,
            university = "ВШЭ",
            about = "Люблю данные",
            skills = listOf("Kotlin"),
            projectTypes = listOf("unknown-value"),
            contactEmail = "a@b.c",
            openToCollaboration = open,
        ),
    )

    // ---- reading ----

    @Test
    fun theSwitchIsTheFieldOfTheProfile() = runTest {
        assertEquals(true, GatewaySettingsDataSource(FakeProfilesApi(profile(open = true))).getOpenToCollaboration())
        assertEquals(false, GatewaySettingsDataSource(FakeProfilesApi(profile(open = false))).getOpenToCollaboration())
    }

    @Test
    fun aFieldTheServerLeftOutIsOff() = runTest {
        assertEquals(false, GatewaySettingsDataSource(FakeProfilesApi(profile(open = null))).getOpenToCollaboration())
    }

    @Test
    fun anAccountWithoutAProfileHasNoSwitchWhetherThe404OrAnEmptyProfileIsHowTheServerSaysSo() = runTest {
        // API-GAP G19: the API does not say which of the two a new account gets, so both are "no profile yet".
        assertNull(GatewaySettingsDataSource(FakeProfilesApi(stored = null)).getOpenToCollaboration())
        assertNull(GatewaySettingsDataSource(FakeProfilesApi(UserProfileDto())).getOpenToCollaboration())
        assertNull(GatewaySettingsDataSource(FakeProfilesApi(profile(name = "   "))).getOpenToCollaboration())
    }

    @Test
    fun otherFailuresOfTheReadAreThrown() = runTest {
        for (status in listOf(401, 403, 409, 500, 503)) {
            val api = FakeProfilesApi(profile()).apply { readFailure = RemoteFailure.Http(status) }

            val failure = assertFailsWith<RemoteFailure.Http> { GatewaySettingsDataSource(api).getOpenToCollaboration() }

            assertEquals(status, failure.status)
        }
        val offline = FakeProfilesApi(profile()).apply { readFailure = RemoteFailure.NoConnection() }
        assertFailsWith<RemoteFailure.NoConnection> { GatewaySettingsDataSource(offline).getOpenToCollaboration() }
    }

    // ---- writing ----

    @Test
    fun savingChangesTheSwitchAndNothingElseOfTheProfile() = runTest {
        val api = FakeProfilesApi(profile(open = false))

        val saved = GatewaySettingsDataSource(api).setOpenToCollaboration(true)

        assertEquals(true, saved)
        assertEquals(profile(open = true).details, api.writes.single())
        // Neither the texts of the form nor the strings the app does not understand were touched.
        assertEquals(listOf("unknown-value"), api.writes.single().projectTypes)
        assertEquals("a@b.c", api.writes.single().contactEmail)
    }

    @Test
    fun savingOffWritesFalseAndNotTheMissingField() = runTest {
        val api = FakeProfilesApi(profile(open = true))

        val saved = GatewaySettingsDataSource(api).setOpenToCollaboration(false)

        assertEquals(false, saved)
        assertEquals(false, api.writes.single().openToCollaboration)
    }

    @Test
    fun theAnswerIsWhatTheServerHasNotWhatWasAskedFor() = runTest {
        val api = FakeProfilesApi(profile(open = false)).apply { answer = profile(open = false) }

        // The server did not take the change: the screen must show what it holds.
        assertEquals(false, GatewaySettingsDataSource(api).setOpenToCollaboration(true))
    }

    @Test
    fun anAnswerWithoutTheFieldIsOff() = runTest {
        val api = FakeProfilesApi(profile(open = false)).apply { answer = profile(open = null) }

        assertEquals(false, GatewaySettingsDataSource(api).setOpenToCollaboration(true))
    }

    @Test
    fun aProfileWithoutANameCannotHoldTheSwitchAndNothingIsWritten() = runTest {
        val noProfile = FakeProfilesApi(stored = null)
        assertFailsWith<ProfileMissingException> { GatewaySettingsDataSource(noProfile).setOpenToCollaboration(true) }
        assertEquals(emptyList(), noProfile.writes)

        val blankName = FakeProfilesApi(profile(name = " "))
        assertFailsWith<ProfileMissingException> { GatewaySettingsDataSource(blankName).setOpenToCollaboration(true) }
        assertEquals(emptyList(), blankName.writes)
    }

    @Test
    fun aFailedWriteIsThrown() = runTest {
        val api = FakeProfilesApi(profile()).apply { writeFailure = RemoteFailure.Http(409) }

        assertEquals(409, assertFailsWith<RemoteFailure.Http> { GatewaySettingsDataSource(api).setOpenToCollaboration(true) }.status)
    }

    // ---- the repository ----

    @Test
    fun theRepositoryPassesEveryRequestToTheSource() = runTest {
        val api = FakeProfilesApi(profile(open = false))
        val repository = SettingsRepositoryImpl(GatewaySettingsDataSource(api))

        assertEquals(false, repository.getOpenToCollaboration())
        assertEquals(true, repository.setOpenToCollaboration(true))
        assertEquals(true, repository.getOpenToCollaboration())
    }

    @Test
    fun theRepositoryDoesNotHideAFailureOrAMissingProfile() = runTest {
        val repository = SettingsRepositoryImpl(GatewaySettingsDataSource(FakeProfilesApi(stored = null)))

        assertNull(repository.getOpenToCollaboration())
        assertFailsWith<ProfileMissingException> { repository.setOpenToCollaboration(true) }
    }
}
