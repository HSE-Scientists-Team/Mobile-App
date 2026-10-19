package org.matchlab.academic_net.feature.profile.data

import org.matchlab.academic_net.gateway.AchievementsApi
import org.matchlab.academic_net.gateway.MediaApi
import org.matchlab.academic_net.gateway.ProfilesApi
import org.matchlab.academic_net.gateway.ReviewsApi
import org.matchlab.academic_net.gateway.dto.CreateMediaUploadDto
import org.matchlab.academic_net.gateway.dto.MediaDownloadDto
import org.matchlab.academic_net.gateway.dto.MediaFileDto
import org.matchlab.academic_net.gateway.dto.MediaUploadDto
import org.matchlab.academic_net.gateway.dto.UserAchievementDto
import org.matchlab.academic_net.gateway.dto.UserAchievementInputDto
import org.matchlab.academic_net.gateway.dto.UserAchievementListDto
import org.matchlab.academic_net.gateway.dto.UserProfileDto
import org.matchlab.academic_net.gateway.dto.UserProfileInputDto
import org.matchlab.academic_net.gateway.dto.UserReviewDto
import org.matchlab.academic_net.gateway.dto.UserReviewListDto

// The Gateway's calls as small fakes, and builders of what they answer. The data layer is tested against these, not against
// HTTP: the HTTP side of the calls has its own tests in :gateway.

internal const val ViewerRaw = "viewer"
internal const val OtherRaw = "other"

internal fun profileDto(
    userId: String = OtherRaw,
    name: String = "Арджун Мехта",
    university: String? = "НИУ ВШЭ",
    studyLevel: String? = "бакалавриат",
    studyYear: Int? = 3,
    specialization: String? = "Computer Science",
    about: String? = "Люблю данные",
    interests: List<String> = listOf("ML"),
    skills: List<String> = listOf("Kotlin"),
    projectTypes: List<String> = emptyList(),
    openToCollaboration: Boolean? = true,
    avatarFileId: String? = null,
    contactEmail: String? = null,
    telegram: String? = null,
    verified: Boolean = false,
    rating: Double = 0.0,
    reviewCount: Int = 0,
    positiveReviewCount: Int = 0,
): UserProfileDto = UserProfileDto(
    userId = userId,
    details = UserProfileInputDto(
        displayName = name,
        avatarFileId = avatarFileId,
        university = university,
        studyLevel = studyLevel,
        studyYear = studyYear,
        specialization = specialization,
        about = about,
        openToCollaboration = openToCollaboration,
        projectTypes = projectTypes,
        interests = interests,
        skills = skills,
        contactEmail = contactEmail,
        telegram = telegram,
    ),
    verified = verified,
    rating = rating,
    reviewCount = reviewCount,
    positiveReviewCount = positiveReviewCount,
)

internal fun achievementDto(
    id: String = "a1",
    kind: String = "scientific",
    title: String = "Статья о графах",
    description: String? = "Описание",
    institution: String? = null,
    graduationYear: Int? = null,
    url: String? = "https://doi.org/10.1/x",
    evidenceFileId: String? = null,
    revision: Int = 1,
    moderationComment: String? = null,
    createdAt: Long = 1_000,
    status: String = "something",
): UserAchievementDto = UserAchievementDto(
    id = id,
    userId = ViewerRaw,
    details = UserAchievementInputDto(
        kind = kind,
        title = title,
        description = description,
        institution = institution,
        graduationYear = graduationYear,
        url = url,
        evidenceFileId = evidenceFileId,
    ),
    status = status,
    revision = revision,
    moderationComment = moderationComment,
    createdAtUnix = createdAt,
)

internal fun reviewDto(
    id: String = "r1",
    authorId: String = OtherRaw,
    authorName: String = "Елена",
    rating: Int = 5,
    createdAt: Long = 1_760_000_000,
    completedAt: Long = 1_750_000_000,
): UserReviewDto = UserReviewDto(
    id = id,
    authorUserId = authorId,
    authorDisplayName = authorName,
    authorRole = "initiator",
    projectId = "p1",
    projectTitle = "Цифровой двойник",
    targetUserId = ViewerRaw,
    title = "Отлично",
    text = "Довёл до демо",
    rating = rating,
    createdAtUnix = createdAt,
    projectCompletedAtUnix = completedAt,
)

/** [ProfilesApi] that answers as the test says; an update lays the change over the stored `details`, as the real one does. */
internal class FakeProfilesApi : ProfilesApi {
    var onMyProfile: suspend () -> UserProfileDto = { error("myProfile was not expected") }

    /** What the server has before an update, `null` for a 404. */
    var stored: UserProfileDto? = null
    var failUpdate: Throwable? = null
    val updates = mutableListOf<UserProfileInputDto>()

    override suspend fun myProfile(): UserProfileDto = onMyProfile()

    override suspend fun updateMyProfile(change: (UserProfileInputDto) -> UserProfileInputDto): UserProfileDto {
        val current = stored ?: UserProfileDto()
        val changed = change(current.details)
        failUpdate?.let { throw it }
        updates += changed
        return current.copy(details = changed).also { stored = it }
    }
}

