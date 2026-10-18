package org.matchlab.academic_net.di

import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.Backend
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.Role
import org.matchlab.academic_net.feature.collaborators.domain.RoleRequirement
import org.matchlab.academic_net.common.result.SubmitResult
import org.matchlab.academic_net.feature.auth.domain.AccessToken
import org.matchlab.academic_net.feature.auth.domain.Session
import org.matchlab.academic_net.feature.projectdetails.domain.ProjectDetailsResult
import org.matchlab.academic_net.feature.projectsearch.domain.ProjectQuery
import org.matchlab.academic_net.feature.projects.domain.RoleDraft
import org.matchlab.academic_net.mockserver.MockAccounts
import kotlin.test.Test
import org.matchlab.academic_net.securestore.InMemorySecureStore
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class AppComponentTest {
    /**
     * The graph of a signed-in user, as the root builds it after a sign-in. It is the Gateway's: the profile and the
     * settings have no mock any more, so the mock backend cannot build a session graph. Building it makes no call (the
     * session client of the Gateway only keeps the token), and the features that are not connected to the server yet
     * run on their mocks.
     */
    private fun sessionGraph(): SessionComponent = createSessionComponent(
        createAppComponent(InMemorySecureStore(), Backend.Gateway()),
        Session(MockAccounts.viewer.id, "student@hse.ru", AccessToken("test-token")),
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
    fun collaboratorsSeeWhatProjectsScreensChange() = runTest {
        val component = sessionGraph()
        val projectId = ProjectId("project-digital-twin")

        // The role is added through the projects feature...
        val added = component.dependencies.projects.addRole(projectId, RoleDraft("DevOps", listOf("Docker")))
        val role = assertIs<SubmitResult.Success<Role>>(added).value

        // ...and the collaborators feature, through the adapter, finds it: both read one repository.
        assertEquals(
            RoleRequirement("Цифровой двойник лаборатории", "DevOps", listOf("Docker")),
            component.roleRequirements.getRoleRequirement(projectId, role.id),
        )
    }

    @Test
    fun projectSearchFindsTheMockCatalog() = runTest {
        val found = sessionGraph().dependencies.projectSearch.searchProjects(ProjectQuery()).getOrThrow()

        assertTrue(found.isNotEmpty())
    }

    @Test
    fun aFoundProjectOpensAsAProjectPage() = runTest {
        val dependencies = sessionGraph().dependencies
        val hit = dependencies.projectSearch.searchProjects(ProjectQuery()).getOrThrow().first()

        val page = dependencies.projectDetails.getProjectDetails(hit.project.id)

        assertIs<ProjectDetailsResult.Found>(page)
    }

    @Test
    fun componentsDoNotShareState() {
        assertNotSame(sessionGraph().projectsRepository, sessionGraph().projectsRepository)
    }
}
