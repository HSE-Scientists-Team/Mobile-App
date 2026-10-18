package org.matchlab.academic_net.gateway

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.matchlab.academic_net.common.model.Study
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.gateway.dto.UserAchievementDto
import org.matchlab.academic_net.gateway.dto.UserAchievementInputDto
import org.matchlab.academic_net.gateway.dto.UserAchievementListDto
import org.matchlab.academic_net.gateway.dto.UserProfileDto
import org.matchlab.academic_net.gateway.dto.UserProfileInputDto
import org.matchlab.academic_net.gateway.dto.UserReviewListDto
import org.matchlab.academic_net.gateway.internal.GatewayJson
import org.matchlab.academic_net.gateway.mapping.instantOfUnixSeconds
import org.matchlab.academic_net.gateway.mapping.ratingOrNull
import org.matchlab.academic_net.gateway.mapping.toCollaborator
import org.matchlab.academic_net.gateway.mapping.toUserSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** The shapes of the profile calls as the server writes them, and the translation of a profile into the models of `:common`. */
class ProfileDtoTest {
    private fun profile(json: String): UserProfileDto = GatewayJson.decodeFromString(json)

    // ---- reading a profile ----

    @Test
    fun aProfileIsReadWithEveryField() {
        val dto = profile(ProfileSpec.Profile)

        assertEquals(ProfileSpec.MeId, dto.userId)
        assertTrue(dto.verified)
        assertEquals(4.8, dto.rating)
        assertEquals(5, dto.reviewCount)
        assertEquals(2, dto.completedProjectCount)
        assertEquals(4, dto.positiveReviewCount)
        val details = dto.details
        assertEquals("Арджун Мехта", details.displayName)
        assertEquals("file-1", details.avatarFileId)
        assertEquals("НИУ ВШЭ", details.university)
        assertEquals("бакалавриат", details.studyLevel)
        assertEquals(3, details.studyYear)
        assertEquals("Computer Science", details.specialization)
        assertEquals("Люблю данные", details.about)
        assertEquals(true, details.openToCollaboration)
        assertEquals(listOf("something-the-client-does-not-know"), details.projectTypes)
        assertEquals(listOf("ML"), details.interests)
        assertEquals(listOf("Kotlin", "SQL"), details.skills)
        assertEquals("arjun@hse.ru", details.contactEmail)
        assertEquals("@arjun", details.telegram)
    }

    @Test
    fun aRatingComesAsAnIntegerOrAsADecimal() {
        assertEquals(4.0, profile("""{"rating":4}""").rating)
        assertEquals(4.8, profile("""{"rating":4.8}""").rating)
        assertEquals(0.0, profile("""{"rating":0}""").rating)
    }

    @Test
    fun anEmptyObjectIsAnEmptyProfile() {
        assertEquals(UserProfileDto(), profile("{}"))
    }

    @Test
    fun leftOutFieldsTakeTheirDefaults() {
        val dto = profile("""{"user_id":"u","details":{"display_name":"Имя"}}""")

        assertEquals("u", dto.userId)
        assertEquals("Имя", dto.details.displayName)
        assertNull(dto.details.studyLevel)
        assertNull(dto.details.studyYear)
        assertNull(dto.details.openToCollaboration)
        assertEquals(emptyList(), dto.details.skills)
        assertFalse(dto.verified)
        assertEquals(0, dto.reviewCount)
    }

    @Test
    fun nullInsteadOfAnArrayIsAnEmptyList() {
        val details = profile("""{"details":{"display_name":"Имя","project_types":null,"interests":null,"skills":null}}""").details

        assertEquals(emptyList(), details.projectTypes)
        assertEquals(emptyList(), details.interests)
        assertEquals(emptyList(), details.skills)
    }

    @Test
    fun nullInsteadOfTheDetailsOrTheNumbersTakesTheDefaults() {
        val dto = profile("""{"details":null,"rating":null,"review_count":null,"verified":null}""")

        assertEquals(UserProfileInputDto(), dto.details)
        assertEquals(0.0, dto.rating)
        assertEquals(0, dto.reviewCount)
        assertFalse(dto.verified)
    }

    @Test
    fun keysTheClientDoesNotKnowAreSkipped() {
        val dto = profile("""{"user_id":"u","brand_new":{"a":1},"details":{"display_name":"Имя","pronouns":"они"}}""")

        assertEquals("Имя", dto.details.displayName)
    }

    // ---- writing a profile ----

    @Test
    fun theInputIsWrittenWithTheNamesOfTheSpecAndWithoutNulls() {
        val json = GatewayJson.encodeToString(
            UserProfileInputDto(
                displayName = "Имя",
                studyLevel = "магистратура",
                studyYear = 1,
                specialization = "Biology",
                openToCollaboration = false,
                skills = listOf("R"),
            ),
        )

        assertEquals(
            """{"display_name":"Имя","study_level":"магистратура","study_year":1,"specialization":"Biology",""" +
                """"open_to_collaboration":false,"project_types":[],"interests":[],"skills":["R"]}""",
            json,
        )
    }

    // ---- achievements and reviews ----

