package org.matchlab.academic_net.di

import org.matchlab.academic_net.Backend
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.feature.auth.domain.AccessToken
import org.matchlab.academic_net.feature.auth.domain.Session
import kotlin.test.Test
import org.matchlab.academic_net.securestore.InMemorySecureStore
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame

class AppComponentTest {
    /**
     * The graph of a signed-in user, as the root builds it after a sign-in. The Gateway client runs over the engine of
     * [FakeServer] (a Ktor `MockEngine`), so nothing leaves the machine and no engine of the platform is left open.
     * Building the graph sends no request (the session client of the Gateway only keeps the token). Every feature
     * but the chats gets its data from that Gateway; the chats, which the API does not have (API-GAP G50), run on
     * a local source that starts empty.
     */
    private fun sessionGraph(): SessionComponent = createSessionComponent(
        createAppComponent(InMemorySecureStore(), Backend.Gateway(), FakeServer().factory),
        Session(UserId("user-me"), "student@hse.ru", AccessToken("test-token")),
    )

    @Test
    fun everyFeatureGetsItsDependencies() {
        val dependencies = sessionGraph().dependencies

        assertNotNull(dependencies.projects)
        assertNotNull(dependencies.chats)
        assertNotNull(dependencies.collaborators)
        assertNotNull(dependencies.feed)
        assertNotNull(dependencies.projectSearch)
        assertNotNull(dependencies.projectDetails)
        assertNotNull(dependencies.profile)
        assertNotNull(dependencies.settings)
    }

    @Test
    fun theSignInGetsItsDependenciesFromTheProcessGraph() {
        assertNotNull(createAppComponent(InMemorySecureStore(), Backend.Gateway(), FakeServer().factory).auth)
    }

    @Test
    fun componentsDoNotShareState() {
        assertNotSame(sessionGraph().projectsRepository, sessionGraph().projectsRepository)
    }
}
