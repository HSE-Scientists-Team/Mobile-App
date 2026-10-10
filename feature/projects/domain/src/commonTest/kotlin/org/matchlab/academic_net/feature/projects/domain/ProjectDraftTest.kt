package org.matchlab.academic_net.feature.projects.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProjectDraftTest {
    @Test
    fun emptyDraftReportsEveryRequiredField() {
        assertEquals(
            setOf(
                ProjectDraftError.TitleEmpty,
                ProjectDraftError.TypeMissing,
                ProjectDraftError.SphereMissing,
                ProjectDraftError.ProblemEmpty,
                ProjectDraftError.GoalEmpty,
            ),
            ProjectDraft().validate(),
        )
    }

    @Test
    fun filledDraftIsValidWithoutRolesAndTags() {
        assertTrue(validDraft().validate().isEmpty())
    }

    @Test
    fun blankTitleIsEmpty() {
        assertEquals(setOf(ProjectDraftError.TitleEmpty), validDraft().copy(title = "   ").validate())
    }

    @Test
    fun titleLongerThanLimitIsRejected() {
        val title = "а".repeat(ProjectLimits.MaxTitleLength + 1)

        assertEquals(setOf(ProjectDraftError.TitleTooLong), validDraft().copy(title = title).validate())
    }

    @Test
    fun titleOfMaxLengthIsAccepted() {
        val title = "а".repeat(ProjectLimits.MaxTitleLength)

        assertTrue(validDraft().copy(title = title).validate().isEmpty())
    }

    @Test
    fun roleWithoutTitleInvalidatesDraft() {
        val draft = validDraft().copy(roles = listOf(RoleDraft("ML Engineer"), RoleDraft("  ")))

        assertEquals(setOf(ProjectDraftError.RoleInvalid), draft.validate())
    }

    @Test
    fun normalizedTrimsTextAndCleansTagsAndRoles() {
        val draft = validDraft().copy(
            title = "  AI   навигация ",
            problem = "  проблема \n",
            tags = listOf("Python", " python", "", "IoT"),
            roles = listOf(RoleDraft("  ML  Engineer ", listOf("Python", "python", " TensorFlow "))),
        ).normalized()

        assertEquals("AI навигация", draft.title)
        assertEquals("проблема", draft.problem)
        assertEquals(listOf("Python", "IoT"), draft.tags)
        assertEquals(RoleDraft("ML Engineer", listOf("Python", "TensorFlow")), draft.roles.single())
    }

    @Test
    fun toNewProjectNeedsTypeAndSphere() {
        assertNull(validDraft().copy(type = null).toNewProject())
        assertNull(validDraft().copy(sphere = null).toNewProject())
        assertNotNull(validDraft().toNewProject())
    }

    @Test
    fun roleDraftValidation() {
        assertEquals(setOf(RoleDraftError.TitleEmpty), RoleDraft("").validate())
        assertEquals(
            setOf(RoleDraftError.TitleTooLong),
            RoleDraft("a".repeat(ProjectLimits.MaxRoleTitleLength + 1)).validate(),
        )
        assertTrue(RoleDraft("Designer").validate().isEmpty())
    }
}
