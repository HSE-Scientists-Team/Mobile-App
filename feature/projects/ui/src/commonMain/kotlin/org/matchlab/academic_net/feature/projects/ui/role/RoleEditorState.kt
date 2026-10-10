package org.matchlab.academic_net.feature.projects.ui.role

import androidx.compose.runtime.Immutable
import org.matchlab.academic_net.feature.projects.domain.RoleDraft
import org.matchlab.academic_net.feature.projects.domain.RoleDraftError
import org.matchlab.academic_net.utils.withTag
import org.matchlab.academic_net.utils.withoutTag

/**
 * The role being declared or edited in the role sheet. Shared by the "new project" form and the
 * roles screen of a published project.
 */
@Immutable
data class RoleEditorState(
    /** Position of the role in the form's list when editing it, `null` for a new role. */
    val editedIndex: Int? = null,
    val draft: RoleDraft = RoleDraft(),
    val errors: Set<RoleDraftError> = emptySet(),
) {
    /** Errors go away as soon as the user starts fixing them. */
    fun withTitle(title: String) = copy(draft = draft.copy(title = title), errors = emptySet())

    fun withSkill(skill: String) = copy(draft = draft.copy(skills = draft.skills.withTag(skill)))

    fun withoutSkill(skill: String) = copy(draft = draft.copy(skills = draft.skills.withoutTag(skill)))

    fun validated(): RoleEditorState = copy(errors = draft.normalized().validate())

    val isValid: Boolean get() = draft.normalized().validate().isEmpty()
}
