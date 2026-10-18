package org.matchlab.academic_net.feature.settings.domain.usecase

import org.matchlab.academic_net.feature.settings.domain.SettingsRepository
import org.matchlab.academic_net.utils.suspendRunCatching

/**
 * Loads "Открыт к сотрудничеству": `true` or `false`, or `null` when the user has no profile yet. A failure is a
 * failed [Result], cancellation is rethrown.
 */
class GetOpenToCollaborationUseCase(private val repository: SettingsRepository) {
    suspend operator fun invoke(): Result<Boolean?> = suspendRunCatching { repository.getOpenToCollaboration() }
}

/**
 * Switches "Открыт к сотрудничеству" and returns the value the server has after it. A failure is a failed [Result]
 * ([org.matchlab.academic_net.feature.settings.domain.ProfileMissingException] when there is no profile to save it in),
 * cancellation is rethrown.
 */
class SetOpenToCollaborationUseCase(private val repository: SettingsRepository) {
    suspend operator fun invoke(open: Boolean): Result<Boolean> =
        suspendRunCatching { repository.setOpenToCollaboration(open) }
}
