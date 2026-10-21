package org.matchlab.academic_net

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import me.tatarka.inject.annotations.Inject
import org.matchlab.academic_net.analytics.StatisticsPreference
import org.matchlab.academic_net.feature.settings.domain.StatisticsSettings

/**
 * Gives the settings feature the switch "usage statistics" of this device, from the [StatisticsPreference] of the
 * analytics. The settings feature does not know the analytics: this adapter is the only place that connects them.
 *
 * A device where nobody has chosen yet (`null` in the preference) reads as "on": that is the default of the setting,
 * and the one the app applies while somebody is signed in (see [AnalyticsController]); it is not written to the store
 * until the person switches the setting themselves.
 */
@Inject
internal class PreferenceStatisticsSettings(
    private val preference: StatisticsPreference,
) : StatisticsSettings {
    override val enabled: Flow<Boolean> = preference.enabled.map { it ?: true }.distinctUntilChanged()

    override suspend fun setEnabled(enabled: Boolean) = preference.set(enabled)
}
