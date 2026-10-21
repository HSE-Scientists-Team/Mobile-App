package org.matchlab.academic_net.feature.reviews.domain

import org.matchlab.academic_net.common.model.ProjectId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The review form (16): what is trimmed, what is counted, and which fields are named when the draft is wrong. */
class ReviewDraftTest {
    private val project = ProjectId("p-1")

    private fun valid(
        projectId: ProjectId? = project,
        rating: Int = 5,
        title: String = "Отлично",
        text: String = "Довёл до демо",
    ) = ReviewDraft(projectId, rating, title, text)

    private fun errorsOf(draft: ReviewDraft) = draft.validate()

    /** [count] emoji outside the Basic Multilingual Plane: [count] code points, twice as many UTF-16 units. */
    private fun emoji(count: Int) = "😀".repeat(count)

    // ---- the draft as a whole ----

    @Test
    fun anEmptyDraftNamesEveryField() {
        assertEquals(
            setOf(ReviewDraftError.ProjectMissing, ReviewDraftError.RatingMissing, ReviewDraftError.TitleBlank, ReviewDraftError.TextBlank),
            ReviewDraft().validate(),
        )
    }

    @Test
    fun aFilledDraftHasNoErrors() {
        assertEquals(emptySet(), valid().validate())
    }

    @Test
    fun theLimitsAreTheBackendsOnes() {
        assertEquals(255, ReviewLimits.MaxTitle)
        assertEquals(10_000, ReviewLimits.MaxText)
        assertEquals(1, ReviewLimits.MinRating)
        assertEquals(5, ReviewLimits.MaxRating)
    }

    @Test
    fun severalMistakesAreNamedAtOnce() {
        val draft = valid(projectId = null, rating = 6, title = "t".repeat(256), text = "x".repeat(10_001))

        assertEquals(
            setOf(ReviewDraftError.ProjectMissing, ReviewDraftError.RatingMissing, ReviewDraftError.TitleTooLong, ReviewDraftError.TextTooLong),
            errorsOf(draft),
        )
    }

    @Test
    fun aBlankTitleAndATooLongTextAreNamedTogether() {
        assertEquals(
            setOf(ReviewDraftError.TitleBlank, ReviewDraftError.TextTooLong),
            errorsOf(valid(title = "   ", text = "x".repeat(10_001))),
        )
    }

    // ---- the project ----

    @Test
    fun noProjectIsTheOnlyMistakeWhenTheRestIsRight() {
        assertEquals(setOf(ReviewDraftError.ProjectMissing), errorsOf(valid(projectId = null)))
    }

    @Test
    fun aProjectWithAnyIdPassesBecauseTheServerKnowsTheIds() {
        assertEquals(emptySet(), errorsOf(valid(projectId = ProjectId("x"))))
    }

    // ---- the rating ----

    @Test
    fun ratingsOneToFiveAreAccepted() {
        for (rating in 1..5) assertEquals(emptySet(), errorsOf(valid(rating = rating)), "rating $rating")
    }

    @Test
    fun zeroMeansNotGivenYet() {
        assertEquals(setOf(ReviewDraftError.RatingMissing), errorsOf(valid(rating = 0)))
    }

    @Test
    fun sixAndNegativeRatingsAreRefusedAsWell() {
        for (rating in listOf(6, 7, 100, -1, Int.MIN_VALUE, Int.MAX_VALUE)) {
            assertEquals(setOf(ReviewDraftError.RatingMissing), errorsOf(valid(rating = rating)), "rating $rating")
        }
    }

    // ---- the title ----

    @Test
    fun anEmptyTitleIsBlank() {
        assertEquals(setOf(ReviewDraftError.TitleBlank), errorsOf(valid(title = "")))
    }

    @Test
    fun aTitleOfOnlySpacesTabsAndNewlinesIsBlank() {
        for (title in listOf(" ", "   ", "\t", "\n", " \t\r\n ", " ", " ")) {
            assertEquals(setOf(ReviewDraftError.TitleBlank), errorsOf(valid(title = title)), "title ${title.map { it.code }}")
        }
    }

    @Test
    fun aTitleOfOneCharacterIsEnough() {
        assertEquals(emptySet(), errorsOf(valid(title = "a")))
    }

    @Test
    fun aTitleOf255CharactersPasses() {
        assertEquals(emptySet(), errorsOf(valid(title = "a".repeat(255))))
    }

    @Test
    fun aTitleOf256CharactersIsTooLong() {
        assertEquals(setOf(ReviewDraftError.TitleTooLong), errorsOf(valid(title = "a".repeat(256))))
    }

