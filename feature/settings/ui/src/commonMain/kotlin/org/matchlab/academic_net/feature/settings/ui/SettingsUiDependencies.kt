package org.matchlab.academic_net.feature.settings.ui

import org.matchlab.academic_net.feature.settings.domain.usecase.GetOpenToCollaborationUseCase
import org.matchlab.academic_net.feature.settings.domain.usecase.ObserveStatisticsUseCase
import org.matchlab.academic_net.feature.settings.domain.usecase.SetOpenToCollaborationUseCase
import org.matchlab.academic_net.feature.settings.domain.usecase.SetStatisticsUseCase

/**
 * What the screens of the settings feature need from the outside; the app creates it once and
 * hands it to [SettingsRoute]. Signing out is not here: it belongs to the auth feature, so the
 * app does it in the `onSignOut` callback of the route.
 *
 * @property getOpenToCollaboration reads the switch "Открыт к сотрудничеству" (a field of the profile, on the server)
 * @property setOpenToCollaboration saves it
 * @property observeStatistics watches the switch "Статистика использования" (a setting of the device, not on the server)
 * @property setStatistics saves it
 */
class SettingsUiDependencies(
    val getOpenToCollaboration: GetOpenToCollaborationUseCase,
    val setOpenToCollaboration: SetOpenToCollaborationUseCase,
    val observeStatistics: ObserveStatisticsUseCase,
    val setStatistics: SetStatisticsUseCase,
)
