package org.matchlab.academic_net.feature.profile.ui.my

import kotlin.test.assertIs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.Study
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.feature.profile.domain.ProfileDraftError
import org.matchlab.academic_net.feature.profile.domain.UploadFile
import org.matchlab.academic_net.feature.profile.domain.usecase.ChangeAvatarUseCase
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

    private fun viewModel() = EditProfileViewModel(
        ObserveMyProfileUseCase(repository),
        UpdateProfileUseCase(repository),
        ChangeAvatarUseCase(repository),
    )

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
    fun theYearTakesDigitsOnlyAndHasNoUpperLimit() {
        val viewModel = viewModel()

        viewModel.onStudyYearChange("a3b")
        assertEquals("3", viewModel.uiState.value.draft.studyYear)

        viewModel.onStudyYearChange("12345")
        assertEquals("12345", viewModel.uiState.value.draft.studyYear, "how large the year may be is the server's to judge")

        viewModel.onStudyYearChange("")
        assertEquals("", viewModel.uiState.value.draft.studyYear)
    }

    @Test
    fun aYearOfSevenOrTenIsSaved() {
        for (year in listOf("7", "10")) {
            val viewModel = viewModel()
            viewModel.onStudyYearChange(year)

            viewModel.onSaveClick()

            assertEquals(emptySet(), viewModel.uiState.value.errors, year)
            assertEquals(year, repository.updatedProfiles.last().studyYear, year)
        }
    }

    @Test
    fun aYearOfZeroIsAnError() {
        val viewModel = viewModel()
        viewModel.onStudyYearChange("0")

        viewModel.onSaveClick()

        assertEquals(setOf(ProfileDraftError.StudyYearInvalid), viewModel.uiState.value.errors)
        assertEquals(emptyList(), repository.updatedProfiles)
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
        viewModel.onStudyYearChange("0")

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

    // ---- the form while it is saved ----

    /**
     * Every edit the screen has, each with a value that differs from what the form of [myProfile] holds (the name, the
     * university, the study, the field, the text, the interest "ML", the skill "Kotlin", no type of interest).
     */
    private fun EditProfileViewModel.editEverything() {
        onNameChange("Другое имя")
        onUniversityChange("МГУ")
        onStudyLevelChange("Аспирантура")
        onStudyYearChange("2")
        onFieldChange("Biology")
        onAboutChange("Другой текст")
        onInterestTypeToggle(ProjectType.Scientific)
        onAddInterest("NLP")
        onRemoveInterest("ML")
        onAddSkill("Rust")
        onRemoveSkill("Kotlin")
    }

    @Test
    fun editsWhileTheFormIsBeingSavedChangeNothing() {
        val gate = CompletableDeferred<Unit>()
        repository.onUpdateProfile = { gate.await(); repository.profile.value }
        val viewModel = viewModel()
        val inTheSave = viewModel.uiState.value.draft

        viewModel.onSaveClick()
        assertTrue(viewModel.uiState.value.isSaving)
        viewModel.editEverything()

        val busy = viewModel.uiState.value
        assertEquals(inTheSave, busy.draft, "the save has its own copy of the form: the screen must not show more")
        assertEquals(emptySet(), busy.errors, "an ignored edit cannot bring a problem of the form up")
        assertTrue(busy.isSaving)
        gate.complete(Unit)
        assertEquals(inTheSave, viewModel.uiState.value.draft)
        assertEquals(inTheSave.normalized(), repository.updatedProfiles.single(), "what is shown is what was sent")
    }

    @Test
    fun anEditThatWouldBreakTheFormIsIgnoredWhileItIsBeingSaved() {
        val gate = CompletableDeferred<Unit>()
        repository.onUpdateProfile = { gate.await(); repository.profile.value }
        val viewModel = viewModel()
        viewModel.onSaveClick() // showErrors is on from now

        viewModel.onNameChange("")
        viewModel.onStudyYearChange("9")

        val busy = viewModel.uiState.value
        assertEquals("Арджун Мехта", busy.draft.name)
        assertEquals("3", busy.draft.studyYear)
        assertEquals(emptySet(), busy.errors)
        gate.complete(Unit)
        assertEquals("Арджун Мехта", repository.updatedProfiles.single().name)
    }

    @Test
    fun editsAfterASuccessfulSaveChangeNothingWhileTheScreenCloses() {
        val viewModel = viewModel()
        viewModel.onNameChange("Сохранённое имя")
        viewModel.onSaveClick()
        val saved = viewModel.uiState.value
        assertTrue(saved.isSaved)
        assertTrue(saved.isSaving, "the form stays busy until it has faded out")

        viewModel.editEverything()

        val after = viewModel.uiState.value
        assertEquals(saved.draft, after.draft)
        assertEquals("Сохранённое имя", after.draft.name)
        assertEquals(saved, after, "nothing of the state moves once the changes are saved")
        assertEquals("Сохранённое имя", repository.updatedProfiles.single().name)
    }

    @Test
    fun afterAFailedSaveTheEditsWorkAgainAndWhatWasIgnoredStaysIgnored() {
        val gate = CompletableDeferred<Unit>()
        repository.onUpdateProfile = { gate.await(); throw IllegalStateException("no network") }
        val viewModel = viewModel()
        val inTheSave = viewModel.uiState.value.draft
        viewModel.onSaveClick()
        viewModel.editEverything() // ignored: the form is busy
        gate.complete(Unit)
        with(viewModel.uiState.value) {
            assertFalse(isSaving)
            assertTrue(saveFailed)
            assertEquals(inTheSave, draft, "the failed save leaves the form as the save saw it")
        }

        viewModel.onNameChange("Новое имя")
        viewModel.onAddSkill("Rust")
        viewModel.onInterestTypeToggle(ProjectType.Practical)

        with(viewModel.uiState.value) {
            assertEquals("Новое имя", draft.name)
            assertEquals(listOf("Kotlin", "Rust"), draft.skills)
            assertEquals(setOf(ProjectType.Practical), draft.interestTypes)
            assertEquals(inTheSave.university, draft.university, "the ignored edit of the university did not come back")
            assertFalse(saveFailed, "an edit hides the failure")
        }

        // The next save sends what was typed after the failure, nothing of what was ignored.
        repository.onUpdateProfile = { repository.profile.value }
        viewModel.onSaveClick()
        val sent = repository.updatedProfiles.last()
        assertEquals(2, repository.updatedProfiles.size)
        assertEquals("Новое имя", sent.name)
        assertEquals(listOf("Kotlin", "Rust"), sent.skills)
        assertEquals(setOf(ProjectType.Practical), sent.interestTypes)
        assertEquals(inTheSave.university, sent.university)
        assertEquals(inTheSave.about, sent.about)
        assertTrue(viewModel.uiState.value.isSaved)
    }

    @Test
    fun anInvalidFormThatIsNotSentLeavesTheFormOpenForEdits() {
        val viewModel = viewModel()
        viewModel.onNameChange("")
        viewModel.onSaveClick()
        assertEquals(setOf(ProfileDraftError.NameEmpty), viewModel.uiState.value.errors)
        assertFalse(viewModel.uiState.value.isSaving, "nothing was sent: the form is not busy")

        viewModel.onNameChange("Имя")
        viewModel.onAddSkill("Rust")

        with(viewModel.uiState.value) {
            assertEquals("Имя", draft.name)
            assertEquals(listOf("Kotlin", "Rust"), draft.skills)
            assertEquals(emptySet(), errors)
        }
    }

    // ---- the photo ----

    private val photo = UploadFile(byteArrayOf(1, 2), "image/jpeg", "avatar.jpg")

    @Test
    fun theAvatarKnowsWhoseItIsAndAnAccountWithAProfileCanChangeIt() {
        repository.profile.value = myProfile(person = person(name = "Арджун"))

        val avatar = viewModel().uiState.value.avatar

        assertTrue(avatar.canChange)
        assertEquals(repository.profile.value.profile.person.user.id.value, avatar.userKey)
        assertFalse(avatar.isUploading)
        assertNull(avatar.failure)
    }

    @Test
    fun anAccountWithoutANameCannotChangeTheAvatarYet() {
        repository.profile.value = myProfile(person = person(name = ""))
        val viewModel = viewModel()

        assertFalse(viewModel.uiState.value.avatar.canChange)
        viewModel.onAvatarChosen(photo)

        assertTrue(repository.changedAvatars.isEmpty())
    }

    @Test
    fun aChosenPhotoIsUploadedOnceAndTheControlIsFreeAgainAfterIt() = runTest {
        val gate = CompletableDeferred<Unit>()
        repository.onChangeAvatar = { gate.await() }
        val viewModel = viewModel()

        viewModel.onAvatarChosen(photo)
        viewModel.onAvatarChosen(photo)
        assertTrue(viewModel.uiState.value.avatar.isUploading)
        assertEquals(photo, viewModel.uiState.value.avatar.chosen, "the photo is shown while it is uploaded")
        gate.complete(Unit)

        assertEquals(1, repository.changedAvatars.size, "a second choice while one is on its way does nothing")
        assertFalse(viewModel.uiState.value.avatar.isUploading)
        assertNull(viewModel.uiState.value.avatar.failure)
        assertEquals(photo, viewModel.uiState.value.avatar.chosen, "the photo stays on the screen after it is saved")
    }

    @Test
    fun aFailedChangeShowsItsCauseAndTheNextTryClearsIt() {
        repository.onChangeAvatar = { throw RemoteFailure.NoConnection() }
        val viewModel = viewModel()

        viewModel.onAvatarChosen(photo)

        assertIs<RemoteFailure.NoConnection>(viewModel.uiState.value.avatar.failure)
        assertFalse(viewModel.uiState.value.avatar.isUploading)
        assertNull(viewModel.uiState.value.avatar.chosen, "a photo that was not saved is not shown: the old one stays")

        repository.onChangeAvatar = {}
        viewModel.onAvatarChosen(photo)

        assertNull(viewModel.uiState.value.avatar.failure)
    }

    @Test
    fun aPhotoThatCouldNotBeReadIsAFailureToo() {
        val viewModel = viewModel()

        viewModel.onAvatarUnreadable(IllegalStateException("not an image"))

        assertIs<IllegalStateException>(viewModel.uiState.value.avatar.failure)
        assertTrue(repository.changedAvatars.isEmpty())
    }
}
