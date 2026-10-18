package org.matchlab.academic_net.feature.profile.ui.my

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.result.LoadResult
import org.matchlab.academic_net.feature.profile.domain.AboutDraftError
import org.matchlab.academic_net.feature.profile.domain.ProfileLimits
import org.matchlab.academic_net.feature.profile.domain.ProofAttachment
import org.matchlab.academic_net.feature.profile.domain.UserProfile
import org.matchlab.academic_net.feature.profile.ui.FlakySource
import org.matchlab.academic_net.feature.profile.ui.Me
import org.matchlab.academic_net.feature.profile.ui.TestGraph
import org.matchlab.academic_net.feature.profile.ui.installMainDispatcher
import org.matchlab.academic_net.feature.profile.ui.mockSource
import org.matchlab.academic_net.feature.profile.ui.uninstallMainDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class EditProfileViewModelTest {
    @BeforeTest
    fun setUp() = installMainDispatcher()

    @AfterTest
    fun tearDown() = uninstallMainDispatcher()

    private fun viewModel(graph: TestGraph) =
        EditProfileViewModel(graph.observeMyProfile, graph.getUserWorks, graph.updateAbout)

    @Test
    fun draftComesFromTheProfile() = runTest {
        val viewModel = viewModel(TestGraph(mockSource()))

        val state = viewModel.uiState.value

        assertFalse(state.isLoading)
        assertFalse(state.loadFailed)
        assertTrue(state.draft.about.startsWith("Занимаюсь компьютерным зрением"))
        assertEquals(setOf(ProjectType.Scientific), state.draft.interestTypes)
        assertEquals(listOf("Computer Vision", "Edge AI", "Робототехника"), state.draft.interests)
        assertEquals(listOf("Python", "TensorFlow", "PyTorch", "OpenCV"), state.draft.skills)
    }

    @Test
    fun listsAllDocumentsAndAllWorksNotOnlyThePreview() = runTest {
        val viewModel = viewModel(TestGraph(mockSource()))

        val state = viewModel.uiState.value

        assertEquals(2, state.documents.size)
        // The profile previews three works; the list has all four.
        assertEquals(4, state.works.size)
    }

    @Test
    fun editsChangeTheDraft() = runTest {
        val viewModel = viewModel(TestGraph(mockSource()))

        viewModel.onAboutChange("Новый текст")
        viewModel.onInterestTypeToggle(ProjectType.Practical)
        viewModel.onInterestTypeToggle(ProjectType.Scientific)
        viewModel.onAddInterest("Kotlin")
        viewModel.onRemoveInterest("Edge AI")
        viewModel.onAddSkill("Compose")
        viewModel.onAddSkill("compose")
        viewModel.onRemoveSkill("Python")

        val draft = viewModel.uiState.value.draft
        assertEquals("Новый текст", draft.about)
        assertEquals(setOf(ProjectType.Practical), draft.interestTypes)
        assertEquals(listOf("Computer Vision", "Робототехника", "Kotlin"), draft.interests)
        assertEquals(listOf("TensorFlow", "PyTorch", "OpenCV", "Compose"), draft.skills)
    }

    @Test
    fun savingStoresTheDraftAndClosesTheScreen() = runTest {
        val graph = TestGraph(mockSource())
        val viewModel = viewModel(graph)
        viewModel.onAboutChange("  Теперь про нейросети  ")
        viewModel.onAddSkill("Kotlin")

        viewModel.onSaveClick()

        val state = viewModel.uiState.value
        assertTrue(state.isSaved)
        assertFalse(state.isSaving)
        val result = graph.getUserProfile(Me)
        assertIs<LoadResult.Found<UserProfile>>(result)
        assertEquals("Теперь про нейросети", result.value.about)
        assertTrue("Kotlin" in result.value.skills)
    }

    @Test
    fun tooLongAboutIsNotSavedAndTheErrorFollowsTheEditing() = runTest {
        val viewModel = viewModel(TestGraph(mockSource()))
        viewModel.onAboutChange("а".repeat(ProfileLimits.MaxAboutLength + 1))

        viewModel.onSaveClick()

        assertEquals(setOf(AboutDraftError.AboutTooLong), viewModel.uiState.value.errors)
        assertFalse(viewModel.uiState.value.isSaved)

        viewModel.onAboutChange("Коротко")

        assertTrue(viewModel.uiState.value.errors.isEmpty())
    }

    @Test
    fun tooManyTagsAreReportedPerField() = runTest {
        val viewModel = viewModel(TestGraph(mockSource()))
        repeat(ProfileLimits.MaxTags + 1) { viewModel.onAddSkill("skill $it") }

        viewModel.onSaveClick()

        assertTrue(AboutDraftError.TooManySkills in viewModel.uiState.value.errors)
        assertFalse(AboutDraftError.TooManyInterests in viewModel.uiState.value.errors)
    }

    @Test
    fun failedSavingKeepsTheDraftAndSaysSo() = runTest {
        val source = FlakySource(mockSource())
        val viewModel = viewModel(TestGraph(source))
        viewModel.onAboutChange("Текст")
        source.failWrites = true

        viewModel.onSaveClick()

        val state = viewModel.uiState.value
        assertTrue(state.saveFailed)
        assertFalse(state.isSaving)
        assertFalse(state.isSaved)
        assertEquals("Текст", state.draft.about)

        // Typing again withdraws the message; the next attempt goes through.
        viewModel.onAboutChange("Текст!")
        assertFalse(viewModel.uiState.value.saveFailed)
        source.failWrites = false
        viewModel.onSaveClick()
        assertTrue(viewModel.uiState.value.isSaved)
    }

    @Test
    fun profileThatCannotBeLoadedOffersARetry() = runTest {
        val source = FlakySource(mockSource()).apply { failReads = true }
        val viewModel = viewModel(TestGraph(source))
        assertTrue(viewModel.uiState.value.loadFailed)
        assertFalse(viewModel.uiState.value.isLoading)

        source.failReads = false
        viewModel.onRetry()

        val state = viewModel.uiState.value
        assertFalse(state.loadFailed)
        assertFalse(state.isLoading)
        assertTrue(state.draft.about.isNotEmpty())
    }

    @Test
    fun addedWorkAppearsInTheListAndTheDraftIsKept() = runTest {
        val graph = TestGraph(mockSource())
        val viewModel = viewModel(graph)
        viewModel.onAboutChange("Мой незаконченный текст")
        val addWork = AddWorkViewModel(graph.addWork)
        addWork.onTitleChange("Новая работа")
        addWork.onDescriptionChange("Описание")

        addWork.onSubmitClick()

        val state = viewModel.uiState.value
        assertEquals(5, state.works.size)
        assertEquals("Новая работа", state.works.first().title)
        assertEquals("Мой незаконченный текст", state.draft.about)
    }

    @Test
    fun addedDocumentAppearsInTheList() = runTest {
        val graph = TestGraph(mockSource())
        val viewModel = viewModel(graph)
        val addDocument = AddDocumentViewModel(graph.addDocument)
        addDocument.onTitleChange("Сертификат Kotlin")
        addDocument.onIssuerChange("JetBrains")
        addDocument.onProofChange(ProofAttachment("https://example.com/c.pdf"))

        addDocument.onSubmitClick()

        assertEquals(3, viewModel.uiState.value.documents.size)
        assertTrue(viewModel.uiState.value.documents.any { it.title == "Сертификат Kotlin" })
    }

    @Test
    fun doubleTapSavesOnce() = runTest {
        // With a latency the first request is still in flight when the second tap comes.
        val source = FlakySource(mockSource(latencyMillis = 300))
        val viewModel = viewModel(TestGraph(source))
        advanceUntilIdle()
        viewModel.onAboutChange("Новый текст")

        viewModel.onSaveClick()
        assertTrue(viewModel.uiState.value.isSaving)
        viewModel.onSaveClick()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaved)
        assertEquals(1, source.aboutUpdates)
    }

    @Test
    fun tapOnTheSavedFormSavesNothingMore() = runTest {
        // The form stays on the screen while it fades out; even new typing in that time is not sent.
        val source = FlakySource(mockSource())
        val viewModel = viewModel(TestGraph(source))
        viewModel.onAboutChange("Новый текст")
        viewModel.onSaveClick()
        assertTrue(viewModel.uiState.value.isSaved)

        viewModel.onAboutChange("Ещё правка")
        viewModel.onSaveClick()

        assertEquals(1, source.aboutUpdates)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun saveWhileTheProfileIsLoadingSendsNothing() = runTest {
        val source = FlakySource(mockSource(latencyMillis = 300))
        val viewModel = viewModel(TestGraph(source))
        assertTrue(viewModel.uiState.value.isLoading)

        viewModel.onSaveClick()

        assertEquals(0, source.aboutUpdates)
        assertFalse(viewModel.uiState.value.isSaving)
    }
}
