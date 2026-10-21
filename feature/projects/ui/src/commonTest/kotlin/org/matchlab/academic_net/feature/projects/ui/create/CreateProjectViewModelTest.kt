package org.matchlab.academic_net.feature.projects.ui.create

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.YearMonth
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectPeriod
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.Sphere
import org.matchlab.academic_net.common.result.FeatureUnavailableException
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.feature.projects.domain.ProjectDraftError
import org.matchlab.academic_net.feature.projects.domain.RoleDraft
import org.matchlab.academic_net.feature.projects.domain.RoleDraftError
import org.matchlab.academic_net.feature.projects.domain.usecase.GetSpheresUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.PublishProjectUseCase
import org.matchlab.academic_net.feature.projects.ui.FakeProjectsRepository
import org.matchlab.academic_net.feature.projects.ui.Gate
import org.matchlab.academic_net.feature.projects.ui.TestSphere
import org.matchlab.academic_net.feature.projects.ui.installMainDispatcher
import org.matchlab.academic_net.feature.projects.ui.project
import org.matchlab.academic_net.feature.projects.ui.uninstallMainDispatcher

/**
 * "05 Создание проекта": the form, its checks, publishing (the success, the failures that keep what was typed, the guard
 * against a second tap) and what the screen says if a source cannot create projects at all.
 */
class CreateProjectViewModelTest {
    @BeforeTest
    fun setUp() = installMainDispatcher()

    @AfterTest
    fun tearDown() = uninstallMainDispatcher()

    private val repository = FakeProjectsRepository()
    private val cause = IllegalStateException("offline")

    private fun viewModel() = CreateProjectViewModel(PublishProjectUseCase(repository), GetSpheresUseCase(repository))

    private val CreateProjectViewModel.state get() = uiState.value

    private fun CreateProjectViewModel.fillValidForm() {
        onTitleChange("AI-навигация")
        onTypeSelect(ProjectType.Scientific)
        onSphereSelect(TestSphere)
        onProblemChange("GPS не работает")
        onGoalChange("Маршруты")
        onStartFieldClick()
        onMonthSelect(YearMonth(2026, 10))
        onEndFieldClick()
        onMonthSelect(YearMonth(2027, 3))
    }

    // ---- the checks ----

    @Test
    fun theFormStartsEmptyWithoutErrors() {
        val viewModel = viewModel()

        with(viewModel.state) {
            assertEquals(emptySet(), errors)
            assertFalse(isPublishing)
            assertNull(publishedProjectId)
            assertFalse(publishFailed)
        }
    }

    @Test
    fun publishingAnEmptyFormShowsEveryMissingFieldAndSendsNothing() {
        val viewModel = viewModel()

        viewModel.onPublishClick()

        assertEquals(
            setOf(
                ProjectDraftError.TitleEmpty,
                ProjectDraftError.TypeMissing,
                ProjectDraftError.SphereMissing,
                ProjectDraftError.ProblemEmpty,
                ProjectDraftError.GoalEmpty,
                ProjectDraftError.StartMissing,
                ProjectDraftError.EndMissing,
            ),
            viewModel.state.errors,
        )
        assertFalse(viewModel.state.isPublishing)
        assertEquals(emptyList(), repository.published)
    }

    @Test
    fun theErrorsGoAsTheUserFixesThemButOnlyAfterTheFirstAttempt() {
        val viewModel = viewModel()
        viewModel.onTitleChange("A")
        assertEquals(emptySet(), viewModel.state.errors, "no errors before the first attempt")
        viewModel.onPublishClick()
        assertTrue(ProjectDraftError.TitleEmpty !in viewModel.state.errors)
        assertTrue(ProjectDraftError.GoalEmpty in viewModel.state.errors)

        viewModel.onGoalChange("Цель")
        viewModel.onTypeSelect(ProjectType.Practical)

        assertTrue(ProjectDraftError.GoalEmpty !in viewModel.state.errors)
        assertTrue(ProjectDraftError.TypeMissing !in viewModel.state.errors)
        assertTrue(ProjectDraftError.ProblemEmpty in viewModel.state.errors)
    }

    @Test
    fun aProjectCannotEndBeforeItStarts() {
        val viewModel = viewModel()
        viewModel.fillValidForm()
        viewModel.onEndFieldClick()
        viewModel.onMonthSelect(YearMonth(2026, 9))

        viewModel.onPublishClick()

        assertEquals(setOf(ProjectDraftError.EndBeforeStart), viewModel.state.errors)
        assertEquals(emptyList(), repository.published)
        viewModel.onEndFieldClick()
        viewModel.onMonthSelect(YearMonth(2026, 10))
        assertEquals(emptySet(), viewModel.state.errors, "ending in the month it starts is fine")
    }

