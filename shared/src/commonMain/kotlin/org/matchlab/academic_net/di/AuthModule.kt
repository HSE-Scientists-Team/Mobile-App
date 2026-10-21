package org.matchlab.academic_net.di

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import me.tatarka.inject.annotations.Provides
import org.matchlab.academic_net.feature.auth.data.AuthDataSource
import org.matchlab.academic_net.feature.auth.data.AuthRepositoryImpl
import org.matchlab.academic_net.feature.auth.data.GatewayAuthDataSource
import org.matchlab.academic_net.feature.auth.data.SessionStorage
import org.matchlab.academic_net.feature.auth.domain.AuthRepository
import org.matchlab.academic_net.feature.auth.domain.usecase.ConfirmEmailUseCase
import org.matchlab.academic_net.feature.auth.domain.usecase.GetPendingRegistrationUseCase
import org.matchlab.academic_net.feature.auth.domain.usecase.ObserveAuthStateUseCase
import org.matchlab.academic_net.feature.auth.domain.usecase.RegisterUseCase
import org.matchlab.academic_net.feature.auth.domain.usecase.ResendLetterUseCase
import org.matchlab.academic_net.feature.auth.domain.usecase.RestoreSessionUseCase
import org.matchlab.academic_net.feature.auth.domain.usecase.SignInPendingUseCase
import org.matchlab.academic_net.feature.auth.domain.usecase.SignInUseCase
import org.matchlab.academic_net.feature.auth.domain.usecase.SignOutUseCase
import org.matchlab.academic_net.feature.auth.ui.AuthUiDependencies
import org.matchlab.academic_net.gateway.Gateway
import org.matchlab.academic_net.securestore.SecureStore

/**
 * Registration and sign-in, for the whole process: the session outlives every screen and every session graph. It
 * lives in the [SecureStore] the platform gives to the component: encrypted with the Android Keystore, in the iOS
 * Keychain. The password is never part of it (see [SessionStorage]).
 */
internal interface AuthModule {
    @Provides
    fun sessionStorage(store: SecureStore): SessionStorage = SessionStorage(store)

    /** The server of the accounts: the Gateway. */
    @Provides
    @AppScope
    fun authDataSource(gateway: Gateway): AuthDataSource = GatewayAuthDataSource(gateway.account)

    @Provides
    @AppScope
    fun authRepository(source: AuthDataSource, storage: SessionStorage): AuthRepository =
        AuthRepositoryImpl(source, storage, io = Dispatchers.IO)

    @Provides
    @AppScope
    fun authUiDependencies(repository: AuthRepository): AuthUiDependencies = AuthUiDependencies(
        observeAuthState = ObserveAuthStateUseCase(repository),
        restoreSession = RestoreSessionUseCase(repository),
        signIn = SignInUseCase(repository),
        register = RegisterUseCase(repository),
        confirmEmail = ConfirmEmailUseCase(repository),
        signInPending = SignInPendingUseCase(repository),
        resendLetter = ResendLetterUseCase(repository),
        getPendingRegistration = GetPendingRegistrationUseCase(repository),
        signOut = SignOutUseCase(repository),
    )
}
