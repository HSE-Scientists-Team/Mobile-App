package org.matchlab.academic_net.feature.settings.domain

import kotlinx.coroutines.flow.Flow

/**
 * The switch "usage statistics" of this device: whether the app may send counts of the taps on projects and people to the
 * analytics (the id of the project or of the person and the place of the tap, tied to the account). It is a setting of the device, not of the account, and it is not on the server: the app
 * keeps it on the device and the settings feature only reads and changes it through this contract (the adapter is
 * made in the composition root, as the feature knows nothing of the analytics).
 */
interface StatisticsSettings {
    /**
     * Whether usage statistics are sent from this device; on until the person says otherwise, so a device that has
     * no stored choice yet reads `true`. Emits the current value first and then every change.
     */
    val enabled: Flow<Boolean>

    /** Saves [enabled] as the person's choice. Throws when the choice could not be saved. */
    suspend fun setEnabled(enabled: Boolean)
}
