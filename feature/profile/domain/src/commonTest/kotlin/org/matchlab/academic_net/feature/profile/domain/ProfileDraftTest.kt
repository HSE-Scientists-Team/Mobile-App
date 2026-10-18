package org.matchlab.academic_net.feature.profile.domain

import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.Study
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun text(length: Int) = "a".repeat(length)

/** "10b Редактирование профиля" as a draft: only the name is required, the rest has limits. */
class ProfileDraftTest {
    private val valid = ProfileDraft(name = "Арджун Мехта")

    @Test
    fun anEmptyDraftLacksTheNameOnly() {
        assertEquals(setOf(ProfileDraftError.NameEmpty), ProfileDraft().validate())
    }

    @Test
    fun aBlankNameIsNoName() {
        assertEquals(setOf(ProfileDraftError.NameEmpty), valid.copy(name = "   ").validate())
        assertEquals(setOf(ProfileDraftError.NameEmpty), valid.copy(name = "\n\t").validate())
    }

    @Test
    fun aDraftWithOnlyANameIsValid() {
        assertEquals(emptySet(), valid.validate())
    }

    @Test
    fun theTextsHaveTheirLimitsInclusive() {
        val atTheLimit = valid.copy(
            name = text(ProfileLimits.MaxNameLength),
            university = text(ProfileLimits.MaxUniversityLength),
            studyLevel = text(ProfileLimits.MaxStudyLevelLength),
            field = text(ProfileLimits.MaxFieldLength),
            about = text(ProfileLimits.MaxAboutLength),
        )
        assertEquals(emptySet(), atTheLimit.validate())

        val overTheLimit = valid.copy(
            name = text(ProfileLimits.MaxNameLength + 1),
            university = text(ProfileLimits.MaxUniversityLength + 1),
            studyLevel = text(ProfileLimits.MaxStudyLevelLength + 1),
            field = text(ProfileLimits.MaxFieldLength + 1),
            about = text(ProfileLimits.MaxAboutLength + 1),
        )
        assertEquals(
            setOf(
                ProfileDraftError.NameTooLong,
                ProfileDraftError.UniversityTooLong,
                ProfileDraftError.StudyLevelTooLong,
                ProfileDraftError.FieldTooLong,
                ProfileDraftError.AboutTooLong,
            ),
            overTheLimit.validate(),
        )
    }

    @Test
    fun theTagsHaveTheirLimit() {
        val twenty = List(ProfileLimits.MaxTags) { "tag$it" }

        assertEquals(emptySet(), valid.copy(interests = twenty, skills = twenty).validate())
        assertEquals(
            setOf(ProfileDraftError.TooManyInterests, ProfileDraftError.TooManySkills),
            valid.copy(interests = twenty + "one more", skills = twenty + "one more").validate(),
        )
    }

    @Test
    fun theYearOfStudyIsOptionalAndBetweenOneAndSix() {
        assertEquals(emptySet(), valid.copy(studyYear = "").validate())
        assertEquals(emptySet(), valid.copy(studyYear = "  ").validate())
        for (year in 1..6) assertEquals(emptySet(), valid.copy(studyYear = year.toString()).validate(), "year $year")
        for (year in listOf("0", "7", "10", "-1", "+3", "abc", "3.5", "1 2", "99999999999999999999")) {
            assertEquals(setOf(ProfileDraftError.StudyYearInvalid), valid.copy(studyYear = year).validate(), year)
        }
    }

    @Test
    fun theYearOfStudyAsANumber() {
        assertEquals(3, valid.copy(studyYear = "3").studyYearNumber)
        assertEquals(4, valid.copy(studyYear = " 4 ").studyYearNumber)
        assertNull(valid.copy(studyYear = "").studyYearNumber)
        assertNull(valid.copy(studyYear = "7").studyYearNumber)
        assertNull(valid.copy(studyYear = "x").studyYearNumber)
    }

