package org.matchlab.academic_net.di

import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.Role
import org.matchlab.academic_net.common.result.SubmitResult
import org.matchlab.academic_net.feature.collaborators.domain.RoleRequirement
import org.matchlab.academic_net.feature.projectsearch.domain.ProjectQuery
import org.matchlab.academic_net.feature.projects.domain.RoleDraft
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class AppComponentTest {
    @Test
    fun everyFeatureGetsItsDependencies() {
        val dependencies = createAppComponent().dependencies

        assertNotNull(dependencies.projects)
        assertNotNull(dependencies.chats)
        assertNotNull(dependencies.collaborators)
        assertNotNull(dependencies.feed)
        assertNotNull(dependencies.projectSearch)
    }

    @Test
    fun collaboratorsSeeWhatProjectsScreensChange() = runTest {
        val component = createAppComponent()
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
        val found = createAppComponent().dependencies.projectSearch.searchProjects(ProjectQuery()).getOrThrow()

        assertTrue(found.isNotEmpty())
    }

    @Test
    fun componentsDoNotShareState() {
        assertNotSame(createAppComponent().projectsRepository, createAppComponent().projectsRepository)
    }
}
