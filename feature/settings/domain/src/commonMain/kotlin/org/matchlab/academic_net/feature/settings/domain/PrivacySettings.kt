package org.matchlab.academic_net.feature.settings.domain

/**
 * What the user lets other people see in their profile.
 *
 * The server applies the settings when it serves someone else's profile; the user's own profile
 * is always visible to them in full.
 */
data class PrivacySettings(
    /**
     * Other people see the projects where the user participates. When off, only the initiators of
     * those projects see that the user takes part.
     */
    val showParticipatedProjects: Boolean,
    /**
     * Everyone sees the user's e-mail and Telegram. When off, only the members of the teams of the
     * user's projects see them.
     */
    val showContacts: Boolean,
) {
    /** Whether [option] is on. */
    fun isEnabled(option: PrivacyOption): Boolean = when (option) {
        PrivacyOption.ParticipatedProjects -> showParticipatedProjects
        PrivacyOption.Contacts -> showContacts
    }

    /** These settings with [option] switched to [enabled]; the other options keep their values. */
    fun with(option: PrivacyOption, enabled: Boolean): PrivacySettings = when (option) {
        PrivacyOption.ParticipatedProjects -> copy(showParticipatedProjects = enabled)
        PrivacyOption.Contacts -> copy(showContacts = enabled)
    }
}

/** One switch of [PrivacySettings]; the screen changes them one at a time. */
enum class PrivacyOption {
    ParticipatedProjects,
    Contacts,
}
