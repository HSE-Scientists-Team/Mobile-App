package org.matchlab.academic_net.gateway.internal

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.gateway.AchievementsApi
import org.matchlab.academic_net.gateway.dto.UserAchievementDto
import org.matchlab.academic_net.gateway.dto.UserAchievementInputDto
import org.matchlab.academic_net.gateway.dto.UserAchievementListDto

private const val MyAchievementsPath = "users/me/achievements"

private val AchievementInputKeys: Set<String> = serialNamesOf(UserAchievementInputDto.serializer())

/** [AchievementsApi] over the client of a session. */
internal class KtorAchievementsApi(private val requests: ApiRequests) : AchievementsApi {
    override suspend fun userAchievements(userId: String, limit: Int, offset: Int): UserAchievementListDto =
        requests.get("users/${pathSegment(userId)}/achievements", pageQuery(limit, offset))

    override suspend fun myAchievements(limit: Int, offset: Int): UserAchievementListDto =
        requests.get(MyAchievementsPath, pageQuery(limit, offset))

    override suspend fun myAchievement(id: String): UserAchievementDto = requests.get(achievementPath(id))

    override suspend fun create(input: UserAchievementInputDto): UserAchievementDto =
        requests.post(MyAchievementsPath, input)

    override suspend fun updateMine(
        id: String,
        expectedRevision: Int,
        change: (UserAchievementInputDto) -> UserAchievementInputDto,
    ): UserAchievementDto {
        val path = achievementPath(id)
        val current = requests.getWithDetails<UserAchievementDto>(path)
        // The server would refuse the stale revision too; asking it first costs a request and nothing is written.
        if (current.dto.revision != expectedRevision) throw RemoteFailure.Http(StaleRevision)

        val changed = change(current.dto.details).copy(expectedRevision = expectedRevision)
        val typed = GatewayJson.encodeToJsonElement(UserAchievementInputDto.serializer(), changed).jsonObject
        val body = layOver(current.rawDetails, typed, AchievementInputKeys)
        return requests.putJson(path, GatewayJson.encodeToString(JsonObject.serializer(), body))
    }

    override suspend fun submit(id: String): UserAchievementDto = requests.postEmpty("${achievementPath(id)}/submit")

    override suspend fun delete(id: String) = requests.delete(achievementPath(id))

    private fun achievementPath(id: String) = "$MyAchievementsPath/${pathSegment(id)}"

    private fun pageQuery(limit: Int, offset: Int): Map<String, String?> {
        require(limit in 1..AchievementsApi.MaxLimit) { "limit must be 1..${AchievementsApi.MaxLimit}: $limit" }
        require(offset in 0..AchievementsApi.MaxOffset) { "offset must be 0..${AchievementsApi.MaxOffset}: $offset" }
        return mapOf("limit" to limit.toString(), "offset" to offset.toString())
    }

    private companion object {
        /** "Конфликт состояния или версии" in the table of answers of the API. */
        const val StaleRevision = 409
    }
}
