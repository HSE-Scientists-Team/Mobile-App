package org.matchlab.academic_net.feature.profile.domain.usecase

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.result.SubmitResult
import org.matchlab.academic_net.feature.profile.domain.AboutDraft
import org.matchlab.academic_net.feature.profile.domain.AboutDraftError
import org.matchlab.academic_net.feature.profile.domain.DocumentDraft
import org.matchlab.academic_net.feature.profile.domain.DocumentDraftError
import org.matchlab.academic_net.feature.profile.domain.FakeMyProfileRepository
import org.matchlab.academic_net.feature.profile.domain.ProofAttachment
import org.matchlab.academic_net.feature.profile.domain.SomeDocument
import org.matchlab.academic_net.feature.profile.domain.SomeMyProfile
import org.matchlab.academic_net.feature.profile.domain.SomeWork
import org.matchlab.academic_net.feature.profile.domain.VerificationResultId
import org.matchlab.academic_net.feature.profile.domain.WorkDraft
import org.matchlab.academic_net.feature.profile.domain.WorkDraftError
import org.matchlab.academic_net.feature.profile.domain.result

class ObserveMyProfileUseCaseTest {
    @Test
    fun passesTheProfileOfTheRepository() = runTest {
        val repository = FakeMyProfileRepository()

        assertSame(SomeMyProfile, ObserveMyProfileUseCase(repository)().first())
    }
}

class UpdateAboutUseCaseTest {
    private val repository = FakeMyProfileRepository()
    private val updateAbout = UpdateAboutUseCase(repository)

    @Test
    fun invalidDraftIsNotSent() = runTest {
        val result = updateAbout(AboutDraft(about = "a".repeat(2000)))

        assertEquals(SubmitResult.Invalid(setOf(AboutDraftError.AboutTooLong)), result)
        assertTrue(repository.aboutDrafts.isEmpty())
    }

    @Test
    fun validDraftIsSentNormalizedAndComesBack() = runTest {
        val result = updateAbout(
            AboutDraft(about = " Hi ", interestTypes = setOf(ProjectType.Practical), skills = listOf(" Python ", "python")),
        )

        assertSame(SomeMyProfile, assertIs<SubmitResult.Success<*>>(result).value)
        assertEquals(
            listOf(AboutDraft("Hi", setOf(ProjectType.Practical), emptyList(), listOf("Python"))),
            repository.aboutDrafts,
        )
    }

    @Test
    fun failureOfTheRepositoryIsAResultWithTheCause() = runTest {
        val cause = IllegalStateException("offline")
        repository.failure = cause

        assertSame(cause, assertIs<SubmitResult.Failure>(updateAbout(AboutDraft())).cause)
    }

    @Test
    fun cancellationIsNotAFailure() = runTest {
        repository.failure = CancellationException("cancelled")

        assertFailsWith<CancellationException> { updateAbout(AboutDraft()) }
    }
}

class AddWorkUseCaseTest {
    private val repository = FakeMyProfileRepository()
    private val addWork = AddWorkUseCase(repository)

    @Test
    fun invalidDraftIsNotSent() = runTest {
        val result = addWork(WorkDraft(title = "Title", description = "Text", link = "not a link"))

        assertEquals(SubmitResult.Invalid(setOf(WorkDraftError.LinkInvalid)), result)
        assertTrue(repository.workDrafts.isEmpty())
    }

    @Test
    fun emptyDraftIsInvalid() = runTest {
        val result = addWork(WorkDraft())

        assertEquals(
            SubmitResult.Invalid(setOf(WorkDraftError.TitleEmpty, WorkDraftError.DescriptionEmpty)),
            result,
        )
    }

    @Test
    fun validDraftIsSentNormalizedAndTheWorkComesBack() = runTest {
        val result = addWork(
            WorkDraft(
                title = "  Title ",
                description = " Text ",
                link = " https://github.com/a/b ",
                proof = ProofAttachment(" https://files.example.com/a.pdf "),
            ),
        )

        assertSame(SomeWork, assertIs<SubmitResult.Success<*>>(result).value)
        assertEquals(
            listOf(
                WorkDraft(
                    title = "Title",
                    description = "Text",
                    link = "https://github.com/a/b",
                    proof = ProofAttachment("https://files.example.com/a.pdf"),
                ),
            ),
            repository.workDrafts,
        )
    }

    @Test
    fun workWithoutLinkAndProofIsSentToo() = runTest {
        val result = addWork(WorkDraft(title = "Title", description = "Text"))

        assertIs<SubmitResult.Success<*>>(result)
        assertEquals(1, repository.workDrafts.size)
    }

