package org.matchlab.academic_net.feature.settings.data

import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.feature.settings.domain.ProfileMissingException
import org.matchlab.academic_net.gateway.ProfilesApi

/**
 * [SettingsDataSource] over the profile of the Gateway: "Открыт к сотрудничеству" is the field `open_to_collaboration`
 * of the user's profile, not a setting of the API's own (`/users/me/settings` holds other switches, which the app
 * does not use).
 *
 * - `getOpenToCollaboration`: the profile (`GET /users/me/profile`) with a name gives `open_to_collaboration`, an
 *   absent field being `false`; a 404, or a profile without a name, is `null` (API-GAP G19: the API does not say which
 *   of the two an account without a profile gets).
 * - `setOpenToCollaboration`: a read-modify-write of the profile ([ProfilesApi.updateMyProfile]), so that the switch
 *   changes this one field and keeps what the profile form saved; a profile without a name throws
 *   [ProfileMissingException] and nothing is sent. The answer is the value in the profile the server returns.
 *
 * Any other status, and every failure of the network, is thrown as the `RemoteFailure` it is.
 */
class GatewaySettingsDataSource(private val profiles: ProfilesApi) : SettingsDataSource {
    override suspend fun getOpenToCollaboration(): Boolean? {
        val profile = try {
            profiles.myProfile()
        } catch (failure: RemoteFailure.Http) {
            if (failure.status == NOT_FOUND) return null else throw failure
        }
        if (profile.details.displayName.isBlank()) return null
        return profile.details.openToCollaboration ?: false
    }

    override suspend fun setOpenToCollaboration(open: Boolean): Boolean =
        profiles.updateMyProfile { details ->
            if (details.displayName.isBlank()) throw ProfileMissingException()
            details.copy(openToCollaboration = open)
        }.details.openToCollaboration ?: false

    private companion object {
        const val NOT_FOUND = 404
    }
}
