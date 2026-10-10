package org.matchlab.academic_net.components

import academicnet.components.generated.resources.Res
import academicnet.components.generated.resources.avatar_arjun
import academicnet.components.generated.resources.avatar_elena
import org.jetbrains.compose.resources.DrawableResource

/**
 * The outlined "Avatar" illustrations of the design. Until profiles carry their own images
 * ([forKey] is the stand-in), every person gets one of these.
 */
object AppAvatars {
    val Elena: DrawableResource = Res.drawable.avatar_elena
    val Arjun: DrawableResource = Res.drawable.avatar_arjun

    private val all = listOf(Elena, Arjun)

    /** The same illustration for the same [key] (e.g. a user id) on every launch and platform. */
    fun forKey(key: String): DrawableResource = all[(key.hashCode() and Int.MAX_VALUE) % all.size]
}
