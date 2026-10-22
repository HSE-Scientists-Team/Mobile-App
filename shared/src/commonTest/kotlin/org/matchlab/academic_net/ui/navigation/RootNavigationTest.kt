package org.matchlab.academic_net.ui.navigation

import androidx.compose.runtime.saveable.SaverScope
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.di.SessionGraphs
import org.matchlab.academic_net.SessionDependencies
import org.matchlab.academic_net.feature.auth.domain.AccessToken
import org.matchlab.academic_net.feature.auth.domain.AuthState
import org.matchlab.academic_net.feature.auth.domain.Session
import org.matchlab.academic_net.feature.auth.ui.SignInStart
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private val session = Session(UserId("u1"), "anna@hse.ru", AccessToken("secret-token"))

/** What the root of the app shows for each state of the session: one of three, from the state alone. */
class RootScreenTest {
    @Test
    fun whileTheSavedSessionIsReadTheSplashIsShown() {
        assertEquals(RootScreen.Splash, AuthState.Restoring.toRootScreen())
    }

    @Test
    fun withoutASessionTheVisitorBrowsesAndTheSignInStartsWithTheLastAddress() {
        val screen = AuthState.SignedOut(sessionExpired = false, lastEmail = "anna@hse.ru").toRootScreen()

        assertEquals(RootScreen.Guest(SignInStart(email = "anna@hse.ru", sessionExpired = false)), screen)
    }

    @Test
    fun aSessionThatEndedIsToldOnTheSignInScreen() {
        val screen = AuthState.SignedOut(sessionExpired = true, lastEmail = "anna@hse.ru").toRootScreen()

        assertEquals(RootScreen.Guest(SignInStart(email = "anna@hse.ru", sessionExpired = true)), screen)
    }

    @Test
    fun aFirstLaunchStartsTheSignInWithAnEmptyField() {
        assertEquals(RootScreen.Guest(SignInStart(email = "", sessionExpired = false)), AuthState.SignedOut().toRootScreen())
    }

    @Test
    fun aSessionThatWasRestoredShowsTheTabsAtOnce() {
        assertEquals(RootScreen.Main(session), AuthState.SignedIn(session).toRootScreen())
    }

    @Test
    fun theSameStateAlwaysGivesTheSameScreen() {
        val states = listOf(AuthState.Restoring, AuthState.SignedOut(true, "a@b.c"), AuthState.SignedIn(session))

        for (state in states) assertEquals(state.toRootScreen(), state.toRootScreen())
    }

    @Test
    fun noStateShowsTheGuestScreenToASignedInUser() {
        // A user whose session was restored never sees the guest's screen, not even for a frame.
        assertFalse(AuthState.SignedIn(session).toRootScreen() is RootScreen.Guest)
        assertFalse(AuthState.Restoring.toRootScreen() is RootScreen.Guest)
    }
}

/** The address and the note of the sign-in flow travel between its screens, and the password never does. */
class AuthFlowTest {
    @Test
    fun startingTheRegistrationTakesWhatWasTypedAndForgetsTheNoteOfTheSignIn() {
        val flow = AuthFlow(email = "anna@hse.ru", emailConfirmed = true)

        val started = flow.registrationStarted("anna.new@hse.ru")

        assertEquals(AuthFlow(email = "anna.new@hse.ru", emailConfirmed = false), started)
    }

    @Test
    fun anAddressThatChangedLeavesTheNoteAlone() {
        val flow = AuthFlow(email = "anna@hse.ru", emailConfirmed = true)

        assertEquals(AuthFlow(email = "other@hse.ru", emailConfirmed = true), flow.addressChanged("other@hse.ru"))
        assertEquals(AuthFlow(email = "other@hse.ru", emailConfirmed = false), AuthFlow("a@b.c").addressChanged("other@hse.ru"))
    }

    @Test
    fun returningToTheSignInSaysWhetherTheAddressIsConfirmed() {
        val flow = AuthFlow(email = "anna@hse.ru")

        assertEquals(AuthFlow("anna@hse.ru", emailConfirmed = true), flow.returnedToSignIn("anna@hse.ru", confirmed = true))
        assertEquals(AuthFlow("anna@hse.ru", emailConfirmed = false), flow.returnedToSignIn("anna@hse.ru", confirmed = false))
    }

    @Test
    fun returningToTheSignInDropsAnOldNote() {
        val flow = AuthFlow(email = "anna@hse.ru", emailConfirmed = true)

        assertEquals(AuthFlow("other@hse.ru", emailConfirmed = false), flow.returnedToSignIn("other@hse.ru", confirmed = false))
    }

    @Test
    fun theFlowSurvivesTheDeathOfTheProcessAsTheAddressAndTheFlag() {
        val scope = SaverScope { true }
        for (flow in listOf(AuthFlow(""), AuthFlow("anna@hse.ru"), AuthFlow("anna@hse.ru", emailConfirmed = true))) {
            val saved = with(AuthFlow.Saver) { scope.save(flow) }

            assertEquals(listOf(flow.email, flow.emailConfirmed), saved)
            assertEquals(flow, AuthFlow.Saver.restore(saved!!))
        }
    }

    @Test
    fun theSavedFlowHoldsNothingButTheAddressAndTheFlag() {
        val saved = with(AuthFlow.Saver) { SaverScope { true }.save(AuthFlow("anna@hse.ru", true)) } as List<*>

        assertEquals(2, saved.size)
    }