    @Test
    fun aTitleIsNeverBlankAndTooLongAtOnce() {
        val errors = errorsOf(valid(title = "a".repeat(1000)))

        assertTrue(ReviewDraftError.TitleBlank !in errors)
        assertTrue(ReviewDraftError.TitleTooLong in errors)
    }

    @Test
    fun theSpacesAroundATitleAreNotCounted() {
        assertEquals(emptySet(), errorsOf(valid(title = "  " + "a".repeat(255) + "\n\t ")))
        assertEquals(setOf(ReviewDraftError.TitleTooLong), errorsOf(valid(title = "  " + "a".repeat(256) + "  ")))
    }

    @Test
    fun theSpacesInsideATitleAreCounted() {
        assertEquals(emptySet(), errorsOf(valid(title = "a" + " ".repeat(253) + "b")))
        assertEquals(setOf(ReviewDraftError.TitleTooLong), errorsOf(valid(title = "a" + " ".repeat(254) + "b")))
    }

    @Test
    fun anEmojiTitleIsCountedInCodePointsNotInUtf16Units() {
        // 255 emoji are 510 units: counting units would call it too long, the server accepts it.
        assertEquals(emptySet(), errorsOf(valid(title = emoji(255))))
        assertEquals(setOf(ReviewDraftError.TitleTooLong), errorsOf(valid(title = emoji(256))))
    }

    @Test
    fun anEmojiAloneIsATitle() {
        assertEquals(emptySet(), errorsOf(valid(title = emoji(1))))
    }

    // ---- the text ----

    @Test
    fun anEmptyTextIsBlank() {
        assertEquals(setOf(ReviewDraftError.TextBlank), errorsOf(valid(text = "")))
    }

    @Test
    fun aTextOfOnlyWhitespaceIsBlank() {
        for (text in listOf(" ", "\n\n", "\t \r\n", "  ")) {
            assertEquals(setOf(ReviewDraftError.TextBlank), errorsOf(valid(text = text)), "text ${text.map { it.code }}")
        }
    }

    @Test
    fun aTextOfOneCharacterIsEnough() {
        assertEquals(emptySet(), errorsOf(valid(text = "я")))
    }

    @Test
    fun aTextOf10000CharactersPasses() {
        assertEquals(emptySet(), errorsOf(valid(text = "x".repeat(10_000))))
    }

    @Test
    fun aTextOf10001CharactersIsTooLong() {
        assertEquals(setOf(ReviewDraftError.TextTooLong), errorsOf(valid(text = "x".repeat(10_001))))
    }

    @Test
    fun theSpacesAroundATextAreNotCounted() {
        assertEquals(emptySet(), errorsOf(valid(text = "\n\n" + "x".repeat(10_000) + "   ")))
        assertEquals(setOf(ReviewDraftError.TextTooLong), errorsOf(valid(text = "\n" + "x".repeat(10_001) + " ")))
    }

    @Test
    fun newlinesInsideATextAreCharacters() {
        assertEquals(emptySet(), errorsOf(valid(text = "x" + "\n".repeat(9_998) + "y")))
        assertEquals(setOf(ReviewDraftError.TextTooLong), errorsOf(valid(text = "x" + "\n".repeat(9_999) + "y")))
    }

    @Test
    fun anEmojiTextIsCountedInCodePointsNotInUtf16Units() {
        assertEquals(emptySet(), errorsOf(valid(text = emoji(10_000))))
        assertEquals(setOf(ReviewDraftError.TextTooLong), errorsOf(valid(text = emoji(10_001))))
    }

    @Test
    fun aLoneSurrogateIsOneCharacterAndNotHalfAPair() {
        val lonelyHigh = "\uD83D"
        val lonelyLow = "\uDE00"
        val reversedPair = lonelyLow + lonelyHigh // two lone halves, not a pair

        assertEquals(emptySet(), errorsOf(valid(title = "a".repeat(254) + lonelyHigh)))
        assertEquals(setOf(ReviewDraftError.TitleTooLong), errorsOf(valid(title = "a".repeat(254) + reversedPair)))
        assertEquals(emptySet(), errorsOf(valid(title = "a".repeat(253) + reversedPair)))
        assertEquals(emptySet(), errorsOf(valid(title = "a".repeat(255 - 1) + lonelyLow)))
    }

    @Test
    fun aHighSurrogateBeforeALetterIsNotAPair() {
        // "\uD83D" + "a" is two characters; counted as a pair it would be one and 255 of them would pass.
        val pairsOfNonPairs = "\uD83Da".repeat(128)

        assertEquals(setOf(ReviewDraftError.TitleTooLong), errorsOf(valid(title = pairsOfNonPairs)))
    }

