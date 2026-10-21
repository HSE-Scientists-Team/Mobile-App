package org.matchlab.academic_net.gateway

import org.matchlab.academic_net.gateway.dto.UserAchievementDto
import org.matchlab.academic_net.gateway.dto.UserAchievementInputDto
import org.matchlab.academic_net.gateway.dto.UserAchievementListDto

/**
 * The achievements of a profile: the works, the diplomas and the certificates. Every call throws `RemoteFailure`
 * when it fails and lets a `CancellationException` through, like [AccountApi]. What a status code means for a
 * feature (a 409 is a conflict, a 404 on a delete is "already gone") is that feature's business.
 *
 * The lists take `limit` 1..[MaxLimit] and `offset` 0..[MaxOffset] (a call outside the range is an
 * `IllegalArgumentException`) and carry no total (API-GAP G16).
 */
interface AchievementsApi {
    /** `GET /api/v1/users/{user_id}/achievements?limit&offset`: what anyone may see of [userId]'s achievements. */
    suspend fun userAchievements(userId: String, limit: Int = MaxLimit, offset: Int = 0): UserAchievementListDto

    /** `GET /api/v1/users/me/achievements?limit&offset`: the owner's view (every record, the moderation comment). */
    suspend fun myAchievements(limit: Int = MaxLimit, offset: Int = 0): UserAchievementListDto

    /** `GET /api/v1/users/me/achievements/{id}`; `Http(404)` when the viewer has no such record. */
    suspend fun myAchievement(id: String): UserAchievementDto

    /** `POST /api/v1/users/me/achievements` → 201: adds an achievement that is not confirmed yet. */
    suspend fun create(input: UserAchievementInputDto): UserAchievementDto

    /**
     * The only way to write `PUT /api/v1/users/me/achievements/{id}`, which replaces the details and resets the
     * confirmation. It reads the record first; when its revision is not [expectedRevision] it throws `Http(409)`
     * and sends nothing. Otherwise [change] gets the typed `details` and the result is sent with
     * `expected_revision`, laid over the raw `details` JSON of the read: `evidence_file_id` and the fields this
     * client does not know are kept. [change] should copy what it does not mean to change.
     */
    suspend fun updateMine(
        id: String,
        expectedRevision: Int,
        change: (UserAchievementInputDto) -> UserAchievementInputDto,
    ): UserAchievementDto

    /** `POST /api/v1/users/me/achievements/{id}/submit` → 200: sends the record for verification. */
    suspend fun submit(id: String): UserAchievementDto

    /** `DELETE /api/v1/users/me/achievements/{id}` → 204 (the server keeps the history). */
    suspend fun delete(id: String)

    companion object {
        /** The most achievements one call returns. */
        const val MaxLimit = 100

        /** The largest `offset` the API accepts. */
        const val MaxOffset = 10_000
    }
}
