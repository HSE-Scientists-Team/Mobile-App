package org.matchlab.academic_net.feature.settings.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.feature.settings.data.mock.MockSettingsDataSource
import org.matchlab.academic_net.feature.settings.domain.PrivacyOption
import org.matchlab.academic_net.feature.settings.domain.PrivacySettings

class SettingsRepositoryImplTest {
    private val repository = SettingsRepositoryImpl(MockSettingsDataSource(latencyMillis = 0))

    @Test
    fun readsTheServersSettings() = runTest {
        assertEquals(PrivacySettings(false, false), repository.getPrivacySettings())
    }

    @Test
    fun changeIsKeptByTheServer() = runTest {
        val changed = repository.setPrivacyOption(PrivacyOption.ParticipatedProjects, true)

        assertEquals(PrivacySettings(true, false), changed)
        assertEquals(changed, repository.getPrivacySettings())
    }

    @Test
    fun sourceFailureIsPassedOn() = runTest {
        val failing = SettingsRepositoryImpl(object : SettingsDataSource {
            override suspend fun getPrivacySettings(): PrivacySettings = error("no network")

            override suspend fun setPrivacyOption(option: PrivacyOption, enabled: Boolean): PrivacySettings =
                error("no network")
        })

        assertFailsWith<IllegalStateException> { failing.getPrivacySettings() }
        assertFailsWith<IllegalStateException> { failing.setPrivacyOption(PrivacyOption.Contacts, true) }
    }
}
