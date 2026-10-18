package org.matchlab.academic_net.feature.settings.domain

/**
 * The signed-in user's settings: the one switch there is, "Открыт к сотрудничеству". The calls throw when they fail
 * (no network, a server error); the use cases wrap them.
 */
interface SettingsRepository {
    /**
     * Whether people can find the user in the people search and in the recommendations, or `null` when the user has
     * no profile yet (API-GAP G19: an account that never saved the profile form has no name, and the switch is a
     * field of the profile).
     */
    suspend fun getOpenToCollaboration(): Boolean?

    /**
     * Saves [open] and returns the value the server has now. The profile is changed in this one field only: what was
     * saved in the profile form stays.
     *
     * @throws ProfileMissingException when the user has no profile yet: there is nothing to put the switch in.
     */
    suspend fun setOpenToCollaboration(open: Boolean): Boolean
}

/** The user has no profile yet (no name), so a setting that lives in the profile cannot be saved. */
class ProfileMissingException : IllegalStateException("The user has no profile yet")
