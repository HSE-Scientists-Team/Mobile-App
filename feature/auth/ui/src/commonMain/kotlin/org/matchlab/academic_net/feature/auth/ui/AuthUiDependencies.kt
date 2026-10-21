package org.matchlab.academic_net.feature.auth.ui

import org.matchlab.academic_net.feature.auth.domain.usecase.ConfirmEmailUseCase
import org.matchlab.academic_net.feature.auth.domain.usecase.GetPendingRegistrationUseCase
import org.matchlab.academic_net.feature.auth.domain.usecase.ObserveAuthStateUseCase
import org.matchlab.academic_net.feature.auth.domain.usecase.RegisterUseCase
import org.matchlab.academic_net.feature.auth.domain.usecase.ResendLetterUseCase
import org.matchlab.academic_net.feature.auth.domain.usecase.RestoreSessionUseCase
import org.matchlab.academic_net.feature.auth.domain.usecase.SignInPendingUseCase
import org.matchlab.academic_net.feature.auth.domain.usecase.SignInUseCase
import org.matchlab.academic_net.feature.auth.domain.usecase.SignOutUseCase

/**
 * What the screens of the auth feature need from the outside; the app creates it once and hands it to
 * [SignInRoute], [RegisterRoute] and [ConfirmEmailRoute].
 *
 * The screens of this module use [signIn], [register], [confirmEmail], [signInPending], [resendLetter] and
 * [getPendingRegistration]. The rest is for the app's root, which decides what to show by [observeAuthState]
 * (the splash [AuthSplash] while the state is "restoring", the sign-in screens only when the user is signed
 * out, the tabs when there is a session), restores the saved session at start ([restoreSession]) and ends it
 * on "Выйти из аккаунта" ([signOut]); they are here so that the root does not need a second set of
 * dependencies.
 */
class AuthUiDependencies(
    val observeAuthState: ObserveAuthStateUseCase,
    val restoreSession: RestoreSessionUseCase,
    val signIn: SignInUseCase,
    val register: RegisterUseCase,
    val confirmEmail: ConfirmEmailUseCase,
    val signInPending: SignInPendingUseCase,
    val resendLetter: ResendLetterUseCase,
    val getPendingRegistration: GetPendingRegistrationUseCase,
    val signOut: SignOutUseCase,
)
