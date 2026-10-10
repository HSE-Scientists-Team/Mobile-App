package org.matchlab.academic_net.feature.projects.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.common.result.SubmitResult
import org.matchlab.academic_net.feature.projects.domain.FakeProjectsRepository
import org.matchlab.academic_net.feature.projects.domain.ProjectDraft
import org.matchlab.academic_net.feature.projects.domain.ProjectDraftError
import org.matchlab.academic_net.feature.projects.domain.RoleDraft
import org.matchlab.academic_net.feature.projects.domain.validDraft

class PublishProjectUseCaseTest {
    private val repository = FakeProjectsRepository()
    private val publish = PublishProjectUseCase(repository)

    @Test
    fun invalidDraftIsNotSent() = runTest {
        val result = publish(ProjectDraft())

        val invalid = assertIs<SubmitResult.Invalid<ProjectDraftError>>(result)
        assertEquals(ProjectDraftError.TitleEmpty in invalid.errors, true)
        assertNull(repository.published)
    }

    @Test
    fun validDraftIsPublishedWithRoles() = runTest {
        val draft = validDraft().copy(
            tags = listOf("Python", "python"),
            roles = listOf(RoleDraft(" ML Engineer ", listOf("TensorFlow"))),
        )

        val result = publish(draft)

        val project = assertIs<SubmitResult.Success<*>>(result).value
        assertEquals(project, repository.projects.value.first())
        assertEquals(listOf("Python"), repository.published?.tags)
        assertEquals(listOf(RoleDraft("ML Engineer", listOf("TensorFlow"))), repository.published?.roles)
    }

    @Test
    fun publishedProjectIsRecruiting() = runTest {
        publish(validDraft())

        assertEquals(ProjectStatus.Recruiting, repository.projects.value.first().status)
    }

    @Test
    fun repositoryErrorBecomesFailure() = runTest {
        val error = IllegalStateException("no network")
        repository.failure = error

        val result = publish(validDraft())

        assertSame(error, assertIs<SubmitResult.Failure>(result).cause)
    }
}
