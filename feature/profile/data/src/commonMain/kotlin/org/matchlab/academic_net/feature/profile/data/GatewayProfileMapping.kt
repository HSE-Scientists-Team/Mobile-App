package org.matchlab.academic_net.feature.profile.data

import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toLocalDateTime
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.feature.profile.domain.DocumentDraft
import org.matchlab.academic_net.feature.profile.domain.DocumentId
import org.matchlab.academic_net.feature.profile.domain.DocumentKind
import org.matchlab.academic_net.feature.profile.domain.PortfolioWork
import org.matchlab.academic_net.feature.profile.domain.ProfileDocument
import org.matchlab.academic_net.feature.profile.domain.ProfileDraft
import org.matchlab.academic_net.feature.profile.domain.ProfileInterests
import org.matchlab.academic_net.feature.profile.domain.ProofAttachment
import org.matchlab.academic_net.feature.profile.domain.Review
import org.matchlab.academic_net.feature.profile.domain.ReviewId
import org.matchlab.academic_net.feature.profile.domain.ReviewsSummary
import org.matchlab.academic_net.feature.profile.domain.WorkDraft
import org.matchlab.academic_net.feature.profile.domain.WorkId
import org.matchlab.academic_net.gateway.contract.AchievementKinds
import org.matchlab.academic_net.gateway.dto.UserAchievementDto
import org.matchlab.academic_net.gateway.dto.UserAchievementInputDto
import org.matchlab.academic_net.gateway.dto.UserAchievementListDto
import org.matchlab.academic_net.gateway.dto.UserProfileDto
import org.matchlab.academic_net.gateway.dto.UserProfileInputDto
import org.matchlab.academic_net.gateway.dto.UserReviewDto
import org.matchlab.academic_net.gateway.mapping.instantOfUnixSeconds
import org.matchlab.academic_net.gateway.mapping.ratingOrNull
import org.matchlab.academic_net.gateway.mapping.toCollaborator
import org.matchlab.academic_net.utils.linkSource

// The Gateway's shapes as the models of the profile feature, and back. Only what the API defines, or the app defines for
// itself, is read: the `kind` of an achievement (its enum), the links, the numbers, the codes of the project types of a
// profile (docs/api-values.md). The strings the server writes and the API does not define are not read at all: the status
// of an achievement is `null` (API-GAP G12), the role of the author of a review is `null` (G13). The ids are not required
// by the schema (API-GAP G55): a work, a document or a review
// without one is left out, and an author without one stays, with an empty id.

/**
 * The profile without the portfolio and the reviews. The project types are the ones whose code the server's
 * `project_types` hold ([ProjectType.fromCode], in the server's order, each once); an entry the app has no code for is
 * left out of the model (and kept on the server by [withDraft]). `open_to_collaboration` that is missing or `null` is
 * `false`.
 */
internal fun UserProfileDto.toRecord(): ProfileRecord = ProfileRecord(
    person = toCollaborator(),
    about = details.about.orEmpty().trim(),
    interests = ProfileInterests(projectTypes = details.projectTypes.knownProjectTypes(), topics = details.interests),
    skills = details.skills,
    reviews = ReviewsSummary(average = ratingOrNull(), count = reviewCount, positiveCount = positiveReviewCount),
    openToCollaboration = details.openToCollaboration == true,
)

/**
 * The profile as it is after [draft], laid over what the server has: the name, the university, the study, the field, the
 * text about the person, the project types, the interests and the skills come from the form (a blank text clears the
 * field); the avatar, the contacts and `open_to_collaboration` are not the form's and stay as the server sent them.
 * docs/api-values.md: the level of study is the text as typed. The project types are written as codes by [withSelectedTypes].
 */
internal fun UserProfileInputDto.withDraft(draft: ProfileDraft): UserProfileInputDto = copy(
    displayName = draft.name,
    university = draft.university.nullIfBlank(),
    studyLevel = draft.studyLevel.nullIfBlank(),
    studyYear = draft.studyYearNumber,
    specialization = draft.field.nullIfBlank(),
    about = draft.about.nullIfBlank(),
    projectTypes = projectTypes.withSelectedTypes(draft.interestTypes),
    interests = draft.interests,
    skills = draft.skills,
)

/**
 * The `project_types` of a profile after a save: the entries the app has no code for first, exactly as the server sent them
 * (another client may have written them; they are not the person's to lose), then the codes of the [selected] types in
 * the order of [ProjectType]; each entry once. An entry that is a code of a type the app knows (also with outer spaces)
 * is replaced by the selection: a type that was cleared is gone.
 */
internal fun List<String>.withSelectedTypes(selected: Set<ProjectType>): List<String> =
    (filter { ProjectType.fromCode(it) == null } + ProjectType.entries.filter { it in selected }.map { it.code })
        .distinct()

/** The types of the codes in the list, in its order, each once; entries that are not the code of a type are left out. */
private fun List<String>.knownProjectTypes(): Set<ProjectType> = mapNotNull { ProjectType.fromCode(it) }.toSet()

/**
 * The work, or `null` when the record is not one (its `kind` is not [AchievementKinds.Works]) or it has no `id`
 * (API-GAP G55: the schema does not require it, and a work without one can be neither opened nor changed).
 * API-GAP G12: no status.
 */
