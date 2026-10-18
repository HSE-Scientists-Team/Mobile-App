package org.matchlab.academic_net.gateway.internal

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.gateway.ProfilesApi
import org.matchlab.academic_net.gateway.dto.UserProfileDto
import org.matchlab.academic_net.gateway.dto.UserProfileInputDto

private const val MyProfilePath = "users/me/profile"

private val ProfileInputKeys: Set<String> = serialNamesOf(UserProfileInputDto.serializer())

/**
 * [ProfilesApi] over the client of a session.
 *
 * @param onProfileSaved told after every successful `PUT` with the id of the saved profile, so that the directory
 * of public profiles forgets its older copy of it.
 */
internal class KtorProfilesApi(
    private val requests: ApiRequests,
    private val onProfileSaved: suspend (userId: String) -> Unit,
) : ProfilesApi {
    // The PUT replaces the whole profile, so two read-modify-writes must not interleave: the second would write
    // over the first with a copy it read before the first was saved.
    private val writes = Mutex()

    override suspend fun myProfile(): UserProfileDto = requests.get(MyProfilePath)

    override suspend fun updateMyProfile(change: (UserProfileInputDto) -> UserProfileInputDto): UserProfileDto =
        writes.withLock {
            val current = readOrEmpty()
            val changed = change(current.dto.details)
            require(changed.displayName.isNotBlank()) { "The display name of a profile cannot be blank" }

            val typed = GatewayJson.encodeToJsonElement(UserProfileInputDto.serializer(), changed).jsonObject
            val body = layOver(current.rawDetails, typed, ProfileInputKeys)
            val saved: UserProfileDto =
                requests.putJson(MyProfilePath, GatewayJson.encodeToString(JsonObject.serializer(), body))
            onProfileSaved(saved.userId.ifBlank { current.dto.userId })
            saved
        }

    /** The profile as the server has it; the 404 of an account without a profile is an empty profile. */
    private suspend fun readOrEmpty(): AnswerWithDetails<UserProfileDto> =
        try {
            requests.getWithDetails(MyProfilePath)
        } catch (failure: RemoteFailure.Http) {
            if (failure.status != NotFound) throw failure
            AnswerWithDetails(UserProfileDto(), rawDetails = null)
        }

    private companion object {
        const val NotFound = 404
    }
}