    @Test
    fun aMonthIsChosenForTheFieldThatOpenedThePicker() {
        val viewModel = viewModel()

        viewModel.onStartFieldClick()
        assertEquals(DateField.Start, viewModel.state.monthPickerFor)
        viewModel.onMonthSelect(YearMonth(2026, 10))
        viewModel.onEndFieldClick()
        assertEquals(DateField.End, viewModel.state.monthPickerFor)
        viewModel.onMonthSelect(YearMonth(2027, 3))

        with(viewModel.state) {
            assertEquals(YearMonth(2026, 10), draft.start)
            assertEquals(YearMonth(2027, 3), draft.end)
            assertNull(monthPickerFor)
        }
    }

    @Test
    fun aMonthChosenWithoutAPickerChangesNothing() {
        val viewModel = viewModel()

        viewModel.onMonthSelect(YearMonth(2026, 10))

        assertNull(viewModel.state.draft.start)
        assertNull(viewModel.state.draft.end)
    }

    @Test
    fun closingTheMonthPickerKeepsTheDraft() {
        val viewModel = viewModel()
        viewModel.onStartFieldClick()

        viewModel.onMonthPickerDismiss()

        assertNull(viewModel.state.monthPickerFor)
        assertNull(viewModel.state.draft.start)
    }

    // ---- a source that cannot create projects ----

    @Test
    fun aSourceThatCannotCreateIsNotAFailureToTryAgain() = runTest {
        repository.publishFailure = FeatureUnavailableException("G40")
        val viewModel = viewModel()
        viewModel.fillValidForm()

        viewModel.onPublishClick()

        with(viewModel.state) {
            assertTrue(publishUnavailable)
            assertTrue(publishFailed)
            assertFalse(isPublishing)
            assertNull(publishedProjectId)
        }
        assertEquals(1, repository.published.size)
    }

    @Test
    fun anyOtherFailureIsAFailureToTryAgain() {
        repository.publishFailure = cause
        val viewModel = viewModel()
        viewModel.fillValidForm()

        viewModel.onPublishClick()

        with(viewModel.state) {
            assertSame(cause, publishFailure)
            assertTrue(publishFailed)
            assertFalse(publishUnavailable)
            assertFalse(isPublishing)
        }
    }

    @Test
    fun editingTheFormClearsTheFailureAndTheNextAttemptSendsAgain() {
        repository.publishFailure = cause
        val viewModel = viewModel()
        viewModel.fillValidForm()
        viewModel.onPublishClick()

        viewModel.onTitleChange("AI-навигация 2")
        assertFalse(viewModel.state.publishFailed)
        repository.publishFailure = null
        viewModel.onPublishClick()

        assertEquals(2, repository.published.size)
        assertEquals(ProjectId("new"), viewModel.state.publishedProjectId)
    }

    // ---- publishing ----

    @Test
    fun aValidFormIsSentNormalizedAndTheNewProjectIsRemembered() {
        repository.publishFailure = null
        repository.publishAnswer = project("created")
        val viewModel = viewModel()
        viewModel.fillValidForm()
        viewModel.onTitleChange("  AI   навигация  ")
        viewModel.onAddTag("Python")
        viewModel.onAddTag("python")
        viewModel.onAddTag("ROS")

        viewModel.onPublishClick()

        val sent = repository.published.single()
        assertEquals("AI навигация", sent.title)
        assertEquals(ProjectType.Scientific, sent.type)
        assertEquals(TestSphere, sent.sphere)
        assertEquals(listOf("Python", "ROS"), sent.tags)
        assertEquals(ProjectPeriod(YearMonth(2026, 10), YearMonth(2027, 3)), sent.period)
        with(viewModel.state) {
            assertEquals(ProjectId("created"), publishedProjectId)
            assertFalse(isPublishing)
            assertFalse(publishFailed)
        }
    }

    @Test
    fun aSecondTapWhileTheRequestIsOnItsWayPublishesNothing() {
        repository.publishFailure = null
        repository.publishGate = Gate()
        val viewModel = viewModel()
        viewModel.fillValidForm()

        viewModel.onPublishClick()
        assertTrue(viewModel.state.isPublishing)
        viewModel.onPublishClick()
        viewModel.onPublishClick()

        assertEquals(1, repository.published.size)
        repository.publishGate!!.open()
        assertNotNull(viewModel.state.publishedProjectId)
    }

    @Test
    fun aTapAfterTheProjectIsPublishedDoesNotPublishACopy() {
        repository.publishFailure = null
        val viewModel = viewModel()
        viewModel.fillValidForm()
        viewModel.onPublishClick()
        assertNotNull(viewModel.state.publishedProjectId)

        // The form stays on the screen until the transition to the new project is over.
        viewModel.onPublishClick()

        assertEquals(1, repository.published.size)
    }