    @Test
    fun anAchievementIsReadWithItsDetailsAndTheServersState() {
        val dto = GatewayJson.decodeFromString<UserAchievementDto>(ProfileSpec.achievement())

        assertEquals("ach-1", dto.id)
        assertEquals("scientific", dto.details.kind)
        assertEquals("Статья о графах", dto.details.title)
        assertEquals(2025, dto.details.graduationYear)
        assertEquals("file-9", dto.details.evidenceFileId)
        assertEquals(3, dto.revision)
        assertEquals("whatever", dto.status)
        assertEquals("Нужна ссылка", dto.moderationComment)
    }

    @Test
    fun theTimeOfAnAchievementComesAsAStringOrAsANumber() {
        val asString = GatewayJson.decodeFromString<UserAchievementDto>(ProfileSpec.achievement(createdAt = "\"1760000000\""))
        val asNumber = GatewayJson.decodeFromString<UserAchievementDto>(ProfileSpec.achievement(createdAt = "1760000000"))

        assertEquals(1_760_000_000L, asString.createdAtUnix)
        assertEquals(asString.createdAtUnix, asNumber.createdAtUnix)
    }

    @Test
    fun anAchievementWithoutDetailsOrTimeIsEmpty() {
        val dto = GatewayJson.decodeFromString<UserAchievementDto>("""{"id":"x"}""")

        assertEquals(UserAchievementInputDto(), dto.details)
        assertEquals(0L, dto.createdAtUnix)
        assertNull(dto.moderationComment)
    }

    @Test
    fun aListWithoutItemsIsEmpty() {
        assertEquals(emptyList(), GatewayJson.decodeFromString<UserAchievementListDto>("{}").items)
        assertEquals(emptyList(), GatewayJson.decodeFromString<UserAchievementListDto>("""{"items":null}""").items)
        assertEquals(emptyList(), GatewayJson.decodeFromString<UserReviewListDto>("{}").items)
        assertEquals(emptyList(), GatewayJson.decodeFromString<UserReviewListDto>("""{"items":null}""").items)
    }

    // ---- the mapping to :common ----

    @Test
    fun theSummaryIsTheIdTheTrimmedNameAndTheVerification() {
        val summary = profile(
            """{"user_id":"u1","verified":true,"details":{"display_name":"  Арджун Мехта "}}""",
        ).toUserSummary()

        assertEquals(UserId("u1"), summary.id)
        assertEquals("Арджун Мехта", summary.name)
        assertTrue(summary.isVerified)
        // API-GAP G18: no avatar from the API.
        assertNull(summary.avatarUrl)
    }

    @Test
    fun aNewAccountHasAnEmptyName() {
        assertEquals("", profile("""{"user_id":"u1"}""").toUserSummary().name)
    }

    @Test
    fun aCollaboratorTakesTheUniversityTheStudyAndTheField() {
        val collaborator = profile(ProfileSpec.Profile).toCollaborator()

        assertEquals("Арджун Мехта", collaborator.user.name)
        assertEquals("НИУ ВШЭ", collaborator.university)
        assertEquals(Study(level = "бакалавриат", year = 3), collaborator.study)
        assertEquals("Computer Science", collaborator.field)
    }

    @Test
    fun theStudyLevelIsTheTextOfTheServerTrimmedAndABlankOneIsNone() {
        fun levelOf(level: String?): String? =
            UserProfileDto(details = UserProfileInputDto(displayName = "n", studyLevel = level)).toCollaborator().study.level

        assertEquals("Master", levelOf("  Master "))
        assertEquals("что-то своё", levelOf("что-то своё"))
        assertNull(levelOf("   "))
        assertNull(levelOf(""))
        assertNull(levelOf(null))
    }

    @Test
    fun aYearOfStudyBelowOneIsNotAYear() {
        fun yearOf(year: Int?): Int? =
            UserProfileDto(details = UserProfileInputDto(displayName = "n", studyYear = year)).toCollaborator().study.year

        assertEquals(1, yearOf(1))
        assertEquals(6, yearOf(6))
        assertNull(yearOf(0))
        assertNull(yearOf(-2))
        assertNull(yearOf(null))
    }

    @Test
    fun aCollaboratorWithoutUniversityOrFieldHasEmptyTexts() {
        val collaborator = profile("""{"user_id":"u","details":{"display_name":"Имя"}}""").toCollaborator()

        assertEquals("", collaborator.university)
        assertEquals("", collaborator.field)
        assertEquals(Study(), collaborator.study)
    }

    @Test
    fun theRatingIsKnownOnlyWhenThereAreReviews() {
        assertEquals(4.8, profile("""{"rating":4.8,"review_count":5}""").ratingOrNull())
        assertEquals(1.0, profile("""{"rating":1,"review_count":1}""").ratingOrNull())
        // API-GAP G20: what the API sends as the rating of a person without reviews is not defined.
        assertNull(profile("""{"rating":4.8,"review_count":0}""").ratingOrNull())
        assertNull(profile("{}").ratingOrNull())
    }

    @Test
    fun aUnixTimeIsAMomentOfTheEpochExceptZeroAndBelow() {
        assertEquals(Instant.fromEpochSeconds(1_760_000_000), instantOfUnixSeconds(1_760_000_000))
        assertEquals(Instant.fromEpochSeconds(1), instantOfUnixSeconds(1))
        // Protobuf JSON leaves a zero out, and a date of 1970 is not a date of this app's data.
        assertNull(instantOfUnixSeconds(0))
        assertNull(instantOfUnixSeconds(-1))
        assertNull(instantOfUnixSeconds(Long.MIN_VALUE))
    }
}
