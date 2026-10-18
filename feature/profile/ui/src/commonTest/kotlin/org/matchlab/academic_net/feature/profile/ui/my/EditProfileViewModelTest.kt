package org.matchlab.academic_net.feature.profile.ui.my

import kotlinx.coroutines.CompletableDeferred
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.Study
import org.matchlab.academic_net.feature.profile.domain.ProfileDraftError
import org.matchlab.academic_net.feature.profile.domain.ProfileLimits
import org.matchlab.academic_net.feature.profile.domain.usecase.ObserveMyProfileUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.UpdateProfileUseCase
import org.matchlab.academic_net.feature.profile.ui.FakeMyProfileRepository
import org.matchlab.academic_net.feature.profile.ui.document
import org.matchlab.academic_net.feature.profile.ui.installMainDispatcher
import org.matchlab.academic_net.feature.profile.ui.myProfile
import org.matchlab.academic_net.feature.profile.ui.person
import org.matchlab.academic_net.feature.profile.ui.uninstallMainDispatcher
import org.matchlab.academic_net.feature.profile.ui.work
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * "10b Редактирование профиля": the form starts from the profile once, validation shows after the first "Сохранить", the
 * save runs once, and the lists of works and documents follow the profile.
 */
class EditProfileViewModelTest {
    @BeforeTest
    fun setUp() = installMainDispatcher()

    @AfterTest
    fun tearDown() = uninstallMainDispatcher()

    private val repository = FakeMyProfileRepository()

    private fun viewModel() = EditProfileViewModel(ObserveMyProfileUseCase(repository), UpdateProfileUseCase(repository))

    // ---- starting ----

    @Test
    fun theFormStartsWithWhatTheProfileHas() {
        repository.profile.value = myProfile(
            person = person(name = "Арджун", study = Study(level = "Магистратура", year = 2)),
            works = listOf(work("w1")),
            documents = listOf(document("d1")),
        )

        val state = viewModel().uiState.value

        assertFalse(state.isLoading)
        assertNull(state.loadFailure)
        assertEquals("Арджун", state.draft.name)
        assertEquals("Магистратура", state.draft.studyLevel)
        assertEquals("2", state.draft.studyYear)
        assertEquals("Люблю данные", state.draft.about)
        assertEquals(listOf("w1"), state.works.map { it.id.value })
        assertEquals(listOf("d1"), state.documents.map { it.id.value })
    }

    @Test
    fun aNewAccountStartsWithAnEmptyFormAndNoLoadFailure() {
        repository.profile.value = myProfile(person = person(name = "", study = Study()))

        val state = viewModel().uiState.value

        assertEquals("", state.draft.name)
        assertEquals("", state.draft.studyLevel)
        assertNull(state.loadFailure)
    }

    @Test
    fun aFailureToLoadIsToldAndRetryObservesAgain() {
        val cause = IllegalStateException("no network")
        repository.observeFailure = cause
        val viewModel = viewModel()

        assertEquals(cause, viewModel.uiState.value.loadFailure)
        assertFalse(viewModel.uiState.value.isLoading)

        repository.observeFailure = null
        viewModel.onRetry()

        assertNull(viewModel.uiState.value.loadFailure)
        assertEquals("Арджун Мехта", viewModel.uiState.value.draft.name)
    }

    @Test
    fun nothingIsSavedWhileTheProfileIsNotThere() {
        repository.observeFailure = IllegalStateException("no network")
        val viewModel = viewModel()

        viewModel.onSaveClick()

        assertEquals(emptyList(), repository.updatedProfiles)
    }

    @Test
    fun theTypingIsNotOverwrittenWhenTheProfileChanges() {
        val viewModel = viewModel()
        viewModel.onNameChange("Моё новое имя")

        // A work was added on its own screen: the profile emits again.
        repository.profile.value = myProfile(person = person(name = "Другое имя"), works = listOf(work("w1")))

        val state = viewModel.uiState.value
        assertEquals("Моё новое имя", state.draft.name)
        assertEquals(listOf("w1"), state.works.map { it.id.value }, "the lists always follow the profile")
    }

    // ---- editing ----

    @Test
    fun everyFieldIsEdited() {
        val viewModel = viewModel()

        viewModel.onNameChange("Имя")
        viewModel.onUniversityChange("МГУ")
        viewModel.onStudyLevelChange("Аспирантура")
        viewModel.onStudyYearChange("2")
        viewModel.onFieldChange("Biology")
        viewModel.onAboutChange("Текст")
        viewModel.onAddInterest("NLP")
        viewModel.onAddSkill("Rust")

        val draft = viewModel.uiState.value.draft
        assertEquals("Имя", draft.name)
        assertEquals("МГУ", draft.university)
        assertEquals("Аспирантура", draft.studyLevel)
        assertEquals("2", draft.studyYear)
        assertEquals("Biology", draft.field)
        assertEquals("Текст", draft.about)
        assertEquals(listOf("ML", "NLP"), draft.interests)
        assertEquals(listOf("Kotlin", "Rust"), draft.skills)
    }

    @Test
    fun theYearTakesDigitsOnlyAndNoMoreThanTheLargestYearHas() {
        val viewModel = viewModel()

        viewModel.onStudyYearChange("a3b")
        assertEquals("3", viewModel.uiState.value.draft.studyYear)

        viewModel.onStudyYearChange("12345")
        assertEquals("1", viewModel.uiState.value.draft.studyYear, "one digit: the year is 1..${ProfileLimits.MaxStudyYear}")

        viewModel.onStudyYearChange("")
        assertEquals("", viewModel.uiState.value.draft.studyYear)
    }