    @Test
    fun aFailureOfTheServerKeepsEverythingTyped() {
        repository.publishFailure = RemoteFailure.Http(400)
        val viewModel = viewModel()
        viewModel.fillValidForm()
        viewModel.onAddTag("Python")
        viewModel.onAddRoleClick()
        viewModel.onRoleTitleChange("ML Engineer")
        viewModel.onRoleSave()
        val typed = viewModel.state.draft

        viewModel.onPublishClick()

        with(viewModel.state) {
            assertEquals(typed, draft)
            assertTrue(publishFailed)
            assertFalse(publishUnavailable)
            assertFalse(isPublishing)
            assertNull(publishedProjectId)
            assertEquals(emptySet(), errors)
        }
    }

    @Test
    fun everyKindOfFailureCanBeRetriedUntilItSucceeds() {
        val viewModel = viewModel()
        viewModel.fillValidForm()
        for (failure in listOf(RemoteFailure.NoConnection(), RemoteFailure.Timeout(), RemoteFailure.Http(500), RemoteFailure.Http(400))) {
            repository.publishFailure = failure
            viewModel.onPublishClick()
            assertSame(failure, viewModel.state.publishFailure)
            assertFalse(viewModel.state.isPublishing)
        }
        repository.publishFailure = null

        viewModel.onPublishClick()

        assertEquals(5, repository.published.size)
        assertEquals(ProjectId("new"), viewModel.state.publishedProjectId)
        assertFalse(viewModel.state.publishFailed)
    }

    @Test
    fun theNextAttemptClearsTheOldFailureWhileItIsOnItsWay() {
        repository.publishFailure = cause
        val viewModel = viewModel()
        viewModel.fillValidForm()
        viewModel.onPublishClick()
        assertTrue(viewModel.state.publishFailed)
        repository.publishFailure = null
        repository.publishGate = Gate()

        viewModel.onPublishClick()

        assertTrue(viewModel.state.isPublishing)
        assertFalse(viewModel.state.publishFailed)
    }

    @Test
    fun aRetryTapWhileTheRetryIsOnItsWayPublishesNothingMore() {
        repository.publishFailure = cause
        val viewModel = viewModel()
        viewModel.fillValidForm()
        viewModel.onPublishClick()
        repository.publishFailure = null
        repository.publishGate = Gate()

        viewModel.onPublishClick()
        viewModel.onPublishClick()

        assertEquals(2, repository.published.size, "the failed attempt and one retry")
        repository.publishGate!!.open()
        assertNotNull(viewModel.state.publishedProjectId)
    }

    @Test
    fun theDeclaredRolesAreSentWithTheProject() {
        val viewModel = viewModel()
        viewModel.fillValidForm()
        viewModel.onAddRoleClick()
        viewModel.onRoleTitleChange("ML Engineer")
        viewModel.onRoleSkillAdd("Python")
        viewModel.onRoleSave()
        viewModel.onAddRoleClick()
        viewModel.onRoleTitleChange("Дизайнер")
        viewModel.onRoleSave()

        viewModel.onPublishClick()

        assertEquals(
            listOf(RoleDraft("ML Engineer", listOf("Python")), RoleDraft("Дизайнер")),
            repository.published.single().roles,
        )
    }

    @Test
    fun theSphereOfTheFormIsSentWithItsCode() {
        val sphere = Sphere("12", "Дизайн")
        val viewModel = viewModel()
        viewModel.fillValidForm()
        viewModel.onSphereSelect(sphere)

        viewModel.onPublishClick()

        assertEquals("12", repository.published.single().sphere.id)
    }

    @Test
    fun aSecondTapAfterAFailureOfTheFirstWhileThereIsAnInvalidEditSendsNothing() {
        repository.publishFailure = cause
        val viewModel = viewModel()
        viewModel.fillValidForm()
        viewModel.onPublishClick()

        viewModel.onTitleChange("   ")
        viewModel.onPublishClick()

        assertEquals(1, repository.published.size)
        assertEquals(setOf(ProjectDraftError.TitleEmpty), viewModel.state.errors)
    }

    // ---- the roles ----

    @Test
    fun aRoleIsDeclaredInTheSheetAndGoesIntoTheDraft() {
        val viewModel = viewModel()

        viewModel.onAddRoleClick()
        viewModel.onRoleTitleChange("  ML   Engineer ")
        viewModel.onRoleSkillAdd("Python")
        viewModel.onRoleSave()

        assertNull(viewModel.state.roleEditor)
        assertEquals(listOf(RoleDraft("ML Engineer", listOf("Python"))), viewModel.state.draft.roles)
    }

