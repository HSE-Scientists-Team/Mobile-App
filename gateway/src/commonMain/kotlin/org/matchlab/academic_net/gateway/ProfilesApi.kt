package org.matchlab.academic_net.gateway

import org.matchlab.academic_net.gateway.dto.UserProfileDto
import org.matchlab.academic_net.gateway.dto.UserProfileInputDto

/**
 * The viewer's own profile. Every call throws `RemoteFailure` when it fails and lets a `CancellationException`
 * through, like [AccountApi]. The profile of any other person is read through [UserDirectory].
 */
interface ProfilesApi {
    /**
     * `GET /api/v1/users/me/profile` → 200. An account that never saved a profile gets a 404 (`Http(404)`) or
     * an empty `details`: the API does not say which (API-GAP G19), so the caller treats both as "no profile yet".
     */
    suspend fun myProfile(): UserProfileDto

    /**
     * The only way to write `PUT /api/v1/users/me/profile`, which replaces every field of the profile: a change
     * of one field must not drop the others, so the call reads before it writes.
     *
     * One call at a time per session (a `Mutex`): it reads the profile (a 404 is an empty one), hands the typed
     * `details` to [change], and sends the result laid over the raw `details` JSON of the answer, so that the
     * fields the typed form does not know (the API may grow) go back as they came. Nothing is sent when the read
     * fails, when [change] throws, or when the result has a blank `display_name` (an `IllegalArgumentException`:
     * the API requires it). After the 200 the viewer's entry in [UserDirectory] is dropped.
     *
     * [change] should copy what it does not mean to change (`details.copy(about = ...)`): the avatar, the
     * contacts and the strings the app does not understand (`project_types`) go back untouched this way.
     */
    suspend fun updateMyProfile(change: (UserProfileInputDto) -> UserProfileInputDto): UserProfileDto
}
