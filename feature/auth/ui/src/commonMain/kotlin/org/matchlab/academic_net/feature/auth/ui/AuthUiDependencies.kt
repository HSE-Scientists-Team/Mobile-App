package org.matchlab.academic_net.feature.auth.ui

import org.matchlab.academic_net.feature.auth.domain.usecase.GetLastChallengeUseCase
import org.matchlab.academic_net.feature.auth.domain.usecase.ObserveAuthStateUseCase
import org.matchlab.academic_net.feature.auth.domain.usecase.RequestEmailCodeUseCase
import org.matchlab.academic_net.feature.auth.domain.usecase.RestoreSessionUseCase
import org.matchlab.academic_net.feature.auth.domain.usecase.SignOutUseCase
import org.matchlab.academic_net.feature.auth.domain.usecase.VerifyEmailCodeUseCase

/**
 * What the screens of the auth feature need from the outside; the app creates it once and hands
 * it to [SignInRoute] and [VerifyEmailRoute].
 *
 * The screens of this module use only [requestCode], [verifyCode] and [getLastChallenge]. The
 * rest is for the app's root, which decides what to show by [observeAuthState], restores the
 * session at start ([restoreSession]) and ends it on "Выйти из аккаунта" ([signOut]); they are
 * here so that the root does not need a second set of dependencies.
 */
class AuthUiDependencies(
    val observeAuthState: ObserveAuthStateUseCase,
    val restoreSession: RestoreSessionUseCase,
    val requestCode: RequestEmailCodeUseCase,
    val verifyCode: VerifyEmailCodeUseCase,
    val getLastChallenge: GetLastChallengeUseCase,
    val signOut: SignOutUseCase,
)