    @Test
    fun tagsAreAddedOnceAndRemovedIgnoringCase() {
        val viewModel = viewModel()

        viewModel.onAddInterest("  ml ")
        viewModel.onAddInterest("")
        viewModel.onAddSkill("Rust")
        viewModel.onAddSkill("rust")
        viewModel.onRemoveSkill("KOTLIN")
        viewModel.onRemoveInterest("ml")

        assertEquals(emptyList(), viewModel.uiState.value.draft.interests)
        assertEquals(listOf("Rust"), viewModel.uiState.value.draft.skills)
    }

    @Test
    fun theTypeOfInterestIsToggled() {
        val viewModel = viewModel()

        viewModel.onInterestTypeToggle(ProjectType.Scientific)
        assertEquals(setOf(ProjectType.Scientific), viewModel.uiState.value.draft.interestTypes)
        viewModel.onInterestTypeToggle(ProjectType.Practical)
        viewModel.onInterestTypeToggle(ProjectType.Scientific)

        assertEquals(setOf(ProjectType.Practical), viewModel.uiState.value.draft.interestTypes)
    }

    // ---- validation ----

    @Test
    fun anInvalidFormShowsItsErrorsAndSendsNothing() {
        val viewModel = viewModel()
        viewModel.onNameChange("   ")
        viewModel.onStudyYearChange("9")

        viewModel.onSaveClick()

        val state = viewModel.uiState.value
        assertEquals(setOf(ProfileDraftError.NameEmpty, ProfileDraftError.StudyYearInvalid), state.errors)
        assertFalse(state.isSaving)
        assertEquals(emptyList(), repository.updatedProfiles)
    }

    @Test
    fun errorsAreNotShownBeforeTheFirstAttemptToSave() {
        val viewModel = viewModel()

        viewModel.onNameChange("")

        assertEquals(emptySet(), viewModel.uiState.value.errors)
    }

    @Test
    fun afterTheFirstAttemptTheErrorsFollowTheTyping() {
        val viewModel = viewModel()
        viewModel.onNameChange("")
        viewModel.onSaveClick()
        assertEquals(setOf(ProfileDraftError.NameEmpty), viewModel.uiState.value.errors)

        viewModel.onNameChange("Имя")

        assertEquals(emptySet(), viewModel.uiState.value.errors)
    }

    // ---- saving ----

    @Test
    fun aValidFormIsSavedAndTheScreenCloses() {
        val viewModel = viewModel()
        viewModel.onNameChange("  Новое   имя ")

        viewModel.onSaveClick()

        val state = viewModel.uiState.value
        assertTrue(state.isSaved)
        assertEquals("Новое имя", repository.updatedProfiles.single().name, "the use case sends the normalized form")
        assertEquals(emptySet(), state.errors)
        assertFalse(state.saveFailed)
    }

    @Test
    fun aDoubleTapSavesOnce() {
        val gate = CompletableDeferred<Unit>()
        repository.onUpdateProfile = { gate.await(); repository.profile.value }
        val viewModel = viewModel()

        viewModel.onSaveClick()
        assertTrue(viewModel.uiState.value.isSaving)
        viewModel.onSaveClick()
        viewModel.onSaveClick()
        gate.complete(Unit)

        assertEquals(1, repository.updatedProfiles.size)
    }

    @Test
    fun afterTheSaveSucceededNothingIsSavedAgainWhileTheScreenCloses() {
        val viewModel = viewModel()
        viewModel.onSaveClick()
        assertTrue(viewModel.uiState.value.isSaved)

        viewModel.onSaveClick()

        assertEquals(1, repository.updatedProfiles.size)
        assertTrue(viewModel.uiState.value.isSaving, "the form stays busy until it has faded out")
    }

    @Test
    fun aFailedSaveIsToldAndTheFormCanBeSavedAgain() {
        repository.onUpdateProfile = { throw IllegalStateException("no network") }
        val viewModel = viewModel()

        viewModel.onSaveClick()

        val failed = viewModel.uiState.value
        assertTrue(failed.saveFailed)
        assertFalse(failed.isSaving)
        assertFalse(failed.isSaved)

        repository.onUpdateProfile = { repository.profile.value }
        viewModel.onSaveClick()

        assertTrue(viewModel.uiState.value.isSaved)
        assertFalse(viewModel.uiState.value.saveFailed)
        assertEquals(2, repository.updatedProfiles.size)
    }

    @Test
    fun editingAfterAFailureHidesTheFailure() {
        repository.onUpdateProfile = { throw IllegalStateException("no network") }
        val viewModel = viewModel()
        viewModel.onSaveClick()
        assertTrue(viewModel.uiState.value.saveFailed)

        viewModel.onAboutChange("Другой текст")

        assertFalse(viewModel.uiState.value.saveFailed)
    }

    @Test
    fun theFormKeepsWhatWasTypedWhenTheSaveFails() {
        repository.onUpdateProfile = { throw IllegalStateException("no network") }
        val viewModel = viewModel()
        viewModel.onAboutChange("Длинный текст о себе")

        viewModel.onSaveClick()

        assertEquals("Длинный текст о себе", viewModel.uiState.value.draft.about)
    }

    @Test
    fun theServerSeesTheStudyLevelAsTypedAndTheYearAsANumberStringForTheUseCase() {
        val viewModel = viewModel()
        viewModel.onStudyLevelChange("  аспирантура ")
        viewModel.onStudyYearChange("3")

        viewModel.onSaveClick()

        val sent = assertNotNull(repository.updatedProfiles.singleOrNull())
        assertEquals("аспирантура", sent.studyLevel)
        assertEquals(3, sent.studyYearNumber)
    }
}
