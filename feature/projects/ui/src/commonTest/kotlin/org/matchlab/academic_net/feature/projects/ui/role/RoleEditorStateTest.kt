package org.matchlab.academic_net.feature.projects.ui.role

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.matchlab.academic_net.feature.projects.domain.RoleDraftError

class RoleEditorStateTest {
    @Test
    fun emptyEditorIsInvalid() {
        val state = RoleEditorState().validated()

        assertEquals(setOf(RoleDraftError.TitleEmpty), state.errors)
        assertFalse(state.isValid)
    }

    @Test
    fun typingClearsErrors() {
        val state = RoleEditorState().validated().withTitle("ML")

        assertTrue(state.errors.isEmpty())
        assertTrue(state.isValid)
    }

    @Test
    fun skillsAreDeduplicatedAndRemovable() {
        val state = RoleEditorState(draft = org.matchlab.academic_net.feature.projects.domain.RoleDraft("ML"))
            .withSkill("Python")
            .withSkill(" python ")
            .withSkill("TensorFlow")

        assertEquals(listOf("Python", "TensorFlow"), state.draft.skills)
        assertEquals(listOf("TensorFlow"), state.withoutSkill("PYTHON").draft.skills)
    }
}
