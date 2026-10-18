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
     * The graph of a signed-in user, as the root builds it after a sign-in. It is the Gateway's: the profile and the
     * settings have no mock any more, so the mock backend cannot build a session graph. Building it makes no call (the
     * session client of the Gateway only keeps the token), and the features that are not connected to the server yet
     * run on their mocks.
     */
    private fun sessionGraph(): SessionComponent = createSessionComponent(
        createAppComponent(InMemorySecureStore(), Backend.Gateway()),
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
        assertNotNull(createAppComponent(InMemorySecureStore(), Backend.Mock).auth)
    }

    @Test
    fun componentsDoNotShareState() {
        assertNotSame(sessionGraph().projectsRepository, sessionGraph().projectsRepository)
    }
}
