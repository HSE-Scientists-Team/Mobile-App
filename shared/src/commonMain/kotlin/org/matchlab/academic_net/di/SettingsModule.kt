package org.matchlab.academic_net.di

import me.tatarka.inject.annotations.Provides
import org.matchlab.academic_net.feature.settings.data.SettingsDataSource
import org.matchlab.academic_net.feature.settings.data.SettingsRepositoryImpl
import org.matchlab.academic_net.feature.settings.domain.usecase.GetPrivacySettingsUseCase
import org.matchlab.academic_net.feature.settings.domain.usecase.SetPrivacyOptionUseCase
import org.matchlab.academic_net.feature.settings.domain.SettingsRepository
import org.matchlab.academic_net.feature.settings.ui.SettingsUiDependencies

/** The settings feature: its repository, use cases and what its screen receives. */
internal interface SettingsModule {
    @Provides
    @AppScope
    fun settingsRepository(source: SettingsDataSource): SettingsRepository = SettingsRepositoryImpl(source)

    @Provides
    fun settingsUiDependencies(repository: SettingsRepository): SettingsUiDependencies = SettingsUiDependencies(
        getPrivacySettings = GetPrivacySettingsUseCase(repository),
        setPrivacyOption = SetPrivacyOptionUseCase(repository),
    )
}
