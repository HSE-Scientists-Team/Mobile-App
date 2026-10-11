package org.matchlab.academic_net.di

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import me.tatarka.inject.annotations.Provides
import org.matchlab.academic_net.feature.auth.data.AuthDataSource
import org.matchlab.academic_net.feature.auth.data.AuthRepositoryImpl
import org.matchlab.academic_net.feature.auth.data.SessionStorage
import org.matchlab.academic_net.feature.auth.domain.AuthRepository
import org.matchlab.academic_net.feature.auth.domain.usecase.GetLastChallengeUseCase
import org.matchlab.academic_net.feature.auth.domain.usecase.ObserveAuthStateUseCase
import org.matchlab.academic_net.feature.auth.domain.usecase.RequestEmailCodeUseCase
import org.matchlab.academic_net.feature.auth.domain.usecase.RestoreSessionUseCase
import org.matchlab.academic_net.feature.auth.domain.usecase.SignOutUseCase
import org.matchlab.academic_net.feature.auth.domain.usecase.VerifyEmailCodeUseCase
import org.matchlab.academic_net.feature.auth.ui.AuthUiDependencies
import org.matchlab.academic_net.securestore.SecureStore

/**
 * Registration and sign-in. The session lives in the [SecureStore] the platform gives to the
 * component: encrypted with the Android Keystore, in the iOS Keychain.
 */
internal interface AuthModule {
    @Provides
    fun sessionStorage(store: SecureStore): SessionStorage = SessionStorage(store)

    @Provides
    @AppScope
    fun authRepository(source: AuthDataSource, storage: SessionStorage): AuthRepository =
        AuthRepositoryImpl(source, storage, io = Dispatchers.IO)

    @Provides
    fun authUiDependencies(repository: AuthRepository): AuthUiDependencies = AuthUiDependencies(
        observeAuthState = ObserveAuthStateUseCase(repository),
        restoreSession = RestoreSessionUseCase(repository),
        requestCode = RequestEmailCodeUseCase(repository),
        verifyCode = VerifyEmailCodeUseCase(repository),
        getLastChallenge = GetLastChallengeUseCase(repository),
        signOut = SignOutUseCase(repository),
    )
}