/** [AchievementsApi] that answers as the test says, and records what it was asked. */
internal class FakeAchievementsApi : AchievementsApi {
    val calls = mutableListOf<String>()

    var onUserAchievements: suspend (userId: String, limit: Int, offset: Int) -> UserAchievementListDto =
        { _, _, _ -> UserAchievementListDto() }
    var onMyAchievements: suspend (limit: Int, offset: Int) -> UserAchievementListDto =
        { _, _ -> UserAchievementListDto() }
    var onMyAchievement: suspend (String) -> UserAchievementDto = { error("myAchievement was not expected") }
    var onCreate: suspend (UserAchievementInputDto) -> UserAchievementDto = { input ->
        achievementDto(kind = input.kind, title = input.title).copy(details = input)
    }

    /** The record the server has for an update; the change is laid over its details. */
    var stored: UserAchievementDto = achievementDto()
    var failUpdate: Throwable? = null

    /** What an update answers with instead of the stored record with the change laid over it. */
    var updateAnswer: UserAchievementDto? = null
    var onSubmit: suspend (String) -> UserAchievementDto = { achievementDto(id = it) }
    var onDelete: suspend (String) -> Unit = {}

    val created = mutableListOf<UserAchievementInputDto>()
    val updated = mutableListOf<Pair<String, UserAchievementInputDto>>()
    val updatedRevisions = mutableListOf<Int>()

    override suspend fun userAchievements(userId: String, limit: Int, offset: Int): UserAchievementListDto {
        calls += "userAchievements($userId,$limit,$offset)"
        return onUserAchievements(userId, limit, offset)
    }

    override suspend fun myAchievements(limit: Int, offset: Int): UserAchievementListDto {
        calls += "myAchievements($limit,$offset)"
        return onMyAchievements(limit, offset)
    }

    override suspend fun myAchievement(id: String): UserAchievementDto {
        calls += "myAchievement($id)"
        return onMyAchievement(id)
    }

    override suspend fun create(input: UserAchievementInputDto): UserAchievementDto {
        calls += "create"
        created += input
        return onCreate(input)
    }

    override suspend fun updateMine(
        id: String,
        expectedRevision: Int,
        change: (UserAchievementInputDto) -> UserAchievementInputDto,
    ): UserAchievementDto {
        calls += "updateMine($id)"
        updatedRevisions += expectedRevision
        failUpdate?.let { throw it }
        val changed = change(stored.details)
        updated += id to changed
        return updateAnswer ?: stored.copy(id = id, details = changed)
    }

    override suspend fun submit(id: String): UserAchievementDto {
        calls += "submit($id)"
        return onSubmit(id)
    }

    override suspend fun delete(id: String) {
        calls += "delete($id)"
        onDelete(id)
    }
}

/** [ReviewsApi] that answers as the test says, and records what it was asked. */
internal class FakeReviewsApi : ReviewsApi {
    val calls = mutableListOf<Triple<String, Int, Int>>()
    var onUserReviews: suspend (userId: String, limit: Int, offset: Int) -> UserReviewListDto =
        { _, _, _ -> UserReviewListDto() }

    override suspend fun userReviews(userId: String, limit: Int, offset: Int): UserReviewListDto {
        calls += Triple(userId, limit, offset)
        return onUserReviews(userId, limit, offset)
    }
}

/** [MediaApi] in memory: the files it was asked to make, the bytes sent to them and the files deleted. */
internal class FakeMediaApi : MediaApi {
    val calls = mutableListOf<String>()
    val created = mutableListOf<CreateMediaUploadDto>()
    val sent = mutableListOf<ByteArray>()
    val deleted = mutableListOf<String>()
    var failCreate: Throwable? = null
    var failPut: Throwable? = null
    var failDelete: Throwable? = null
    private var counter = 0

    override suspend fun createUpload(input: CreateMediaUploadDto): MediaUploadDto {
        calls += "create"
        failCreate?.let { throw it }
        created += input
        val file = MediaFileDto(id = "file-${++counter}", originalName = input.originalName, contentType = input.contentType, status = "pending", isPublic = input.isPublic)
        return MediaUploadDto(file = file, uploadUrl = "https://storage.example/${file.id}")
    }

    override suspend fun putContent(upload: MediaUploadDto, bytes: ByteArray) {
        calls += "put ${upload.file.id}"
        failPut?.let { throw it }
        sent += bytes
    }

    override suspend fun complete(fileId: String): MediaFileDto {
        calls += "complete $fileId"
        return MediaFileDto(id = fileId, status = "ready")
    }

    override suspend fun downloadUrl(fileId: String): MediaDownloadDto = error("downloadUrl was not expected")

    override suspend fun fetch(url: String): ByteArray = error("fetch was not expected")

    override suspend fun delete(fileId: String) {
        calls += "delete $fileId"
        failDelete?.let { throw it }
        deleted += fileId
    }
}
