package org.matchlab.academic_net.feature.settings.domain

/** In-memory [SettingsRepository] that keeps the settings and fails on demand. */
class FakeSettingsRepository(var settings: PrivacySettings = PrivacySettings(false, false)) : SettingsRepository {
    var failure: Throwable? = null
    val changes = mutableListOf<Pair<PrivacyOption, Boolean>>()

    override suspend fun getPrivacySettings(): PrivacySettings {
        failure?.let { throw it }
        return settings
    }

    override suspend fun setPrivacyOption(option: PrivacyOption, enabled: Boolean): PrivacySettings {
        failure?.let { throw it }
        changes += option to enabled
        settings = settings.with(option, enabled)
        return settings
    }
}
