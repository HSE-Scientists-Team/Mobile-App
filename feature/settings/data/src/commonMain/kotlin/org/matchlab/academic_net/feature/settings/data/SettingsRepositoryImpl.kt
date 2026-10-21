package org.matchlab.academic_net.feature.settings.data

import org.matchlab.academic_net.feature.settings.domain.SettingsRepository

/** Passes every request to the [SettingsDataSource]; the server is the only keeper of the settings. */
class SettingsRepositoryImpl(private val dataSource: SettingsDataSource) : SettingsRepository {
    override suspend fun getOpenToCollaboration(): Boolean? = dataSource.getOpenToCollaboration()

    override suspend fun setOpenToCollaboration(open: Boolean): Boolean = dataSource.setOpenToCollaboration(open)
}
