package org.matchlab.academic_net.components

import academicnet.components.generated.resources.Res
import academicnet.components.generated.resources.avatar_arjun
import academicnet.components.generated.resources.avatar_elena
import androidx.compose.runtime.Immutable
import org.jetbrains.compose.resources.DrawableResource

/**
 * Whose picture [Avatar] draws: the person's [key] (the user id, the key of the picture they uploaded) and the
 * [illustration] shown while there is no picture, or until it has loaded.
 */
@Immutable
data class AvatarSource(val key: String, val illustration: DrawableResource)

/**
 * The outlined "Avatar" illustrations of the design. A person who has not uploaded a picture, or whose picture has not
 * loaded yet, gets one of these ([forKey]).
 */
object AppAvatars {
    val Elena: DrawableResource = Res.drawable.avatar_elena
    val Arjun: DrawableResource = Res.drawable.avatar_arjun

    private val all = listOf(Elena, Arjun)

    /** The same illustration for the same [key] (e.g. a user id) on every launch and platform. */
    fun illustrationFor(key: String): DrawableResource = all[(key.hashCode() and Int.MAX_VALUE) % all.size]

    /** The source of the picture of the person with [key] (the user id), with the illustration to show until it is there. */
    fun forKey(key: String): AvatarSource = AvatarSource(key, illustrationFor(key))
}
