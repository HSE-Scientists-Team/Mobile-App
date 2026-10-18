package org.matchlab.academic_net.feature.settings.ui

import org.matchlab.academic_net.feature.settings.domain.usecase.GetOpenToCollaborationUseCase
import org.matchlab.academic_net.feature.settings.domain.usecase.SetOpenToCollaborationUseCase

/**
 * What the screens of the settings feature need from the outside; the app creates it once and
 * hands it to [SettingsRoute]. Signing out is not here: it belongs to the auth feature, so the
 * app does it in the `onSignOut` callback of the route.
 */
class SettingsUiDependencies(
    val getOpenToCollaboration: GetOpenToCollaborationUseCase,
    val setOpenToCollaboration: SetOpenToCollaborationUseCase,
)