    @Test
    fun theAddressIsNotPrintedByTheFlow() {
        val text = AuthFlow("anna@hse.ru", emailConfirmed = true).toString()

        assertFalse("anna" in text, text)
        assertFalse("hse.ru" in text, text)
        assertTrue("hasEmail=true" in text && "emailConfirmed=true" in text, text)
        assertTrue("hasEmail=false" in AuthFlow("").toString())
    }
}

/** The three screens of the sign-in flow have routes without arguments: nothing the user typed goes into an address. */
class AuthRoutesTest {
    private val routes = listOf(AuthRoutes.SignIn, AuthRoutes.Register, AuthRoutes.Confirm)

    @Test
    fun thereAreThreeDifferentRoutes() {
        assertEquals(3, routes.toSet().size)
    }

    @Test
    fun noRouteHasAnArgument() {
        for (route in routes) {
            assertFalse('{' in route || '}' in route || '?' in route || '=' in route, route)
        }
    }

    @Test
    fun theRoutesAreOfTheAuthFlowAndNotOfTheTabs() {
        assertTrue(routes.all { it.startsWith("auth/") }, routes.toString())
    }
}

/**
 * The view model stores of the two graphs: what a state of the session lets go of, in which order, and that doing it again
 * is harmless.
 */
class GraphViewModelStoresTest {
    private val journal = mutableListOf<String>()

    private class Marker(private val name: String, private val journal: MutableList<String>) : ViewModel() {
        override fun onCleared() {
            journal += "cleared $name"
        }
    }

    private val sessions = object : SessionGraphs {
        override fun open(session: Session): SessionDependencies = error("the stores only close the graph")

        override fun close() {
            journal += "graph closed"
        }
    }

    private class Factory(private val name: String, private val journal: MutableList<String>) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T = Marker(name, journal) as T
    }

    private fun GraphViewModelStores.fill() {
        ViewModelProvider.create(auth.viewModelStore, Factory("auth", journal)).get(Marker::class)
        ViewModelProvider.create(main.viewModelStore, Factory("main", journal)).get(Marker::class)
    }

    @Test
    fun signedOutClearsTheTabsAndLeavesTheGraphToTheVisitorsGraphThatReplacesIt() {
        val stores = GraphViewModelStores().apply { fill() }

        stores.release(AuthState.SignedOut(), sessions)

        // Closing the graph here would end the visitor's graph, which the root has already opened (SessionGraphs.open
        // closes the one it replaces).
        assertEquals(listOf("cleared main"), journal)
    }

    @Test
    fun signedOutLeavesTheSignInFlowAlone() {
        val stores = GraphViewModelStores().apply { fill() }

        stores.release(AuthState.SignedOut(sessionExpired = true, lastEmail = "a@b.c"), sessions)

        assertFalse("cleared auth" in journal)
    }

    @Test
    fun signedInClearsTheSignInFlowAndKeepsTheGraphAndTheTabs() {
        val stores = GraphViewModelStores().apply { fill() }

        stores.release(AuthState.SignedIn(session), sessions)

        assertEquals(listOf("cleared auth"), journal)
    }

    @Test
    fun signedInAlsoClearsTheScreensTheVisitorWasLookingAt() {
        val stores = GraphViewModelStores().apply {
            fill()
            ViewModelProvider.create(guest.viewModelStore, Factory("guest", journal)).get(Marker::class)
        }

        stores.release(AuthState.SignedIn(session), sessions)

        assertEquals(setOf("cleared auth", "cleared guest"), journal.toSet())
    }

    @Test
    fun restoringReleasesNothing() {
        val stores = GraphViewModelStores().apply { fill() }

        stores.release(AuthState.Restoring, sessions)

        assertEquals(emptyList(), journal)
    }

    @Test
    fun doingItAgainIsHarmless() {
        val stores = GraphViewModelStores().apply { fill() }

        stores.release(AuthState.SignedOut(), sessions)
        stores.release(AuthState.SignedOut(), sessions)
        stores.release(AuthState.SignedIn(session), sessions)
        stores.release(AuthState.SignedIn(session), sessions)

        assertEquals(1, journal.count { it == "cleared main" }, "a view model is cleared once")
        assertEquals(1, journal.count { it == "cleared auth" })
    }

    @Test
    fun theStoresCanBeFilledAgainAfterTheyWereCleared() {
        val stores = GraphViewModelStores().apply { fill() }
        stores.release(AuthState.SignedOut(), sessions)
        journal.clear()

        stores.fill()
        stores.release(AuthState.SignedOut(), sessions)

        assertEquals(listOf("cleared main"), journal)
    }

    @Test
    fun theStoresOfTheTwoGraphsAreNotTheSameStore() {
        val stores = GraphViewModelStores()

        assertNotEquals(stores.auth.viewModelStore, stores.main.viewModelStore)
    }

    @Test
    fun whenTheActivityEndsBothStoresAreCleared() {
        val outer = ViewModelStore()
        val stores = ViewModelProvider.create(
            outer,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T = GraphViewModelStores() as T
            },
        ).get(GraphViewModelStores::class)
        stores.fill()

        outer.clear()

        assertEquals(setOf("cleared auth", "cleared main"), journal.toSet())
        assertEquals(2, journal.size)
    }
}
