package org.matchlab.academic_net.components

import androidx.compose.runtime.Immutable

/**
 * Whose picture [Avatar] draws: the person's [key] (the user id, the key of the picture they uploaded) and their [name],
 * the initials of which are drawn while there is no picture, or until it has loaded.
 *
 * @property name the name as the person wrote it, **not** the text shown when there is none ("Без имени"): a blank name
 * has no initials, and the avatar is an empty disc
 */
@Immutable
data class AvatarSource(val key: String, val name: String) {
    companion object {
        /** A person the app knows nothing about (no profile): no picture is asked for and no initials are drawn. */
        val None: AvatarSource = AvatarSource(key = "", name = "")
    }
}
