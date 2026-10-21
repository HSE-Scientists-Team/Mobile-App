package org.matchlab.academic_net.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.matchlab.academic_net.feature.auth.ui.AuthUiDependencies
import org.matchlab.academic_net.feature.auth.ui.ConfirmEmailRoute
import org.matchlab.academic_net.feature.auth.ui.RegisterRoute
import org.matchlab.academic_net.feature.auth.ui.SignInRoute
import org.matchlab.academic_net.feature.auth.ui.SignInStart

/**
 * Routes of the sign-in flow, shown while there is no session. None has an argument: see [AuthFlow]. There is no route
 * of a forgotten password: API-GAP G02, the API cannot recover or change a password.
 */
internal object AuthRoutes {
    /** "19 Вход": the start of the flow. */
    const val SignIn = "auth/sign_in"

    /** "20 Регистрация". */
    const val Register = "auth/register"

    /** "21 Подтвердите почту". */
    const val Confirm = "auth/confirm"
}

/**
 * The sign-in flow: "Вход", from which the user goes to "Регистрация" and then to "Подтвердите почту". Signing in is
 * done by the root when the state of the session changes, so no screen of the flow navigates on success.
 *
 * Shown only for a user who is signed out. [start] is what the first screen starts with: the address the user last
 * used (so only the password is left to type) and, if the server stopped accepting their session, the note that says
 * so. The note stays for as long as the user is on the flow, also after a trip to the registration and back.
 * [emailConfirmed]: the address was just confirmed from the link of the letter and the app could not sign in by
 * itself (the password of the registration is not remembered), so the sign-in screen says "Почта подтверждена. Войдите".
 *
 * The address travels between the screens in [AuthFlow], not in the routes; **the password travels nowhere**.
 *
 * Every navigation names the entry of the screen that asks for it (`navigateSingleTop(entry, ...)`,
 * `popBackStackOnce(entry)`): see [isCurrentEntry]. A request of a screen that is no longer the current one does
 * nothing, and neither does the address it carries: the flow changes only when the navigation was carried out.
 *
 * **The choice about statistics.** The registration form asks "Отправлять статистику использования" (on by default).
 * Every tick or clearing of the box goes to [onStatisticsChosen] at once, and the box as it stands goes there once more
 * with the sent letter, before the confirmation opens (that is what keeps a box that was never touched): so the setting
 * of the device is what the box shows from the first moment of the account's life, also when the form is left before
 * the letter's answer comes. It is not navigation state, so it is passed on whether or not the navigation was carried
 * out: the letter has been sent with that choice made.
 *
 * @param onStatisticsChosen the person's choice on the registration form, `true` to send usage statistics; the app keeps
 * it as the setting of the device
 */
@Composable
internal fun AuthNavHost(
    dependencies: AuthUiDependencies,
    start: SignInStart,
    onStatisticsChosen: (Boolean) -> Unit,
    emailConfirmed: Boolean = false,
) {
    val navController = rememberNavController()
    var flow by rememberSaveable(stateSaver = AuthFlow.Saver) { mutableStateOf(AuthFlow(start.email, emailConfirmed)) }

    NavHost(
        navController = navController,
        startDestination = AuthRoutes.SignIn,
        enterTransition = NavTransitions.enter,
        exitTransition = NavTransitions.exit,
        popEnterTransition = NavTransitions.enter,
        popExitTransition = NavTransitions.exit,
    ) {
        composable(AuthRoutes.SignIn) { entry ->
            SignInRoute(
                dependencies = dependencies,
                email = flow.email,
                sessionExpired = start.sessionExpired,
                emailConfirmed = flow.emailConfirmed,
                onRegisterClick = { typed ->
                    if (navController.navigateSingleTop(entry, AuthRoutes.Register)) {
                        flow = flow.registrationStarted(typed)
                    }
                },
            )
        }
        composable(AuthRoutes.Register) { entry ->
            RegisterRoute(
                dependencies = dependencies,
                email = flow.email,
                // The registration stays in the stack under the confirmation: "Изменить почту" returns to the form
                // as the user left it.
                onShareStatisticsChange = onStatisticsChosen,
                onLetterSent = { sent, shareStatistics ->
                    // First the choice, then the next screen: see the documentation of this function.
                    onStatisticsChosen(shareStatistics)
                    if (navController.navigateSingleTop(entry, AuthRoutes.Confirm)) {
                        flow = flow.addressChanged(sent)
                    }
                },
                onSignInClick = { typed ->
                    if (navController.popBackStackOnce(entry)) flow = flow.addressChanged(typed)
                },
                onNavigateBack = { navController.popBackStackOnce(entry) },
            )
        }
        composable(AuthRoutes.Confirm) { entry ->
            // The sign-in screen the flow returns to is a new one, not the one under the registration: the user
            // finished a round trip, and the old form would still show an error and a password from before it.
            val returnToSignIn: (String, Boolean) -> Unit = { address, confirmed ->
                val navigated = navController.navigateOnce(entry, AuthRoutes.SignIn) {
                    popUpTo(AuthRoutes.SignIn) { inclusive = true }
                }
                if (navigated) flow = flow.returnedToSignIn(address, confirmed)
            }
            ConfirmEmailRoute(
                dependencies = dependencies,
                email = flow.email,
                onNavigateBack = { navController.popBackStackOnce(entry) },
                onSignInRequired = { address -> returnToSignIn(address, false) },
                onEmailConfirmed = { address -> returnToSignIn(address, true) },
                onRegisterAgain = { address ->
                    if (navController.popBackStackOnce(entry)) flow = flow.addressChanged(address)
                },
            )
        }
    }
}
