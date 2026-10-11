package org.matchlab.academic_net.feature.projects.ui.create

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.datetime.YearMonth
import org.matchlab.academic_net.common.model.ProjectPeriod
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.feature.projects.data.mock.MockProjectsData
import org.matchlab.academic_net.feature.projects.domain.ProjectDraftError
import org.matchlab.academic_net.feature.projects.domain.RoleDraft
import org.matchlab.academic_net.feature.projects.domain.RoleDraftError
import org.matchlab.academic_net.feature.projects.ui.FailingDataSource
import org.matchlab.academic_net.feature.projects.ui.TestGraph
import org.matchlab.academic_net.feature.projects.ui.installMainDispatcher
import org.matchlab.academic_net.feature.projects.ui.uninstallMainDispatcher

class CreateProjectViewModelTest {
    @BeforeTest
    fun setUp() = installMainDispatcher()

    @AfterTest
    fun tearDown() = uninstallMainDispatcher()

    private fun viewModel(graph: TestGraph = TestGraph()) =
        CreateProjectViewModel(graph.publishProject, graph.getSpheres)

    private fun CreateProjectViewModel.fillRequiredFields() {
        onTitleChange("Умная лаборатория")
        onTypeSelect(ProjectType.Practical)
        onSphereSelect(MockProjectsData.spheres.first())
        onProblemChange("Простаивает оборудование")
        onGoalChange("Онлайн-запись")
        onStartFieldClick()
        onMonthSelect(YearMonth(2026, 10))
        onEndFieldClick()
        onMonthSelect(YearMonth(2027, 3))
    }

    @Test
    fun spheresAreLoadedOnStart() = runTest {
        val viewModel = viewModel()

        assertEquals(MockProjectsData.spheres, viewModel.uiState.value.spheres)
        assertFalse(viewModel.uiState.value.spheresLoadFailed)
    }

    @Test
    fun failedSpheresCanBeRetried() = runTest {
        val dataSource = FailingDataSource(failSpheres = true)
        val viewModel = viewModel(TestGraph(dataSource))
        assertTrue(viewModel.uiState.value.spheresLoadFailed)
        assertTrue(viewModel.uiState.value.spheres.isEmpty())

        dataSource.failSpheres = false
        viewModel.onSpheresRetry()

        assertFalse(viewModel.uiState.value.spheresLoadFailed)
        assertEquals(MockProjectsData.spheres, viewModel.uiState.value.spheres)
    }

    @Test
    fun selectingSphereClosesPickerAndStoresIt() {
        val viewModel = viewModel()
        viewModel.onSphereFieldClick()
        assertTrue(viewModel.uiState.value.isSpherePickerVisible)

        viewModel.onSphereSelect(MockProjectsData.spheres[1])

        assertFalse(viewModel.uiState.value.isSpherePickerVisible)
        assertEquals(MockProjectsData.spheres[1], viewModel.uiState.value.draft.sphere)
    }

