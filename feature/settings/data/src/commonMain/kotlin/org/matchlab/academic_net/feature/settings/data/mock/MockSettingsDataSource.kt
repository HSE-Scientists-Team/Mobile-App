package org.matchlab.academic_net.feature.settings.data.mock

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet
import org.matchlab.academic_net.feature.settings.data.SettingsDataSource
import org.matchlab.academic_net.feature.settings.domain.PrivacyOption
import org.matchlab.academic_net.feature.settings.domain.PrivacySettings

/**
 * In-memory stand-in for the settings backend: answers after [latencyMillis], like a network call.
 *
 * It plays the server, so a change patches one field of the stored settings and the answer is the
 * whole state: a switch sent while another is in flight does not bring the other back. By
 * default both options are off: nothing is shown to others until the user allows it.
 */
class MockSettingsDataSource(
    private val latencyMillis: Long = DefaultLatencyMillis,
    initial: PrivacySettings = PrivacySettings(showParticipatedProjects = false, showContacts = false),
) : SettingsDataSource {
    private val settings = MutableStateFlow(initial)

    override suspend fun getPrivacySettings(): PrivacySettings {
        simulateNetwork()
        return settings.value
    }

    override suspend fun setPrivacyOption(option: PrivacyOption, enabled: Boolean): PrivacySettings {
        simulateNetwork()
        return settings.updateAndGet { it.with(option, enabled) }
    }

    private suspend fun simulateNetwork() {
        if (latencyMillis > 0) delay(latencyMillis)
    }

    private companion object {
        const val DefaultLatencyMillis = 400L
    }
}
