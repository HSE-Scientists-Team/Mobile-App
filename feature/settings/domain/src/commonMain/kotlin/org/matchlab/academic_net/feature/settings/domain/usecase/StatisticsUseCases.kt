package org.matchlab.academic_net.feature.settings.domain.usecase

import kotlinx.coroutines.flow.Flow
import org.matchlab.academic_net.feature.settings.domain.StatisticsSettings
import org.matchlab.academic_net.utils.suspendRunCatching

/**
 * Watches the switch "usage statistics" of this device: the current value first, then every change; `true` until the
 * person has switched it off. The flow does not fail on its own account (the value is local).
 */
class ObserveStatisticsUseCase(private val settings: StatisticsSettings) {
    operator fun invoke(): Flow<Boolean> = settings.enabled
}

/**
 * Switches "usage statistics" of this device on or off. A failure to save the choice is a failed [Result] (the screen
 * puts the switch back), cancellation is rethrown.
 */
class SetStatisticsUseCase(private val settings: StatisticsSettings) {
    suspend operator fun invoke(enabled: Boolean): Result<Unit> = suspendRunCatching { settings.setEnabled(enabled) }
}