    @Test
    fun normalizingCleansTheLinesAndTheTags() {
        val normalized = ProfileDraft(
            name = "  Арджун   Мехта ",
            university = " НИУ  ВШЭ ",
            studyLevel = "  бакалавриат ",
            studyYear = " 3 ",
            field = "  Computer   Science ",
            about = "  Текст \n о себе \n",
            interests = listOf(" ML ", "ml", "NLP", "  "),
            skills = listOf("Kotlin", "  kotlin  ", "SQL"),
        ).normalized()

        assertEquals("Арджун Мехта", normalized.name)
        assertEquals("НИУ ВШЭ", normalized.university)
        assertEquals("бакалавриат", normalized.studyLevel)
        assertEquals("3", normalized.studyYear)
        assertEquals("Computer Science", normalized.field)
        // The text about the person is trimmed at the ends only: its line breaks are the person's.
        assertEquals("Текст \n о себе", normalized.about)
        assertEquals(listOf("ML", "NLP"), normalized.interests)
        assertEquals(listOf("Kotlin", "SQL"), normalized.skills)
    }

    @Test
    fun aDraftOfSpacesBecomesAValidOneOnlyIfTheNameSurvives() {
        assertEquals(setOf(ProfileDraftError.NameEmpty), ProfileDraft(name = "   ").normalized().validate())
        assertEquals(emptySet(), ProfileDraft(name = "  Имя ").normalized().validate())
    }

    @Test
    fun theTagsCrowdedWithBlanksAndRepeatsAreCountedAfterNormalizing() {
        val crowded = List(ProfileLimits.MaxTags + 5) { " ML " }

        // 25 spellings of one tag are one tag: the limit applies to what is saved.
        assertEquals(emptySet(), valid.copy(interests = crowded).normalized().validate())
    }

    // ---- the forms that are opened on a record ----

    @Test
    fun theFormOfTheViewersProfileStartsWithWhatTheProfileHas() {
        val profile = myProfile(person = person(name = "Арджун", study = Study(level = "Магистратура", year = 2)))

        val draft = profile.toDraft()

        assertEquals("Арджун", draft.name)
        assertEquals("НИУ ВШЭ", draft.university)
        assertEquals("Магистратура", draft.studyLevel)
        assertEquals("2", draft.studyYear)
        assertEquals("Computer Science", draft.field)
        assertEquals("Люблю данные", draft.about)
        assertEquals(listOf("ML"), draft.interests)
        assertEquals(listOf("Kotlin"), draft.skills)
        assertEquals(emptySet(), draft.interestTypes)
    }

    @Test
    fun theFormOfAProfileWithoutAStudyHasBlankFields() {
        val draft = myProfile(person = person(study = Study())).toDraft()

        assertEquals("", draft.studyLevel)
        assertEquals("", draft.studyYear)
    }

    @Test
    fun aNewAccountsFormStartsEmptyAndNeedsAName() {
        val draft = myProfile(person = person(name = "", study = Study())).toDraft()

        assertEquals("", draft.name)
        assertTrue(ProfileDraftError.NameEmpty in draft.validate())
    }

    @Test
    fun theFormOfAWorkKeepsWhatTheServerHas() {
        val draft = work(link = null, hasEvidenceFile = true, type = ProjectType.Practical).toDraft()

        assertEquals(ProjectType.Practical, draft.type)
        assertEquals("Статья о графах", draft.title)
        assertEquals("", draft.link)
        assertTrue(draft.hasEvidenceFile)
        assertTrue(draft.sendsForVerification)
    }

    @Test
    fun theFormOfADocumentKeepsWhatTheServerHas() {
        val draft = document(kind = DocumentKind.Certificate, year = null, proof = null, hasEvidenceFile = true).toDraft()

        assertEquals(DocumentKind.Certificate, draft.kind)
        assertEquals("", draft.year)
        assertNull(draft.proof)
        assertTrue(draft.hasEvidenceFile)
        // A document with a proof file and no link is valid: the file is its proof.
        assertEquals(emptySet(), draft.validate())
        assertTrue(draft.sendsForVerification)
    }

    @Test
    fun aDocumentIsSentOnlyWithAProof() {
        val bare = DocumentDraft(title = "T", issuer = "I")

        assertFalse(bare.sendsForVerification)
        assertTrue(bare.copy(proof = ProofAttachment("https://x.org/a.pdf")).sendsForVerification)
        assertTrue(bare.copy(hasEvidenceFile = true).sendsForVerification)
    }

    @Test
    fun aFormOpenedOnARecordIsNotChangedUntilTheUserEditsIt() {
        val record = work(title = "Название", link = "https://doi.org/10.1/x")

        assertEquals(record.toDraft(), record.toDraft().normalized())
        assertEquals(document().toDraft(), document().toDraft().normalized())
    }
}
