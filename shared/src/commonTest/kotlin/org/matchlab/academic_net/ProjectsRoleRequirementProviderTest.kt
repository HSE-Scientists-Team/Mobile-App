package org.matchlab.academic_net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.feature.collaborators.domain.RoleRequirement
import org.matchlab.academic_net.feature.projects.data.ProjectsRepositoryImpl
import org.matchlab.academic_net.feature.projects.data.mock.MockProjectsDataSource
import org.matchlab.academic_net.feature.projects.domain.RoleDraft

class ProjectsRoleRequirementProviderTest {
    private val repository = ProjectsRepositoryImpl(MockProjectsDataSource(latencyMillis = 0))
    private val provider = ProjectsRoleRequirementProvider(repository)

    @Test
    fun roleOfAProjectBecomesARequirement() = runTest {
        val requirement = provider.getRoleRequirement(
            ProjectId("project-campus-navigation"),
            RoleId("role-mobile-developer"),
        )

        assertEquals(
            RoleRequirement("AI-навигация по кампусу", "Mobile Developer", listOf("Flutter", "Dart")),
            requirement,
        )
    }

    @Test
    fun roleAddedLaterIsFound() = runTest {
        val projectId = ProjectId("project-digital-twin")
        val role = repository.addRole(projectId, RoleDraft("DevOps", listOf("Docker")))

        assertEquals(
            RoleRequirement("Цифровой двойник лаборатории", "DevOps", listOf("Docker")),
            provider.getRoleRequirement(projectId, role.id),
        )
    }

    @Test
    fun unknownProjectOrRoleIsNull() = runTest {
        assertNull(provider.getRoleRequirement(ProjectId("missing"), RoleId("role-mobile-developer")))
        assertNull(provider.getRoleRequirement(ProjectId("project-campus-navigation"), RoleId("missing")))
    }
}
