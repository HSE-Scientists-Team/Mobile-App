package org.matchlab.academic_net.feature.settings.domain

/** The signed-in user's settings. The calls throw when they fail (no network); use cases wrap them. */
interface SettingsRepository {
    suspend fun getPrivacySettings(): PrivacySettings

    /**
     * Switches one [option] and returns the whole settings as the server has them now.
     *
     * Only the one field is sent (a patch), so two switches pressed one after another cannot
     * overwrite each other with a stale copy of the other field.
     */
    suspend fun setPrivacyOption(option: PrivacyOption, enabled: Boolean): PrivacySettings
}
