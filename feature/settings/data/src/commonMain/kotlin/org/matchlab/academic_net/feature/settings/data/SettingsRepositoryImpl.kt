package org.matchlab.academic_net.feature.settings.data

import org.matchlab.academic_net.feature.settings.domain.PrivacyOption
import org.matchlab.academic_net.feature.settings.domain.PrivacySettings
import org.matchlab.academic_net.feature.settings.domain.SettingsRepository

/** Passes every request to the [SettingsDataSource]; the server is the only keeper of the settings. */
class SettingsRepositoryImpl(private val dataSource: SettingsDataSource) : SettingsRepository {
    override suspend fun getPrivacySettings(): PrivacySettings = dataSource.getPrivacySettings()

    override suspend fun setPrivacyOption(option: PrivacyOption, enabled: Boolean): PrivacySettings =
        dataSource.setPrivacyOption(option, enabled)
}
