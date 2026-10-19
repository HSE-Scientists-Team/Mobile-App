package org.matchlab.academic_net.feature.profile.domain

import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.utils.isWebLink
import org.matchlab.academic_net.utils.normalizeTag
import org.matchlab.academic_net.utils.normalizeTags

/** Limits of the profile forms. The design does not fix them: these are the app's own. */
object ProfileLimits {
    const val MaxNameLength = 100
    const val MaxUniversityLength = 150
    const val MaxFieldLength = 100

    /** The level of study is a free text (docs/api-values.md): a word or two. */
    const val MaxStudyLevelLength = 50

    /**
     * The first year of study a person may name. There is no last one: the number is the server's to judge, so a
     * programme of more than 6 years, or a postgraduate year, is not refused here (`docs/api-values.md`, §3).
     */
    const val MinStudyYear = 1

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

/** What is wrong with a [ProfileDraft]; each entry is a limit of [ProfileLimits], and the name is required. */
enum class ProfileDraftError {
    /** The name is blank: the API requires it, and a new account has none until the first save. */
    NameEmpty,

    /** Longer than [ProfileLimits.MaxNameLength]. */
    NameTooLong,

    /** Longer than [ProfileLimits.MaxUniversityLength]. */
    UniversityTooLong,

    /** Longer than [ProfileLimits.MaxStudyLevelLength]. */
    StudyLevelTooLong,

    /** The year is not a whole number of at least [ProfileLimits.MinStudyYear] (there is no upper limit). */
    StudyYearInvalid,

    /** Longer than [ProfileLimits.MaxFieldLength]. */
    FieldTooLong,

    /** The text about the person is longer than [ProfileLimits.MaxAboutLength]. */
    AboutTooLong,

    /** More interests than [ProfileLimits.MaxTags]. */
    TooManyInterests,

