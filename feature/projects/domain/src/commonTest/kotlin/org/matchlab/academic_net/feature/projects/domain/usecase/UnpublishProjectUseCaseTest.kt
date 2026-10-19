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
    fun aProjectWithAnUnknownStatusIsTriedAndTheServerDecides() = runTest {
        // docs/api-values.md, section 4: only a project known to be off publication is left alone.
        repository.projects.value += testProject("unknown", status = null)

        val result = unpublish(ProjectId("unknown"))

        assertEquals(ProjectStatus.Unpublished, assertIs<UnpublishProjectResult.Unpublished>(result).project.status)
        assertEquals(
            ProjectStatus.Unpublished,
            repository.projects.value.first { it.id == ProjectId("unknown") }.status,
        )
    }

    @Test
    fun anUnpublishedProjectIsNotSentToTheRepositoryAgain() = runTest {
        val before = repository.projects.value

        unpublish(ProjectId("hidden"))

        assertEquals(before, repository.projects.value)
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
