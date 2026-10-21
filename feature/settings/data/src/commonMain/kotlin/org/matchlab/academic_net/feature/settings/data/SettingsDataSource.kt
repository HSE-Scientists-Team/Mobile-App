package org.matchlab.academic_net.feature.settings.data

/**
 * Where the settings come from: the Gateway ([GatewaySettingsDataSource]). A call throws when it fails (no network, a
 * server error); a `CancellationException` is never turned into anything.
 */
interface SettingsDataSource {
    /** "Открыт к сотрудничеству", or `null` when the user has no profile yet. */
    suspend fun getOpenToCollaboration(): Boolean?

    /**
     * Saves the switch and answers with the value the server has after the change.
     *
     * @throws org.matchlab.academic_net.feature.settings.domain.ProfileMissingException when there is no profile.
     */
    suspend fun setOpenToCollaboration(open: Boolean): Boolean
}
