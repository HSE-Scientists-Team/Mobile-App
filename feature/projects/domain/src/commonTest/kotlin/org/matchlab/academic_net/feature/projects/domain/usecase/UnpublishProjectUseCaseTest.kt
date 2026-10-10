package org.matchlab.academic_net.feature.projects.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.feature.projects.domain.FakeProjectsRepository
import org.matchlab.academic_net.feature.projects.domain.testProject

class UnpublishProjectUseCaseTest {
    private val repository = FakeProjectsRepository(
        listOf(
            testProject("recruiting", ProjectStatus.Recruiting),
            testProject("hidden", ProjectStatus.Unpublished),
        ),
    )
    private val unpublish = UnpublishProjectUseCase(repository)

    @Test
    fun recruitingProjectIsUnpublished() = runTest {
        val result = unpublish(ProjectId("recruiting"))

        val project = assertIs<UnpublishProjectResult.Unpublished>(result).project
        assertEquals(ProjectStatus.Unpublished, project.status)
        assertEquals(
            ProjectStatus.Unpublished,
            repository.projects.value.first { it.id == ProjectId("recruiting") }.status,
        )
    }

    @Test
    fun alreadyUnpublishedProjectIsLeftAlone() = runTest {
        assertEquals(UnpublishProjectResult.NotPublished, unpublish(ProjectId("hidden")))
    }

    @Test
    fun unknownProjectIsNotFound() = runTest {
        assertEquals(UnpublishProjectResult.NotFound, unpublish(ProjectId("missing")))
    }

    @Test
    fun repositoryErrorBecomesFailure() = runTest {
        val error = IllegalStateException("no network")
        repository.failure = error

        val result = unpublish(ProjectId("recruiting"))

        assertSame(error, assertIs<UnpublishProjectResult.Failure>(result).cause)
    }
}