    @Test
    fun aRoleWithoutATitleStaysInTheSheetWithTheError() {
        val viewModel = viewModel()
        viewModel.onAddRoleClick()

        viewModel.onRoleSave()

        assertEquals(setOf(RoleDraftError.TitleEmpty), viewModel.state.roleEditor?.errors)
        assertEquals(emptyList(), viewModel.state.draft.roles)
    }

    @Test
    fun aRoleIsEditedInPlaceAndDeletedByItsPlace() {
        val viewModel = viewModel()
        for (title in listOf("Первая", "Вторая", "Третья")) {
            viewModel.onAddRoleClick()
            viewModel.onRoleTitleChange(title)
            viewModel.onRoleSave()
        }

        viewModel.onEditRoleClick(1)
        assertEquals(1, viewModel.state.roleEditor?.editedIndex)
        viewModel.onRoleTitleChange("Вторая (новая)")
        viewModel.onRoleSave()
        viewModel.onDeleteRoleClick(0)

        assertEquals(listOf("Вторая (новая)", "Третья"), viewModel.state.draft.roles.map { it.title })
    }

    @Test
    fun editingARoleThatIsNotThereOpensNothing() {
        val viewModel = viewModel()

        viewModel.onEditRoleClick(3)

        assertNull(viewModel.state.roleEditor)
    }

    @Test
    fun closingTheRoleSheetDropsTheEditedRole() {
        val viewModel = viewModel()
        viewModel.onAddRoleClick()
        viewModel.onRoleTitleChange("Черновик")

        viewModel.onRoleEditorDismiss()

        assertNull(viewModel.state.roleEditor)
        assertEquals(emptyList(), viewModel.state.draft.roles)
    }

    @Test
    fun aRoleThatDidNotPassTheSheetIsNotInTheDraftSoTheFormStaysValid() {
        repository.publishFailure = null
        val viewModel = viewModel()
        viewModel.fillValidForm()
        // A role that did not pass the sheet cannot be in the draft, so the form is still valid.
        viewModel.onAddRoleClick()
        viewModel.onRoleSave()

        viewModel.onPublishClick()

        assertEquals(1, repository.published.size)
        assertEquals(emptyList(), repository.published.single().roles)
    }

    // ---- tags ----

    @Test
    fun tagsAreAddedOnceIgnoringCaseAndRemoved() {
        val viewModel = viewModel()

        viewModel.onAddTag("Python")
        viewModel.onAddTag(" python ")
        viewModel.onAddTag("   ")
        viewModel.onAddTag("ROS")
        viewModel.onRemoveTag("PYTHON")

        assertEquals(listOf("ROS"), viewModel.state.draft.tags)
    }

    // ---- the spheres ----

    @Test
    fun theSpheresAreAskedForAtOnce() {
        val viewModel = viewModel()

        assertEquals(1, repository.spheresCalls)
        assertEquals(listOf(TestSphere), viewModel.state.spheres)
        assertFalse(viewModel.state.spheresLoadFailed)
    }

    @Test
    fun aFailureOfTheSpheresOffersARetry() {
        repository.spheresFailure = cause
        val viewModel = viewModel()
        assertTrue(viewModel.state.spheresLoadFailed)
        repository.spheresFailure = null

        viewModel.onSpheresRetry()

        assertFalse(viewModel.state.spheresLoadFailed)
        assertEquals(listOf(TestSphere), viewModel.state.spheres)
    }

    @Test
    fun aRetryWhileTheSpheresAreOnTheirWayAsksOnce() {
        repository.spheresGate = Gate()
        val viewModel = viewModel()

        viewModel.onSpheresRetry()
        viewModel.onSpheresRetry()

        assertEquals(1, repository.spheresCalls)
        repository.spheresGate!!.open()
        assertEquals(listOf(TestSphere), viewModel.state.spheres)
    }

    @Test
    fun openingThePickerWithoutSpheresAsksAgainAndPickingFillsTheDraft() {
        repository.spheresFailure = cause
        val viewModel = viewModel()
        repository.spheresFailure = null

        viewModel.onSphereFieldClick()

        assertTrue(viewModel.state.isSpherePickerVisible)
        assertEquals(2, repository.spheresCalls)
        val sphere = Sphere("Физика", "Физика")
        viewModel.onSphereSelect(sphere)
        assertEquals(sphere, viewModel.state.draft.sphere)
        assertFalse(viewModel.state.isSpherePickerVisible)
    }

    @Test
    fun theLoadedSpheresAreNotAskedForAgainWhenThePickerOpens() {
        val viewModel = viewModel()

        viewModel.onSphereFieldClick()
        viewModel.onSpherePickerDismiss()

        assertEquals(1, repository.spheresCalls)
        assertFalse(viewModel.state.isSpherePickerVisible)
    }
}
