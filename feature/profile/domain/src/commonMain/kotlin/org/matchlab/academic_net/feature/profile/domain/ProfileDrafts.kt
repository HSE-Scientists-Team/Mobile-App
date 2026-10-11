package org.matchlab.academic_net.feature.profile.domain

import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.utils.isWebLink
import org.matchlab.academic_net.utils.normalizeTag
import org.matchlab.academic_net.utils.normalizeTags

/** Limits of the profile forms. The design does not fix them: these are the app's own. */
object ProfileLimits {
    const val MaxAboutLength = 1000
    const val MaxTitleLength = 150
    const val MaxDescriptionLength = 500
    const val MaxIssuerLength = 100

    /** The most interests, and the most skills, a profile may have. */
    const val MaxTags = 20

    /** The earliest and the latest year a document may be issued in. */
    const val MinYear = 1950
    const val MaxYear = 2100
}

/** What is wrong with an [AboutDraft]; each entry is a limit of [ProfileLimits]. */
enum class AboutDraftError {
    /** The text about the person is longer than [ProfileLimits.MaxAboutLength]. */
    AboutTooLong,

    /** More interests than [ProfileLimits.MaxTags]. */
    TooManyInterests,

    /** More skills than [ProfileLimits.MaxTags]. */
    TooManySkills,
}

/**
 * "Редактирование профиля": the text about the person, their interests and skills as the user
 * has filled them. Everything is optional, so only the limits are checked.
 */
data class AboutDraft(
    val about: String = "",
    val interestTypes: Set<ProjectType> = emptySet(),
    val interests: List<String> = emptyList(),
    val skills: List<String> = emptyList(),
) {
    /** Outer spaces trimmed, tags normalized and deduplicated. */
    fun normalized(): AboutDraft = copy(
        about = about.trim(),
        interests = interests.normalizeTags(),
        skills = skills.normalizeTags(),
    )

    /** Errors of the draft as typed (call on [normalized] to ignore stray spaces). */
    fun validate(): Set<AboutDraftError> = buildSet {
        if (about.length > ProfileLimits.MaxAboutLength) add(AboutDraftError.AboutTooLong)
        if (interests.size > ProfileLimits.MaxTags) add(AboutDraftError.TooManyInterests)
        if (skills.size > ProfileLimits.MaxTags) add(AboutDraftError.TooManySkills)
    }
}

/** What is wrong with a [WorkDraft]; the title and the description are required. */
enum class WorkDraftError {
    TitleEmpty,

    /** Longer than [ProfileLimits.MaxTitleLength]. */
    TitleTooLong,
    DescriptionEmpty,

    /** Longer than [ProfileLimits.MaxDescriptionLength]. */
    DescriptionTooLong,

    /** The link is not an http/https address. */
    LinkInvalid,

    /** The attachment is not an http/https address. */
    ProofInvalid,
}

/** "Добавить работу" as the user has filled it so far. */
data class WorkDraft(
    val type: ProjectType = ProjectType.Scientific,
    val title: String = "",
    val description: String = "",
    /** Address of the publication or the repository; blank if there is none. */
    val link: String = "",
    val proof: ProofAttachment? = null,
) {
    /**
     * With a link or a proof the work goes to moderation, otherwise it is added as unconfirmed
     * ("Добавить без подтверждения").
     */
    val sendsForVerification: Boolean get() = link.isNotBlank() || proof != null

    fun normalized(): WorkDraft = copy(
        title = title.normalizeTag(),
        description = description.trim(),
        link = link.trim(),
        proof = proof?.copy(url = proof.url.trim()),
    )

    /** Errors of the draft as typed (call on [normalized] to ignore stray spaces). */
    fun validate(): Set<WorkDraftError> = buildSet {
        if (title.isBlank()) add(WorkDraftError.TitleEmpty)
        if (title.length > ProfileLimits.MaxTitleLength) add(WorkDraftError.TitleTooLong)
        if (description.isBlank()) add(WorkDraftError.DescriptionEmpty)
        if (description.length > ProfileLimits.MaxDescriptionLength) add(WorkDraftError.DescriptionTooLong)
        if (link.isNotBlank() && !link.isWebLink()) add(WorkDraftError.LinkInvalid)
        if (proof != null && !proof.url.isWebLink()) add(WorkDraftError.ProofInvalid)
    }
}

/** What is wrong with a [DocumentDraft]; the title, the issuer and the proof are required. */
enum class DocumentDraftError {
    TitleEmpty,

    /** Longer than [ProfileLimits.MaxTitleLength]. */
    TitleTooLong,
    IssuerEmpty,

    /** Longer than [ProfileLimits.MaxIssuerLength]. */
    IssuerTooLong,

    /** The year is not a number between [ProfileLimits.MinYear] and [ProfileLimits.MaxYear]. */
    YearInvalid,

    /** A document is always checked, so it needs a proof. */
    ProofMissing,

    /** The attachment is not an http/https address. */
    ProofInvalid,
}

/** The "new document" form as the user has filled it so far. */
data class DocumentDraft(
    val kind: DocumentKind = DocumentKind.Education,
    val title: String = "",
    val issuer: String = "",
    /** As typed; blank if the user did not give it. */
    val year: String = "",
    val proof: ProofAttachment? = null,
) {
    /** The year as a number, `null` while it is blank or not a valid year (see [validate]). */
    val yearNumber: Int?
        get() = year.trim()
            .takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
            ?.toIntOrNull()
            ?.takeIf { it in ProfileLimits.MinYear..ProfileLimits.MaxYear }

    fun normalized(): DocumentDraft = copy(
        title = title.normalizeTag(),
        issuer = issuer.normalizeTag(),
        year = year.trim(),
        proof = proof?.copy(url = proof.url.trim()),
    )

    /** Errors of the draft as typed (call on [normalized] to ignore stray spaces). */
    fun validate(): Set<DocumentDraftError> = buildSet {
        if (title.isBlank()) add(DocumentDraftError.TitleEmpty)
        if (title.length > ProfileLimits.MaxTitleLength) add(DocumentDraftError.TitleTooLong)
        if (issuer.isBlank()) add(DocumentDraftError.IssuerEmpty)
        if (issuer.length > ProfileLimits.MaxIssuerLength) add(DocumentDraftError.IssuerTooLong)
        if (year.isNotBlank() && yearNumber == null) add(DocumentDraftError.YearInvalid)
        if (proof == null) {
            add(DocumentDraftError.ProofMissing)
        } else if (!proof.url.isWebLink()) {
            add(DocumentDraftError.ProofInvalid)
        }
    }
}
