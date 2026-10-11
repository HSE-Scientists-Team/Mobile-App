package org.matchlab.academic_net.feature.settings.domain.usecase

import org.matchlab.academic_net.feature.settings.domain.PrivacyOption
import org.matchlab.academic_net.feature.settings.domain.PrivacySettings
import org.matchlab.academic_net.feature.settings.domain.SettingsRepository
import org.matchlab.academic_net.utils.suspendRunCatching

/** Loads the privacy settings; a network failure is a failed [Result], cancellation is rethrown. */
class GetPrivacySettingsUseCase(private val repository: SettingsRepository) {
    suspend operator fun invoke(): Result<PrivacySettings> =
        suspendRunCatching { repository.getPrivacySettings() }
}

/**
 * Switches one privacy option and returns the settings after the change; a network failure is a
 * failed [Result], cancellation is rethrown.
 */
class SetPrivacyOptionUseCase(private val repository: SettingsRepository) {
    suspend operator fun invoke(option: PrivacyOption, enabled: Boolean): Result<PrivacySettings> =
        suspendRunCatching { repository.setPrivacyOption(option, enabled) }
}
