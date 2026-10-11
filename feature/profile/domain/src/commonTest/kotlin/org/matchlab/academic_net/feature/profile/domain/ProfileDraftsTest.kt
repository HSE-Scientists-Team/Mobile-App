package org.matchlab.academic_net.feature.profile.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.matchlab.academic_net.common.model.ProjectType

private fun text(length: Int) = "a".repeat(length)

class AboutDraftTest {
    @Test
    fun emptyDraftIsValid() {
        assertEquals(emptySet(), AboutDraft().validate())
    }

    @Test
    fun aboutAtTheLimitIsValidAndOneMoreIsTooLong() {
        assertEquals(emptySet(), AboutDraft(about = text(ProfileLimits.MaxAboutLength)).validate())
        assertEquals(
            setOf(AboutDraftError.AboutTooLong),
            AboutDraft(about = text(ProfileLimits.MaxAboutLength + 1)).validate(),
        )
    }

    @Test
    fun tagsAtTheLimitAreValidAndOneMoreIsTooMany() {
        val atLimit = List(ProfileLimits.MaxTags) { "tag $it" }
        val over = atLimit + "one more"

        assertEquals(emptySet(), AboutDraft(interests = atLimit, skills = atLimit).validate())
        assertEquals(
            setOf(AboutDraftError.TooManyInterests, AboutDraftError.TooManySkills),
            AboutDraft(interests = over, skills = over).validate(),
        )
    }

    @Test
    fun normalizingTrimsTheTextAndCleansTheTags() {
        val normalized = AboutDraft(
            about = "  Люблю CV \n",
            interestTypes = setOf(ProjectType.Practical),
            interests = listOf(" Edge   AI ", "edge ai", ""),
            skills = listOf("Python", " python "),
        ).normalized()

        assertEquals("Люблю CV", normalized.about)
        assertEquals(setOf(ProjectType.Practical), normalized.interestTypes)
        assertEquals(listOf("Edge AI"), normalized.interests)
        assertEquals(listOf("Python"), normalized.skills)
    }

    @Test
    fun spacesOnlyDoNotCountForTheLimitAfterNormalizing() {
        val draft = AboutDraft(about = text(ProfileLimits.MaxAboutLength) + "   ")

        assertEquals(setOf(AboutDraftError.AboutTooLong), draft.validate())
        assertEquals(emptySet(), draft.normalized().validate())
    }

    @Test
    fun duplicatesDoNotCountForTheTagLimitAfterNormalizing() {
        val draft = AboutDraft(skills = List(ProfileLimits.MaxTags + 5) { "Python" })

        assertEquals(emptySet(), draft.normalized().validate())
    }
}

class WorkDraftTest {
    private val valid = WorkDraft(title = "Title", description = "Description")

    @Test
    fun scientificTypeIsTheDefault() {
        assertEquals(ProjectType.Scientific, WorkDraft().type)
    }

    @Test
    fun emptyDraftLacksTitleAndDescription() {
        assertEquals(
            setOf(WorkDraftError.TitleEmpty, WorkDraftError.DescriptionEmpty),
            WorkDraft().validate(),
        )
    }

    @Test
    fun draftWithoutLinkAndProofIsValid() {
        assertEquals(emptySet(), valid.validate())
    }

    @Test
    fun blankTitleAndDescriptionAreEmpty() {
        assertEquals(
            setOf(WorkDraftError.TitleEmpty, WorkDraftError.DescriptionEmpty),
            valid.copy(title = "   ", description = " \n ").validate(),
        )
    }

    @Test
    fun titleAtTheLimitIsValidAndOneMoreIsTooLong() {
        assertEquals(emptySet(), valid.copy(title = text(ProfileLimits.MaxTitleLength)).validate())
        assertEquals(
            setOf(WorkDraftError.TitleTooLong),
            valid.copy(title = text(ProfileLimits.MaxTitleLength + 1)).validate(),
        )
    }

    @Test
    fun descriptionAtTheLimitIsValidAndOneMoreIsTooLong() {
        assertEquals(emptySet(), valid.copy(description = text(ProfileLimits.MaxDescriptionLength)).validate())
        assertEquals(
            setOf(WorkDraftError.DescriptionTooLong),
            valid.copy(description = text(ProfileLimits.MaxDescriptionLength + 1)).validate(),
        )
    }

    @Test
    fun linkMustBeAWebLinkWhenGiven() {
        assertEquals(emptySet(), valid.copy(link = "https://doi.org/10.1234/cv").validate())
        assertEquals(emptySet(), valid.copy(link = "").validate())
        assertEquals(setOf(WorkDraftError.LinkInvalid), valid.copy(link = "doi.org/10.1234").validate())
        assertEquals(setOf(WorkDraftError.LinkInvalid), valid.copy(link = "ftp://example.com").validate())
    }

    @Test
    fun proofMustBeAWebLink() {
        assertEquals(emptySet(), valid.copy(proof = ProofAttachment("https://files.example.com/a.pdf")).validate())
        assertEquals(
            setOf(WorkDraftError.ProofInvalid),
            valid.copy(proof = ProofAttachment("not a link")).validate(),
        )
    }