    @Test
    fun publishingEmptyFormShowsErrorsAndSendsNothing() = runTest {
        val graph = TestGraph()
        val viewModel = viewModel(graph)
        val before = graph.observeMyProjects().first().size

        viewModel.onPublishClick()

        val state = viewModel.uiState.value
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
            state.errors,
        )
        assertFalse(state.isPublishing)
        assertNull(state.publishedProjectId)
        assertEquals(before, graph.observeMyProjects().first().size)
    }

    @Test
    fun errorsAreHiddenUntilFirstPublishAttempt() {
        val viewModel = viewModel()

        viewModel.onTitleChange("")

        assertTrue(viewModel.uiState.value.errors.isEmpty())
    }

    @Test
    fun errorsDisappearAsFieldsAreFilled() {
        val viewModel = viewModel()
        viewModel.onPublishClick()
        assertTrue(ProjectDraftError.TitleEmpty in viewModel.uiState.value.errors)

        viewModel.onTitleChange("Проект")

        assertFalse(ProjectDraftError.TitleEmpty in viewModel.uiState.value.errors)
        assertTrue(ProjectDraftError.GoalEmpty in viewModel.uiState.value.errors)
    }

    @Test
    fun monthsAreChosenThroughThePicker() {
        val viewModel = viewModel()

        viewModel.onStartFieldClick()
        assertEquals(DateField.Start, viewModel.uiState.value.monthPickerFor)
        viewModel.onMonthSelect(YearMonth(2026, 10))
        assertNull(viewModel.uiState.value.monthPickerFor)
        viewModel.onEndFieldClick()
        assertEquals(DateField.End, viewModel.uiState.value.monthPickerFor)
        viewModel.onMonthSelect(YearMonth(2027, 3))

        val draft = viewModel.uiState.value.draft
        assertEquals(YearMonth(2026, 10), draft.start)
        assertEquals(YearMonth(2027, 3), draft.end)
    }

    @Test
    fun dismissedPickerChangesNothing() {
        val viewModel = viewModel()
        viewModel.onStartFieldClick()

        viewModel.onMonthPickerDismiss()

        assertNull(viewModel.uiState.value.monthPickerFor)
        assertNull(viewModel.uiState.value.draft.start)
    }

    @Test
    fun selectingWithoutAPickerIsIgnored() {
        val viewModel = viewModel()

        viewModel.onMonthSelect(YearMonth(2026, 10))

        assertNull(viewModel.uiState.value.draft.start)
        assertNull(viewModel.uiState.value.draft.end)
    }

    @Test
    fun endBeforeStartIsReportedAndFixedByAnotherMonth() = runTest {
        val viewModel = viewModel()
        viewModel.fillRequiredFields()
        viewModel.onEndFieldClick()
        viewModel.onMonthSelect(YearMonth(2026, 5))

        viewModel.onPublishClick()

        assertEquals(setOf(ProjectDraftError.EndBeforeStart), viewModel.uiState.value.errors)
        assertNull(viewModel.uiState.value.publishedProjectId)

        viewModel.onEndFieldClick()
        viewModel.onMonthSelect(YearMonth(2026, 10))

        assertTrue(viewModel.uiState.value.errors.isEmpty())
    }

    @Test
    fun tagsAreDeduplicatedAndRemovable() {
        val viewModel = viewModel()

        viewModel.onAddTag("Python")
        viewModel.onAddTag(" python ")
        viewModel.onAddTag("IoT")
        viewModel.onRemoveTag("PYTHON")

        assertEquals(listOf("IoT"), viewModel.uiState.value.draft.tags)
    }

    @Test
    fun roleIsAddedThroughTheEditor() {
        val viewModel = viewModel()

        viewModel.onAddRoleClick()
        assertNotNull(viewModel.uiState.value.roleEditor)
        viewModel.onRoleTitleChange("ML Engineer")
        viewModel.onRoleSkillAdd("Python")
        viewModel.onRoleSave()

        assertNull(viewModel.uiState.value.roleEditor)
        assertEquals(listOf(RoleDraft("ML Engineer", listOf("Python"))), viewModel.uiState.value.draft.roles)
    }

    @Test
    fun roleWithoutTitleStaysInEditorWithError() {
        val viewModel = viewModel()
        viewModel.onAddRoleClick()

        viewModel.onRoleSave()

        val editor = assertNotNull(viewModel.uiState.value.roleEditor)
        assertEquals(setOf(RoleDraftError.TitleEmpty), editor.errors)
        assertTrue(viewModel.uiState.value.draft.roles.isEmpty())
    }

    @Test
    fun roleCanBeEditedAndDeleted() {
        val viewModel = viewModel()
        viewModel.onAddRoleClick()
        viewModel.onRoleTitleChange("Designer")
        viewModel.onRoleSave()
        viewModel.onAddRoleClick()
        viewModel.onRoleTitleChange("QA")
        viewModel.onRoleSave()

        viewModel.onEditRoleClick(0)
        assertEquals(0, viewModel.uiState.value.roleEditor?.editedIndex)
        viewModel.onRoleTitleChange("UX Designer")
        viewModel.onRoleSave()
        viewModel.onDeleteRoleClick(1)

        assertEquals(listOf(RoleDraft("UX Designer")), viewModel.uiState.value.draft.roles)
    }

    @Test
    fun dismissedEditorDiscardsChanges() {
        val viewModel = viewModel()
        viewModel.onAddRoleClick()
        viewModel.onRoleTitleChange("Designer")

        viewModel.onRoleEditorDismiss()

        assertNull(viewModel.uiState.value.roleEditor)
        assertTrue(viewModel.uiState.value.draft.roles.isEmpty())
    }

    @Test
    fun validFormIsPublishedWithItsRoles() = runTest {
        val graph = TestGraph()
        val viewModel = viewModel(graph)
        viewModel.fillRequiredFields()
        viewModel.onAddRoleClick()
        viewModel.onRoleTitleChange("Backend")
        viewModel.onRoleSkillAdd("Kotlin")
        viewModel.onRoleSave()

        viewModel.onPublishClick()

        val state = viewModel.uiState.value
        val id = assertNotNull(state.publishedProjectId)
        assertFalse(state.isPublishing)
        val project = assertNotNull(graph.observeProject(id).first())
        assertEquals("Умная лаборатория", project.title)
        assertEquals(ProjectStatus.Recruiting, project.status)
        assertEquals(listOf("Backend"), project.roles.map { it.title })
        assertEquals(listOf("Kotlin"), project.roles.single().skills)
        assertEquals(ProjectPeriod(YearMonth(2026, 10), YearMonth(2027, 3)), project.period)
    }

    @Test
    fun networkFailureIsReportedAndFormIsKept() = runTest {
        val viewModel = viewModel(TestGraph(FailingDataSource()))
        viewModel.fillRequiredFields()

        viewModel.onPublishClick()

        val state = viewModel.uiState.value
        assertTrue(state.publishFailed)
        assertFalse(state.isPublishing)
        assertNull(state.publishedProjectId)
        assertEquals("Умная лаборатория", state.draft.title)
    }

    @Test
    fun editingAfterFailureClearsTheErrorMessage() = runTest {
        val viewModel = viewModel(TestGraph(FailingDataSource()))
        viewModel.fillRequiredFields()
        viewModel.onPublishClick()

        viewModel.onTitleChange("Другое название")

        assertFalse(viewModel.uiState.value.publishFailed)
    }
}
