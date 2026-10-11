package org.matchlab.academic_net.feature.profile.ui.my

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.result.LoadResult
import org.matchlab.academic_net.feature.profile.domain.DocumentDraftError
import org.matchlab.academic_net.feature.profile.domain.DocumentKind
import org.matchlab.academic_net.feature.profile.domain.PortfolioWork
import org.matchlab.academic_net.feature.profile.domain.ProfileDocument
import org.matchlab.academic_net.feature.profile.domain.ProfileLimits
import org.matchlab.academic_net.feature.profile.domain.ProofAttachment
import org.matchlab.academic_net.feature.profile.domain.UserProfile
import org.matchlab.academic_net.feature.profile.domain.VerificationStatus
import org.matchlab.academic_net.feature.profile.domain.WorkDraftError
import org.matchlab.academic_net.feature.profile.ui.FlakySource
import org.matchlab.academic_net.feature.profile.ui.Me
import org.matchlab.academic_net.feature.profile.ui.TestGraph
import org.matchlab.academic_net.feature.profile.ui.installMainDispatcher
import org.matchlab.academic_net.feature.profile.ui.mockSource
import org.matchlab.academic_net.feature.profile.ui.uninstallMainDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class AddWorkViewModelTest {
    @BeforeTest
    fun setUp() = installMainDispatcher()

    @AfterTest
    fun tearDown() = uninstallMainDispatcher()

    private suspend fun myWorks(graph: TestGraph): List<PortfolioWork> {
        val result = graph.getUserWorks(Me)
        assertIs<LoadResult.Found<List<PortfolioWork>>>(result)
        return result.value
    }

    @Test
    fun scientificWorkIsTheDefaultAndNothingIsSentAtFirst() = runTest {
        val viewModel = AddWorkViewModel(TestGraph(mockSource()).addWork)

        val state = viewModel.uiState.value

        assertEquals(ProjectType.Scientific, state.draft.type)
        assertFalse(state.draft.sendsForVerification)
        assertTrue(state.errors.isEmpty())
    }

    @Test
    fun emptyFormShowsWhatIsRequired() = runTest {
        val viewModel = AddWorkViewModel(TestGraph(mockSource()).addWork)

        viewModel.onSubmitClick()

        val state = viewModel.uiState.value
        assertEquals(setOf(WorkDraftError.TitleEmpty, WorkDraftError.DescriptionEmpty), state.errors)
        assertFalse(state.isAdded)
        assertFalse(state.isSubmitting)
    }

    @Test
    fun errorsFollowTheEditingAfterTheFirstAttempt() = runTest {
        val viewModel = AddWorkViewModel(TestGraph(mockSource()).addWork)
        viewModel.onSubmitClick()

        viewModel.onTitleChange("Работа")
        assertEquals(setOf(WorkDraftError.DescriptionEmpty), viewModel.uiState.value.errors)

        viewModel.onDescriptionChange("Описание")
        viewModel.onLinkChange("not a link")
        assertEquals(setOf(WorkDraftError.LinkInvalid), viewModel.uiState.value.errors)

        viewModel.onLinkChange("https://github.com/me/repo")
        assertTrue(viewModel.uiState.value.errors.isEmpty())
    }

    @Test
    fun errorsAreNotShownBeforeTheFirstAttempt() = runTest {
        val viewModel = AddWorkViewModel(TestGraph(mockSource()).addWork)

        viewModel.onTitleChange("x".repeat(ProfileLimits.MaxTitleLength + 1))

        assertTrue(viewModel.uiState.value.errors.isEmpty())
    }

    @Test
    fun workWithoutLinkOrProofIsAddedAsUnconfirmed() = runTest {
        val graph = TestGraph(mockSource())
        val viewModel = AddWorkViewModel(graph.addWork)
        viewModel.onTitleChange("Заметки по курсу")
        viewModel.onDescriptionChange("Конспект")

        viewModel.onSubmitClick()

        assertTrue(viewModel.uiState.value.isAdded)
        val added = myWorks(graph).first()
        assertEquals("Заметки по курсу", added.title)
        assertEquals(VerificationStatus.Unconfirmed, added.status)
    }

    @Test
    fun workWithALinkGoesToModeration() = runTest {
        val graph = TestGraph(mockSource())
        val viewModel = AddWorkViewModel(graph.addWork)
        viewModel.onTypeSelect(ProjectType.Practical)
        viewModel.onTitleChange("Робот")
        viewModel.onDescriptionChange("Сборка")
        viewModel.onLinkChange("https://github.com/me/robot")
        assertTrue(viewModel.uiState.value.draft.sendsForVerification)

        viewModel.onSubmitClick()

        assertTrue(viewModel.uiState.value.isAdded)
        val added = myWorks(graph).first()
        assertEquals(ProjectType.Practical, added.type)
        assertEquals(VerificationStatus.Pending, added.status)
        assertEquals("GitHub", added.source)
        // It is in the verification queue of the profile.
        val queue = graph.observeMyProfile().first().verificationQueue
        assertTrue(queue.any { it.title == "Робот" && it.status == VerificationStatus.Pending })
    }

    @Test
    fun attachedProofCanBeRemoved() = runTest {
        val viewModel = AddWorkViewModel(TestGraph(mockSource()).addWork)

        viewModel.onProofChange(ProofAttachment("https://example.com/proof.pdf"))
        assertTrue(viewModel.uiState.value.draft.sendsForVerification)

        viewModel.onProofChange(null)
        assertNull(viewModel.uiState.value.draft.proof)
        assertFalse(viewModel.uiState.value.draft.sendsForVerification)
    }

    @Test
    fun invalidProofIsReported() = runTest {
        val viewModel = AddWorkViewModel(TestGraph(mockSource()).addWork)
        viewModel.onTitleChange("Работа")
        viewModel.onDescriptionChange("Описание")
        viewModel.onProofChange(ProofAttachment("file:///sdcard/proof.pdf"))

        viewModel.onSubmitClick()

        assertEquals(setOf(WorkDraftError.ProofInvalid), viewModel.uiState.value.errors)
    }

    @Test
    fun doubleTapSendsTheWorkOnce() = runTest {
        // With a latency the first request is still in flight when the second tap comes.
        val graph = TestGraph(mockSource(latencyMillis = 300))
        val worksBefore = myWorks(graph).size
        val viewModel = AddWorkViewModel(graph.addWork)
        viewModel.onTitleChange("Заметки по курсу")
        viewModel.onDescriptionChange("Конспект")

        viewModel.onSubmitClick()
        assertTrue(viewModel.uiState.value.isSubmitting)
        viewModel.onSubmitClick()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isAdded)
        val works = myWorks(graph)
        assertEquals(worksBefore + 1, works.size)
        assertEquals(1, works.count { it.title == "Заметки по курсу" })
    }

    @Test
    fun failedSendingKeepsTheFormAndAllowsAnotherTry() = runTest {
        val source = FlakySource(mockSource()).apply { failWrites = true }
        val viewModel = AddWorkViewModel(TestGraph(source).addWork)
        viewModel.onTitleChange("Работа")
        viewModel.onDescriptionChange("Описание")

        viewModel.onSubmitClick()

        val failed = viewModel.uiState.value
        assertTrue(failed.submitFailed)
        assertFalse(failed.isSubmitting)
        assertFalse(failed.isAdded)
        assertEquals("Работа", failed.draft.title)

        source.failWrites = false
        viewModel.onSubmitClick()

        assertTrue(viewModel.uiState.value.isAdded)
        assertFalse(viewModel.uiState.value.submitFailed)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AddDocumentViewModelTest {
    @BeforeTest
    fun setUp() = installMainDispatcher()

    @AfterTest
    fun tearDown() = uninstallMainDispatcher()

    private suspend fun myDocuments(graph: TestGraph): List<ProfileDocument> {
        val result = graph.getUserProfile(Me)
        assertIs<LoadResult.Found<UserProfile>>(result)
        return result.value.documents
    }

    @Test
    fun emptyFormShowsWhatIsRequiredIncludingTheProof() = runTest {
        val viewModel = AddDocumentViewModel(TestGraph(mockSource()).addDocument)

        viewModel.onSubmitClick()

        assertEquals(
            setOf(DocumentDraftError.TitleEmpty, DocumentDraftError.IssuerEmpty, DocumentDraftError.ProofMissing),
            viewModel.uiState.value.errors,
        )
    }

    @Test
    fun yearIsOptionalButMustBeAYear() = runTest {
        val viewModel = AddDocumentViewModel(TestGraph(mockSource()).addDocument)
        viewModel.onTitleChange("Диплом")
        viewModel.onIssuerChange("МГУ")
        viewModel.onProofChange(ProofAttachment("https://example.com/diploma.pdf"))
        viewModel.onYearChange("20x7")

        viewModel.onSubmitClick()
        assertEquals(setOf(DocumentDraftError.YearInvalid), viewModel.uiState.value.errors)

        viewModel.onYearChange("")
        assertTrue(viewModel.uiState.value.errors.isEmpty())
    }

    @Test
    fun documentIsAddedAndWaitsForTheModerator() = runTest {
        val graph = TestGraph(mockSource())
        val viewModel = AddDocumentViewModel(graph.addDocument)
        viewModel.onKindSelect(DocumentKind.Certificate)
        viewModel.onTitleChange("Сертификат Kotlin")
        viewModel.onIssuerChange("JetBrains")
        viewModel.onYearChange("2026")
        viewModel.onProofChange(ProofAttachment("https://example.com/c.pdf"))

        viewModel.onSubmitClick()

        assertTrue(viewModel.uiState.value.isAdded)
        val profile = graph.getUserProfile(Me)
        assertIs<LoadResult.Found<UserProfile>>(profile)
        val added = profile.value.documents.first { it.title == "Сертификат Kotlin" }
        assertEquals(DocumentKind.Certificate, added.kind)
        assertEquals(2026, added.year)
        assertEquals(VerificationStatus.Pending, added.status)
    }

    @Test
    fun doubleTapSendsTheDocumentOnce() = runTest {
        val graph = TestGraph(mockSource(latencyMillis = 300))
        val documentsBefore = myDocuments(graph).size
        val viewModel = AddDocumentViewModel(graph.addDocument)
        viewModel.onTitleChange("Сертификат Kotlin")
        viewModel.onIssuerChange("JetBrains")
        viewModel.onProofChange(ProofAttachment("https://example.com/c.pdf"))

        viewModel.onSubmitClick()
        assertTrue(viewModel.uiState.value.isSubmitting)
        viewModel.onSubmitClick()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isAdded)
        val documents = myDocuments(graph)
        assertEquals(documentsBefore + 1, documents.size)
        assertEquals(1, documents.count { it.title == "Сертификат Kotlin" })
    }

    @Test
    fun failedSendingKeepsTheForm() = runTest {
        val source = FlakySource(mockSource()).apply { failWrites = true }
        val viewModel = AddDocumentViewModel(TestGraph(source).addDocument)
        viewModel.onTitleChange("Диплом")
        viewModel.onIssuerChange("МГУ")
        viewModel.onProofChange(ProofAttachment("https://example.com/diploma.pdf"))

        viewModel.onSubmitClick()

        val state = viewModel.uiState.value
        assertTrue(state.submitFailed)
        assertFalse(state.isAdded)
        assertEquals("Диплом", state.draft.title)
    }
}
