package org.matchlab.academic_net.di

import me.tatarka.inject.annotations.Provides
import org.matchlab.academic_net.PreferenceStatisticsSettings
import org.matchlab.academic_net.feature.settings.data.SettingsDataSource
import org.matchlab.academic_net.feature.settings.data.SettingsRepositoryImpl
import org.matchlab.academic_net.feature.settings.domain.SettingsRepository
import org.matchlab.academic_net.feature.settings.domain.StatisticsSettings
import org.matchlab.academic_net.feature.settings.domain.usecase.GetOpenToCollaborationUseCase
import org.matchlab.academic_net.feature.settings.domain.usecase.ObserveStatisticsUseCase
import org.matchlab.academic_net.feature.settings.domain.usecase.SetOpenToCollaborationUseCase
import org.matchlab.academic_net.feature.settings.domain.usecase.SetStatisticsUseCase
import org.matchlab.academic_net.feature.settings.ui.SettingsUiDependencies

/**
 * The settings feature: its repository, use cases and what its screen receives. The switch "usage statistics" is not
 * on the server: it is the choice of the device, which the settings feature gets from the analytics through
 * [PreferenceStatisticsSettings], the one adapter connecting the two.
 */
internal interface SettingsModule {
    @Provides
    @SessionScope
    fun settingsRepository(source: SettingsDataSource): SettingsRepository = SettingsRepositoryImpl(source)

    @Provides
    fun statisticsSettings(adapter: PreferenceStatisticsSettings): StatisticsSettings = adapter

    @Provides
    fun settingsUiDependencies(
        repository: SettingsRepository,
        statistics: StatisticsSettings,
    ): SettingsUiDependencies = SettingsUiDependencies(
        getOpenToCollaboration = GetOpenToCollaborationUseCase(repository),
        setOpenToCollaboration = SetOpenToCollaborationUseCase(repository),
        observeStatistics = ObserveStatisticsUseCase(statistics),
        setStatistics = SetStatisticsUseCase(statistics),
    )
}
