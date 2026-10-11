package org.matchlab.academic_net.feature.settings.data

import org.matchlab.academic_net.feature.settings.domain.PrivacyOption
import org.matchlab.academic_net.feature.settings.domain.PrivacySettings

/** Where the settings come from: the backend (not there yet) or the mock that plays it. */
interface SettingsDataSource {
    suspend fun getPrivacySettings(): PrivacySettings

    /** Patches one field on the server and answers with the whole settings after the change. */
    suspend fun setPrivacyOption(option: PrivacyOption, enabled: Boolean): PrivacySettings
}