    @Test
    fun failureOfTheRepositoryIsAResultWithTheCause() = runTest {
        val cause = IllegalStateException("offline")
        repository.failure = cause

        val result = addWork(WorkDraft(title = "Title", description = "Text"))

        assertSame(cause, assertIs<SubmitResult.Failure>(result).cause)
    }

    @Test
    fun cancellationIsNotAFailure() = runTest {
        repository.failure = CancellationException("cancelled")

        assertFailsWith<CancellationException> { addWork(WorkDraft(title = "Title", description = "Text")) }
    }
}

class AddDocumentUseCaseTest {
    private val repository = FakeMyProfileRepository()
    private val addDocument = AddDocumentUseCase(repository)
    private val valid = DocumentDraft(
        title = "Диплом",
        issuer = "МГУ",
        year = "2025",
        proof = ProofAttachment("https://files.example.com/d.pdf"),
    )

    @Test
    fun documentWithoutProofIsNotSent() = runTest {
        val result = addDocument(valid.copy(proof = null))

        assertEquals(SubmitResult.Invalid(setOf(DocumentDraftError.ProofMissing)), result)
        assertTrue(repository.documentDrafts.isEmpty())
    }

    @Test
    fun validDraftIsSentNormalizedAndTheDocumentComesBack() = runTest {
        val result = addDocument(valid.copy(title = " Диплом ", year = " 2025 "))

        assertSame(SomeDocument, assertIs<SubmitResult.Success<*>>(result).value)
        assertEquals(listOf(valid), repository.documentDrafts)
    }

    @Test
    fun failureOfTheRepositoryIsAResultWithTheCause() = runTest {
        val cause = IllegalStateException("offline")
        repository.failure = cause

        assertSame(cause, assertIs<SubmitResult.Failure>(addDocument(valid)).cause)
    }

    @Test
    fun cancellationIsNotAFailure() = runTest {
        repository.failure = CancellationException("cancelled")

        assertFailsWith<CancellationException> { addDocument(valid) }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class VerificationResultsUseCasesTest {
    private val repository = FakeMyProfileRepository()

    @Test
    fun resultsComeFromTheRepository() = runTest {
        repository.results.value = listOf(result("a"), result("b", isSeen = true))

        val results = ObserveVerificationResultsUseCase(repository)().first()

        assertEquals(listOf("a", "b"), results.map { it.id.value })
    }

    @Test
    fun hasUnseenIsFalseWithoutResults() = runTest {
        assertEquals(false, ObserveHasUnseenResultsUseCase(repository)().first())
    }

    @Test
    fun hasUnseenIsTrueWhenOneResultIsNotSeen() = runTest {
        repository.results.value = listOf(result("a", isSeen = true), result("b"))

        assertEquals(true, ObserveHasUnseenResultsUseCase(repository)().first())
    }

    @Test
    fun hasUnseenIsFalseWhenAllAreSeen() = runTest {
        repository.results.value = listOf(result("a", isSeen = true))

        assertEquals(false, ObserveHasUnseenResultsUseCase(repository)().first())
    }

    @Test
    fun hasUnseenEmitsOnlyChanges() = runTest {
        val emitted = mutableListOf<Boolean>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            ObserveHasUnseenResultsUseCase(repository)().toList(emitted)
        }

        repository.results.value = listOf(result("a"))
        repository.results.value = listOf(result("a"), result("b"))
        repository.results.value = listOf(result("a", isSeen = true), result("b", isSeen = true))
        job.cancel()

        assertEquals(listOf(false, true, false), emitted)
    }

    @Test
    fun markingSeenPassesTheIds() = runTest {
        val ids = setOf(VerificationResultId("a"), VerificationResultId("b"))

        val outcome = MarkResultsSeenUseCase(repository)(ids)

        assertTrue(outcome.isSuccess)
        assertEquals(listOf(ids), repository.seen)
    }

    @Test
    fun failureOfMarkingIsAResultNotAnException() = runTest {
        val cause = IllegalStateException("offline")
        repository.failure = cause

        val outcome = MarkResultsSeenUseCase(repository)(setOf(VerificationResultId("a")))

        assertSame(cause, outcome.exceptionOrNull())
    }

    @Test
    fun cancellationOfMarkingIsNotAFailure() = runTest {
        repository.failure = CancellationException("cancelled")

        assertFailsWith<CancellationException> {
            MarkResultsSeenUseCase(repository)(setOf(VerificationResultId("a")))
        }
    }
}
