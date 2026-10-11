package org.matchlab.academic_net.di

import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.Role
import org.matchlab.academic_net.feature.collaborators.domain.RoleRequirement
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.result.SubmitResult
import org.matchlab.academic_net.feature.auth.domain.AuthState
import org.matchlab.academic_net.feature.auth.domain.CodeRequestOutcome
import org.matchlab.academic_net.common.result.LoadResult
import org.matchlab.academic_net.feature.profile.domain.WorkDraft
import org.matchlab.academic_net.feature.projectdetails.domain.ProjectDetailsResult
import org.matchlab.academic_net.feature.projectsearch.domain.ProjectQuery
import org.matchlab.academic_net.feature.projects.domain.RoleDraft
import kotlin.test.Test
import org.matchlab.academic_net.securestore.InMemorySecureStore
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class AppComponentTest {
    @Test
    fun everyFeatureGetsItsDependencies() {
        val dependencies = createAppComponent(InMemorySecureStore()).dependencies

        assertNotNull(dependencies.projects)
        assertNotNull(dependencies.chats)
        assertNotNull(dependencies.collaborators)
        assertNotNull(dependencies.feed)
        assertNotNull(dependencies.projectSearch)
        assertNotNull(dependencies.projectDetails)
        assertNotNull(dependencies.profile)
        assertNotNull(dependencies.auth)
        assertNotNull(dependencies.settings)
    }

    @Test
    fun collaboratorsSeeWhatProjectsScreensChange() = runTest {
        val component = createAppComponent(InMemorySecureStore())
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
        val found = createAppComponent(InMemorySecureStore()).dependencies.projectSearch.searchProjects(ProjectQuery()).getOrThrow()

        assertTrue(found.isNotEmpty())
    }

    @Test
    fun aFoundProjectOpensAsAProjectPage() = runTest {
        val dependencies = createAppComponent(InMemorySecureStore()).dependencies
        val hit = dependencies.projectSearch.searchProjects(ProjectQuery()).getOrThrow().first()

        val page = dependencies.projectDetails.getProjectDetails(hit.project.id)

        assertIs<ProjectDetailsResult.Found>(page)
    }

    @Test
    fun aWorkAddedToMyProfileShowsInTheProfileOthersRead() = runTest {
        val profile = createAppComponent(InMemorySecureStore()).dependencies.profile

        profile.addWork(WorkDraft(title = "Graph neural networks for campus routing", description = "A survey"))

        // Both profile interfaces are served by one repository: what I add is what the read side returns.
        val found = profile.getUserProfile(UserId("user-me"))
        val me = assertIs<LoadResult.Found<org.matchlab.academic_net.feature.profile.domain.UserProfile>>(found).value
        assertTrue(me.works.any { it.title == "Graph neural networks for campus routing" })
    }

    @Test
    fun theSessionOfASignInIsRestoredByTheNextProcessAndEndsWithSignOut() = runTest {
        val store = InMemorySecureStore()
        val first = createAppComponent(store).dependencies.auth
        first.restoreSession()
        assertIs<AuthState.SignedOut>(first.observeAuthState().value)

        first.requestCode("student@hse.ru")
        first.verifyCode("student@hse.ru", "123456")
        assertIs<AuthState.SignedIn>(first.observeAuthState().value)

        // A new process: another component over the same encrypted storage.
        val second = createAppComponent(store).dependencies.auth
        second.restoreSession()
        assertIs<AuthState.SignedIn>(second.observeAuthState().value)

        second.signOut()
        val third = createAppComponent(store).dependencies.auth
        third.restoreSession()
        assertIs<AuthState.SignedOut>(third.observeAuthState().value)
    }

    @Test
    fun anAddressOutsideTheRegistryGetsNoCode() = runTest {
        val auth = createAppComponent(InMemorySecureStore()).dependencies.auth

        val result = auth.requestCode("someone@gmail.com")

        val outcome = assertIs<SubmitResult.Success<CodeRequestOutcome>>(result).value
        assertIs<CodeRequestOutcome.UntrustedDomain>(outcome)
    }

    @Test
    fun componentsDoNotShareState() {
        assertNotSame(createAppComponent(InMemorySecureStore()).projectsRepository, createAppComponent(InMemorySecureStore()).projectsRepository)
    }
}