    /** More skills than [ProfileLimits.MaxTags]. */
    TooManySkills,
}

/**
 * "Редактирование профиля" as the user has filled it: who they are, what they study, the text about them, their
 * interests and skills. Only the name is required.
 *
 * docs/api-values.md: [studyLevel] is a free text, typed by the person, and goes to the server as it is. [interestTypes] is
 * written as the codes of the types (`docs/api-values.md`); the entries of the server's `project_types` that the app
 * does not know are kept next to them.
 */
data class ProfileDraft(
    val name: String = "",
    val university: String = "",
    /** The level of study as the person writes it ("Бакалавриат"); blank if they give none. */
    val studyLevel: String = "",
    /** The year of study as typed; blank if the person gives none. */
    val studyYear: String = "",
    /** Field of study: "Computer Science". */
    val field: String = "",
    val about: String = "",
    val interestTypes: Set<ProjectType> = emptySet(),
    val interests: List<String> = emptyList(),
    val skills: List<String> = emptyList(),
) {
    /** The year as a number, `null` while it is blank or not a valid year (see [validate]). */
    val studyYearNumber: Int?
        get() = studyYear.trim()
            .takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
            ?.toIntOrNull()
            ?.takeIf { it >= ProfileLimits.MinStudyYear }

    /** Outer spaces trimmed, one-line texts and tags normalized, tags deduplicated. */
    fun normalized(): ProfileDraft = copy(
        name = name.normalizeTag(),
        university = university.normalizeTag(),
        studyLevel = studyLevel.normalizeTag(),
        studyYear = studyYear.trim(),
        field = field.normalizeTag(),
        about = about.trim(),
        interests = interests.normalizeTags(),
        skills = skills.normalizeTags(),
    )

    /** Errors of the draft as typed (call on [normalized] to ignore stray spaces). */
    fun validate(): Set<ProfileDraftError> = buildSet {
        if (name.isBlank()) add(ProfileDraftError.NameEmpty)
        if (name.length > ProfileLimits.MaxNameLength) add(ProfileDraftError.NameTooLong)
        if (university.length > ProfileLimits.MaxUniversityLength) add(ProfileDraftError.UniversityTooLong)
        if (studyLevel.length > ProfileLimits.MaxStudyLevelLength) add(ProfileDraftError.StudyLevelTooLong)
        if (studyYear.isNotBlank() && studyYearNumber == null) add(ProfileDraftError.StudyYearInvalid)
        if (field.length > ProfileLimits.MaxFieldLength) add(ProfileDraftError.FieldTooLong)
        if (about.length > ProfileLimits.MaxAboutLength) add(ProfileDraftError.AboutTooLong)
        if (interests.size > ProfileLimits.MaxTags) add(ProfileDraftError.TooManyInterests)
        if (skills.size > ProfileLimits.MaxTags) add(ProfileDraftError.TooManySkills)
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
}

/**
 * "Добавить работу" and the form of a work being edited, as the user has filled it so far. There is no file to
 * attach yet: the proof of a work is its [link]. A work that came from the server with a proof file keeps it
 * ([hasEvidenceFile]).
 */
data class WorkDraft(
    val type: ProjectType = ProjectType.Scientific,
    val title: String = "",
    val description: String = "",
    /** Address of the publication or the repository; blank if there is none. */
    val link: String = "",
    /** The record being edited has a proof file on the server; the form shows it and the save keeps it. */
    val hasEvidenceFile: Boolean = false,
) {
    /**
     * With a link or a proof file the work can be sent for verification, otherwise it is added as unconfirmed
     * ("Добавить без подтверждения").
     */
    val sendsForVerification: Boolean get() = link.isNotBlank() || hasEvidenceFile

    fun normalized(): WorkDraft = copy(
        title = title.normalizeTag(),
        description = description.trim(),
        link = link.trim(),
    )

    /** Errors of the draft as typed (call on [normalized] to ignore stray spaces). */
    fun validate(): Set<WorkDraftError> = buildSet {
        if (title.isBlank()) add(WorkDraftError.TitleEmpty)
        if (title.length > ProfileLimits.MaxTitleLength) add(WorkDraftError.TitleTooLong)
        if (description.isBlank()) add(WorkDraftError.DescriptionEmpty)
        if (description.length > ProfileLimits.MaxDescriptionLength) add(WorkDraftError.DescriptionTooLong)
        if (link.isNotBlank() && !link.isWebLink()) add(WorkDraftError.LinkInvalid)
    }
}

/** What is wrong with a [DocumentDraft]; the title, the issuer and a proof are required. */
enum class DocumentDraftError {
    TitleEmpty,

    /** Longer than [ProfileLimits.MaxTitleLength]. */
    TitleTooLong,
    IssuerEmpty,

    /** Longer than [ProfileLimits.MaxIssuerLength]. */
    IssuerTooLong,

    /** The year is not a number between [ProfileLimits.MinYear] and [ProfileLimits.MaxYear]. */
    YearInvalid,

    /** A document is always checked, so it needs a proof: a link, or a file that is already attached. */
    ProofMissing,

    /** The attachment is not an http/https address. */
    ProofInvalid,
}

/**
 * The "new document" form and the form of a document being edited, as the user has filled it so far. The proof is
 * a link (it is public, API-GAP G17); a document that came from the server with a proof file keeps it
 * ([hasEvidenceFile]).
 */
data class DocumentDraft(
    val kind: DocumentKind = DocumentKind.Education,
    val title: String = "",
    val issuer: String = "",
    /** As typed; blank if the user did not give it. */
    val year: String = "",
    val proof: ProofAttachment? = null,
    /** The record being edited has a proof file on the server; the form shows it and the save keeps it. */
    val hasEvidenceFile: Boolean = false,
) {
    /** The year as a number, `null` while it is blank or not a valid year (see [validate]). */
    val yearNumber: Int?
        get() = year.trim()
            .takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
            ?.toIntOrNull()
            ?.takeIf { it in ProfileLimits.MinYear..ProfileLimits.MaxYear }

    /** A document is sent for verification when it has a proof; without one it is not valid (see [validate]). */
    val sendsForVerification: Boolean get() = proof != null || hasEvidenceFile

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
            if (!hasEvidenceFile) add(DocumentDraftError.ProofMissing)
        } else if (!proof.url.isWebLink()) {
            add(DocumentDraftError.ProofInvalid)
        }
    }
}

/** The form of the viewer's profile as it stands now: what the editor opens with. */
fun MyProfile.toDraft(): ProfileDraft = ProfileDraft(
    name = profile.person.user.name,
    university = profile.person.university,
    studyLevel = profile.person.study.level.orEmpty(),
    studyYear = profile.person.study.year?.toString().orEmpty(),
    field = profile.person.field,
    about = profile.about,
    interestTypes = profile.interests.projectTypes,
    interests = profile.interests.topics,
    skills = profile.skills,
)

/** The form of a work being edited, with the fields as the server has them. */
fun PortfolioWork.toDraft(): WorkDraft = WorkDraft(
    type = type,
    title = title,
    description = description,
    link = link.orEmpty(),
    hasEvidenceFile = hasEvidenceFile,
)

/** The form of a document being edited, with the fields as the server has them. */
fun ProfileDocument.toDraft(): DocumentDraft = DocumentDraft(
    kind = kind,
    title = title,
    issuer = issuer,
    year = year?.toString().orEmpty(),
    proof = proof,
    hasEvidenceFile = hasEvidenceFile,
)