    @Test
    fun draftWithNothingOrBlankLinkIsNotSentForVerification() {
        assertFalse(valid.sendsForVerification)
        assertFalse(valid.copy(link = "   ").sendsForVerification)
    }

    @Test
    fun linkOrProofSendsTheWorkForVerification() {
        assertTrue(valid.copy(link = "https://github.com/a/b").sendsForVerification)
        assertTrue(valid.copy(proof = ProofAttachment("https://files.example.com/a.pdf")).sendsForVerification)
    }

    @Test
    fun normalizingTrimsTheFields() {
        val normalized = WorkDraft(
            type = ProjectType.Practical,
            title = "  My   work ",
            description = "  Text \n",
            link = " https://github.com/a/b ",
            proof = ProofAttachment(" https://files.example.com/a.pdf "),
        ).normalized()

        assertEquals(ProjectType.Practical, normalized.type)
        assertEquals("My work", normalized.title)
        assertEquals("Text", normalized.description)
        assertEquals("https://github.com/a/b", normalized.link)
        assertEquals(ProofAttachment("https://files.example.com/a.pdf"), normalized.proof)
        assertEquals(emptySet(), normalized.validate())
    }

    @Test
    fun normalizingKeepsAMissingProofMissing() {
        assertNull(valid.normalized().proof)
    }
}

class DocumentDraftTest {
    private val valid = DocumentDraft(
        title = "Сертификат Deep Learning",
        issuer = "Coursera",
        year = "2025",
        proof = ProofAttachment("https://files.example.com/cert.pdf"),
    )

    @Test
    fun educationIsTheDefaultKind() {
        assertEquals(DocumentKind.Education, DocumentDraft().kind)
    }

    @Test
    fun filledDraftIsValid() {
        assertEquals(emptySet(), valid.validate())
    }

    @Test
    fun emptyDraftLacksTitleIssuerAndProof() {
        assertEquals(
            setOf(DocumentDraftError.TitleEmpty, DocumentDraftError.IssuerEmpty, DocumentDraftError.ProofMissing),
            DocumentDraft().validate(),
        )
    }

    @Test
    fun yearIsOptional() {
        assertEquals(emptySet(), valid.copy(year = "").validate())
        assertNull(valid.copy(year = "").yearNumber)
    }

    @Test
    fun yearBoundariesAreInclusive() {
        assertEquals(emptySet(), valid.copy(year = ProfileLimits.MinYear.toString()).validate())
        assertEquals(emptySet(), valid.copy(year = ProfileLimits.MaxYear.toString()).validate())
        assertEquals(
            setOf(DocumentDraftError.YearInvalid),
            valid.copy(year = (ProfileLimits.MinYear - 1).toString()).validate(),
        )
        assertEquals(
            setOf(DocumentDraftError.YearInvalid),
            valid.copy(year = (ProfileLimits.MaxYear + 1).toString()).validate(),
        )
    }

    @Test
    fun yearThatIsNotANumberIsInvalid() {
        for (year in listOf("abc", "20 25", "+2025", "-2025", "2025.5", "99999999999999999999")) {
            assertEquals(setOf(DocumentDraftError.YearInvalid), valid.copy(year = year).validate(), year)
        }
    }

    @Test
    fun yearNumberIsTheParsedYear() {
        assertEquals(2025, valid.yearNumber)
        assertEquals(2027, valid.copy(year = " 2027 ").yearNumber)
        assertNull(valid.copy(year = "1800").yearNumber)
    }

    @Test
    fun titleAndIssuerLimits() {
        assertEquals(
            emptySet(),
            valid.copy(
                title = text(ProfileLimits.MaxTitleLength),
                issuer = text(ProfileLimits.MaxIssuerLength),
            ).validate(),
        )
        assertEquals(
            setOf(DocumentDraftError.TitleTooLong, DocumentDraftError.IssuerTooLong),
            valid.copy(
                title = text(ProfileLimits.MaxTitleLength + 1),
                issuer = text(ProfileLimits.MaxIssuerLength + 1),
            ).validate(),
        )
    }

    @Test
    fun proofIsRequiredAndMustBeAWebLink() {
        assertEquals(setOf(DocumentDraftError.ProofMissing), valid.copy(proof = null).validate())
        assertEquals(
            setOf(DocumentDraftError.ProofInvalid),
            valid.copy(proof = ProofAttachment("cert.pdf")).validate(),
        )
    }

    @Test
    fun normalizingTrimsTheFields() {
        val normalized = valid.copy(
            title = "  Сертификат   Deep Learning ",
            issuer = " Coursera ",
            year = " 2025 ",
            proof = ProofAttachment(" https://files.example.com/cert.pdf "),
        ).normalized()

        assertEquals("Сертификат Deep Learning", normalized.title)
        assertEquals("Coursera", normalized.issuer)
        assertEquals("2025", normalized.year)
        assertEquals(ProofAttachment("https://files.example.com/cert.pdf"), normalized.proof)
        assertEquals(emptySet(), normalized.validate())
    }

    @Test
    fun normalizingKeepsAMissingProofMissing() {
        assertNull(valid.copy(proof = null).normalized().proof)
    }
}
