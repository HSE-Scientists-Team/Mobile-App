package org.matchlab.academic_net.feature.settings.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.feature.settings.data.mock.MockSettingsDataSource
import org.matchlab.academic_net.feature.settings.domain.PrivacyOption
import org.matchlab.academic_net.feature.settings.domain.PrivacySettings

@OptIn(ExperimentalCoroutinesApi::class)
class MockSettingsDataSourceTest {
    @Test
    fun bothOptionsAreOffByDefault() = runTest {
        val settings = MockSettingsDataSource(latencyMillis = 0).getPrivacySettings()

        assertEquals(PrivacySettings(showParticipatedProjects = false, showContacts = false), settings)
    }

    @Test
    fun changePatchesOneFieldAndAnswersWithTheWholeState() = runTest {
        val source = MockSettingsDataSource(latencyMillis = 0)

        val afterFirst = source.setPrivacyOption(PrivacyOption.Contacts, true)
        val afterSecond = source.setPrivacyOption(PrivacyOption.ParticipatedProjects, true)

        assertEquals(PrivacySettings(showParticipatedProjects = false, showContacts = true), afterFirst)
        assertEquals(PrivacySettings(showParticipatedProjects = true, showContacts = true), afterSecond)
        assertEquals(afterSecond, source.getPrivacySettings())
    }

    @Test
    fun switchesInFlightTogetherDoNotLoseEachOther() = runTest {
        val source = MockSettingsDataSource(latencyMillis = 400)

        listOf(
            async { source.setPrivacyOption(PrivacyOption.Contacts, true) },
            async { source.setPrivacyOption(PrivacyOption.ParticipatedProjects, true) },
        ).awaitAll()

        assertEquals(PrivacySettings(true, true), source.getPrivacySettings())
    }

    @Test
    fun initialValueIsRespected() = runTest {
        val initial = PrivacySettings(showParticipatedProjects = true, showContacts = false)

        assertEquals(initial, MockSettingsDataSource(latencyMillis = 0, initial = initial).getPrivacySettings())
    }

    @Test
    fun answersAfterTheLatency() = runTest {
        MockSettingsDataSource(latencyMillis = 400).getPrivacySettings()

        assertEquals(400, currentTime)
    }

    @Test
    fun zeroLatencyDoesNotWait() = runTest {
        MockSettingsDataSource(latencyMillis = 0).getPrivacySettings()

        assertEquals(0, currentTime)
    }
}