    @Test
    fun mixedEmojiAndLettersAreCountedOneByOne() {
        val title = "a😀b".repeat(85) // 255 code points, 340 units

        assertEquals(emptySet(), errorsOf(valid(title = title)))
        assertEquals(setOf(ReviewDraftError.TitleTooLong), errorsOf(valid(title = title + "c")))
    }

    // ---- titleLength and textLength (public: the form shows the counter) ----

    @Test
    fun theLengthsOfAnEmptyDraftAreZero() {
        assertEquals(0, ReviewDraft().titleLength)
        assertEquals(0, ReviewDraft().textLength)
    }

    @Test
    fun theLengthsAreCountedInCodePointsOfTheTrimmedFields() {
        val draft = valid(title = "  a" + emoji(2) + "b \n", text = "\t" + emoji(3) + "я ")

        assertEquals(4, draft.titleLength)
        assertEquals(4, draft.textLength)
        assertEquals(draft.normalized().titleLength, draft.titleLength)
        assertEquals(draft.normalized().textLength, draft.textLength)
    }

    @Test
    fun anEmojiIsOneCodePointAlthoughTwoUtf16Units() {
        assertEquals(2, "😀".length)
        assertEquals(1, valid(title = emoji(1), text = emoji(1)).titleLength)
        assertEquals(1, valid(title = emoji(1), text = emoji(1)).textLength)
        assertEquals(255, valid(title = emoji(255)).titleLength)
        assertEquals(10_000, valid(text = emoji(10_000)).textLength)
    }

    @Test
    fun theLengthsOfABlankFieldAreZero() {
        assertEquals(0, valid(title = " \t\n ", text = "   ").titleLength)
        assertEquals(0, valid(title = " \t\n ", text = "   ").textLength)
    }

    @Test
    fun theLengthsAreTheOnesTheLimitsAreComparedWith() {
        val atTheLimit = valid(title = emoji(ReviewLimits.MaxTitle), text = emoji(ReviewLimits.MaxText))
        val overTheLimit = valid(title = emoji(ReviewLimits.MaxTitle + 1), text = emoji(ReviewLimits.MaxText + 1))

        assertEquals(ReviewLimits.MaxTitle, atTheLimit.titleLength)
        assertEquals(ReviewLimits.MaxText, atTheLimit.textLength)
        assertEquals(emptySet(), atTheLimit.validate())
        assertEquals(ReviewLimits.MaxTitle + 1, overTheLimit.titleLength)
        assertEquals(ReviewLimits.MaxText + 1, overTheLimit.textLength)
        assertEquals(setOf(ReviewDraftError.TitleTooLong, ReviewDraftError.TextTooLong), overTheLimit.validate())
    }

    @Test
    fun theLengthsIgnoreTheSpacesAroundButCountTheOnesInside() {
        val draft = valid(title = "   a  b   ", text = "\n\nx\ny\n\n")

        assertEquals(4, draft.titleLength)
        assertEquals(3, draft.textLength)
    }

    @Test
    fun aLoneSurrogateCountsAsOneInTheLengths() {
        assertEquals(1, valid(title = "\uD83D").titleLength)
        assertEquals(2, valid(text = "\uDE00\uD83D").textLength)
        assertEquals(2, valid(text = "\uD83Da").textLength)
        assertEquals(1, valid(text = "\uD83D\uDE00").textLength)
    }

    // ---- normalized() ----

    @Test
    fun normalizingTrimsTheTitleAndTheTextOnly() {
        val draft = valid(rating = 4, title = "  Заголовок \n", text = "\t Текст  ")

        val normalized = draft.normalized()

        assertEquals(ReviewDraft(project, 4, "Заголовок", "Текст"), normalized)
    }

    @Test
    fun normalizingKeepsTheSpacesInsideAndTheLineBreaks() {
        val normalized = valid(title = " a  b ", text = " первая\n\nвторая  строка ").normalized()

        assertEquals("a  b", normalized.title)
        assertEquals("первая\n\nвторая  строка", normalized.text)
    }

    @Test
    fun normalizingIsIdempotent() {
        val once = valid(title = "  x ", text = " y\n").normalized()

        assertEquals(once, once.normalized())
    }

    @Test
    fun normalizingADraftWithoutAProjectKeepsItWithoutOne() {
        val normalized = valid(projectId = null, rating = 0).normalized()

        assertEquals(null, normalized.projectId)
        assertEquals(0, normalized.rating)
    }

    @Test
    fun validatingDoesNotChangeTheDraft() {
        val draft = valid(title = "  x ")

        draft.validate()

        assertEquals("  x ", draft.title)
    }
}
