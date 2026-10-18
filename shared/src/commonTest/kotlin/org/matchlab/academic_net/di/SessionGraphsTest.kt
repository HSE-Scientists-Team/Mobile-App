package org.matchlab.academic_net.di

import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.feature.auth.domain.AccessToken
import org.matchlab.academic_net.feature.auth.domain.AuthState
import org.matchlab.academic_net.feature.auth.domain.Session
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The one open graph of the signed-in user: the same session gets the same graph, another session ends the first one, and
 * closing a graph is not signing out.
 */
class SessionGraphsTest {
    private val student = Session(UserId(ServerUserId), StudentEmail, AccessToken("token-1"))
    private val another = Session(UserId("another-user"), "another@hse.ru", AccessToken("token-9"))

    private class Recording(launch: AppLaunch) {
        val made = mutableListOf<SessionComponent>()
        val graphs = ComponentSessionGraphs(launch.app) { app, session ->
            createSessionComponent(app, session).also { made += it }
        }
    }

    private fun recording(launch: AppLaunch = AppLaunch()) = Recording(launch)

    @Test
    fun theSameSessionGetsTheSameGraphAndNoSecondOneIsBuilt() {
        val recording = recording()

        val first = recording.graphs.open(student)
        val again = recording.graphs.open(student)

        assertSame(first, again)
        assertEquals(1, recording.made.size)
    }

    @Test
    fun aSessionThatIsEqualButNotTheSameObjectGetsTheSameGraph() {
        val recording = recording()

        val first = recording.graphs.open(student)
        val again = recording.graphs.open(student.copy())

        assertSame(first, again)
        assertEquals(1, recording.made.size)
    }

    @Test
    fun anotherSessionEndsTheOpenGraphAndGetsANewOne() {
        val recording = recording()
        val first = recording.graphs.open(student)

        val second = recording.graphs.open(another)

        assertNotSame(first, second)
        assertEquals(2, recording.made.size)
        assertTrue(recording.made[0].resources.isClosed, "the previous account's graph is over")
        assertFalse(recording.made[1].resources.isClosed)
    }

    @Test
    fun theSameUserWithAnotherTokenIsAnotherSession() {
        val recording = recording()
        val first = recording.graphs.open(student)

        val second = recording.graphs.open(student.copy(accessToken = AccessToken("token-2")))

        assertNotSame(first, second)
        assertTrue(recording.made[0].resources.isClosed)
    }

    @Test
    fun closingEndsTheGraphAndTheNextOpenBuildsANewOneEvenForTheSameSession() {
        val recording = recording()
        val first = recording.graphs.open(student)

        recording.graphs.close()
        val second = recording.graphs.open(student)

        assertTrue(recording.made[0].resources.isClosed)
        assertNotSame(first, second)
        assertEquals(2, recording.made.size)
        assertFalse(recording.made[1].resources.isClosed)
    }

    @Test
    fun closingWhenNothingIsOpenDoesNothing() {
        val recording = recording()

        recording.graphs.close()
        recording.graphs.close()

        assertEquals(emptyList(), recording.made)
    }

    @Test
    fun closingTwiceClosesTheGraphOnce() {
        val recording = recording()
        recording.graphs.open(student)

        recording.graphs.close()
        recording.graphs.close()

        assertEquals(1, recording.made.size)
        assertTrue(recording.made.single().resources.isClosed)
    }

    @Test
    fun signingOutAndInAgainGivesAGraphWithoutTheCachesOfTheOldAccount() = runTest {
        val launch = AppLaunch()
        val recording = recording(launch)
        launch.signIn()
        val before = recording.graphs.open(launch.session)
        val repositoryBefore = recording.made.single().projectsRepository

        launch.app.auth.signOut()
        recording.graphs.close()
        launch.signIn()
        val after = recording.graphs.open(launch.session)

        assertNotSame(before, after)
        assertNotSame(repositoryBefore, recording.made.last().projectsRepository)
        assertTrue(recording.made.first().resources.isClosed)
    }

    @Test
    fun closingTheGraphDoesNotSignTheUserOut() = runTest {
        val launch = AppLaunch()
        val recording = recording(launch)
        launch.signIn()
        recording.graphs.open(launch.session)

        recording.graphs.close()

        assertTrue(launch.state is AuthState.SignedIn)
    }

    @Test
    fun theProcessGraphsStartRestoringTheSessionOnce() = runTest {
        val first = AppLaunch().also { it.signIn() }
        val second = AppLaunch(store = first.store)
        val graphs = AppGraphs(second.app, ComponentSessionGraphs(second.app))

        graphs.start(backgroundScope).join()
        graphs.start(backgroundScope).join()

        assertEquals(AuthState.SignedIn(first.session), second.state)
        assertEquals(1, second.server.requests("GET /api/v1/sessions/current").size)
        assertSame(second.app.auth, graphs.auth)
    }
}