internal fun UserAchievementDto.toWork(): PortfolioWork? {
    val type = when (details.kind) {
        AchievementKinds.Scientific -> ProjectType.Scientific
        AchievementKinds.Project -> ProjectType.Practical
        else -> return null
    }
    if (id.isBlank()) return null // API-GAP G55
    val link = details.url.trimmedOrNull()
    return PortfolioWork(
        id = WorkId(id),
        type = type,
        title = details.title.trim(),
        description = details.description.orEmpty().trim(),
        link = link,
        source = linkSource(link),
        status = null, // API-GAP G12: the API does not define the statuses of an achievement
        moderationComment = moderationComment.trimmedOrNull(),
        hasEvidenceFile = !details.evidenceFileId.isNullOrBlank(),
        revision = revision,
    )
}

/**
 * The document, or `null` when the record is not one (its `kind` is not [AchievementKinds.Documents]) or it has no
 * `id` (API-GAP G55: the schema does not require it, and a document without one can be neither opened nor changed).
 * API-GAP G12: no status.
 */
internal fun UserAchievementDto.toDocument(): ProfileDocument? {
    val kind = when (details.kind) {
        AchievementKinds.Education -> DocumentKind.Education
        AchievementKinds.Certificate -> DocumentKind.Certificate
        else -> return null
    }
    if (id.isBlank()) return null // API-GAP G55
    return ProfileDocument(
        id = DocumentId(id),
        kind = kind,
        title = details.title.trim(),
        issuer = details.institution.orEmpty().trim(),
        year = details.graduationYear?.takeIf { it > 0 },
        proof = details.url.trimmedOrNull()?.let(::ProofAttachment),
        status = null, // API-GAP G12: the API does not define the statuses of an achievement
        moderationComment = moderationComment.trimmedOrNull(),
        hasEvidenceFile = !details.evidenceFileId.isNullOrBlank(),
        revision = revision,
    )
}

/**
 * The works, newest first by the time they were added, and the documents in the order of the list; the records of
 * other kinds, and those without an `id` (API-GAP G55), are left out.
 */
internal fun UserAchievementListDto.toPortfolio(): Portfolio = Portfolio(
    works = items.sortedByDescending { it.createdAtUnix }.mapNotNull { it.toWork() },
    documents = items.mapNotNull { it.toDocument() },
)

/**
 * The review, or `null` when it has no `id` (API-GAP G55: the schema does not require it, and a review without one
 * cannot be found again by the screen of one review). A review without `author_user_id` stays, with an author whose id
 * is empty: the name is shown, but there is no profile to open and nobody to write to.
 *
 * API-GAP G13: the role of the author is not read (`null`). API-GAP G15: the author is not known to be verified. The
 * month of the end of the project is the one it was in the time zone of the device (`null` for a date the API does
 * not give or gives out of reason, see `instantOfUnixSeconds`); the rating is held to 0..5.
 */
internal fun UserReviewDto.toReview(timeZone: TimeZone): Review? {
    if (id.isBlank()) return null // API-GAP G55
    return Review(
        id = ReviewId(id),
        // API-GAP G55: a blank `author_user_id` is the empty id, whatever the blanks were.
        // API-GAP G15: the API does not say whether the author is verified. API-GAP G18: no avatar.
        author = UserSummary(
            UserId(authorUserId.takeIf { it.isNotBlank() }.orEmpty()),
            name = authorDisplayName.trim(),
            avatarUrl = null,
            isVerified = false,
        ),
        authorRole = null, // API-GAP G13: the API does not define the roles of the author of a review
        projectTitle = projectTitle.trim(),
        projectFinished = instantOfUnixSeconds(projectCompletedAtUnix)?.inMonthOf(timeZone),
        title = title.trim(),
        rating = rating.coerceIn(0, 5),
        text = text.trim(),
    )
}

/** The body of the request that adds a work. */
internal fun WorkDraft.toInput(): UserAchievementInputDto = UserAchievementInputDto(
    kind = type.toKind(),
    title = title,
    description = description.nullIfBlank(),
    url = link.nullIfBlank(),
)

/** The body of the request that adds a document; its proof is the public `url`. */
internal fun DocumentDraft.toInput(): UserAchievementInputDto = UserAchievementInputDto(
    kind = kind.toKind(),
    title = title,
    institution = issuer.nullIfBlank(),
    graduationYear = yearNumber,
    url = proof?.url.nullIfBlank(),
)

/** The record as it is after [WorkDraft], laid over what the server has (the proof file and the like stay). */
internal fun UserAchievementInputDto.withDraft(draft: WorkDraft): UserAchievementInputDto = copy(
    kind = draft.type.toKind(),
    title = draft.title,
    description = draft.description.nullIfBlank(),
    url = draft.link.nullIfBlank(),
)

/** The record as it is after [DocumentDraft], laid over what the server has (the proof file and the like stay). */
internal fun UserAchievementInputDto.withDraft(draft: DocumentDraft): UserAchievementInputDto = copy(
    kind = draft.kind.toKind(),
    title = draft.title,
    institution = draft.issuer.nullIfBlank(),
    graduationYear = draft.yearNumber,
    url = draft.proof?.url.nullIfBlank(),
)

private fun ProjectType.toKind(): String = when (this) {
    ProjectType.Scientific -> AchievementKinds.Scientific
    ProjectType.Practical -> AchievementKinds.Project
}

private fun DocumentKind.toKind(): String = when (this) {
    DocumentKind.Education -> AchievementKinds.Education
    DocumentKind.Certificate -> AchievementKinds.Certificate
}

private fun Instant.inMonthOf(timeZone: TimeZone): YearMonth {
    val date = toLocalDateTime(timeZone).date
    return YearMonth(date.year, date.month)
}

private fun String?.trimmedOrNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

private fun String?.nullIfBlank(): String? = this?.takeIf { it.isNotBlank() }
