package org.matchlab.academic_net.feature.profile.ui

import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.feature.profile.domain.Review
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What a review lets the viewer do with its author (API-GAP G55): the server may leave the id of the author out, and a
 * person with no id has no page to open and no chat to start, so the screens show the name as a plain row.
 */
class ReviewAuthorTest {
    private fun reviewBy(authorId: String, isByViewer: Boolean = false): Review =
        review().copy(author = UserSummary(UserId(authorId), "Елена"), isByViewer = isByViewer)

    @Test
    fun anAuthorWithAnIdCanBeOpenedAndMessaged() {
        val review = reviewBy("author-1")

        assertTrue(review.canOpenAuthor)
        assertTrue(review.canMessageAuthor)
    }

    @Test
    fun anAuthorWithoutAnIdCanNeitherBeOpenedNorMessaged() {
        val review = reviewBy("")

        assertFalse(review.canOpenAuthor, "the route profile/ matches nothing and would crash the navigation")
        assertFalse(review.canMessageAuthor)
    }

    @Test
    fun anIdOfWhitespaceOnlyIsAnIdThatIsNotThere() {
        // A space, a tab, a line break, several of them, and a no-break space: all of it is blank.
        for (blank in listOf(" ", "   ", "\t", "\n", " \t\n ", " ")) {
            val review = reviewBy(blank)

            assertFalse(review.canOpenAuthor, "id ${blank.toCodes()} is blank")
            assertFalse(review.canMessageAuthor, "id ${blank.toCodes()} is blank")
        }
    }

    @Test
    fun anIdWithWhitespaceAroundItIsStillAnId() {
        // Only an id that is blank as a whole counts as missing; the id is never trimmed or changed here.
        val review = reviewBy(" author-1 ")

        assertTrue(review.canOpenAuthor)
        assertTrue(review.canMessageAuthor)
    }

    @Test
    fun theViewersOwnReviewCanBeOpenedButHasNobodyToMessage() {
        val review = reviewBy("viewer", isByViewer = true)

        assertTrue(review.canOpenAuthor, "the id is there: the author row still leads to the page of the viewer")
        assertFalse(review.canMessageAuthor, "a review the viewer wrote has nobody to message")
    }

    @Test
    fun theViewersOwnReviewWithoutAnAuthorIdCanDoNeither() {
        val review = reviewBy("", isByViewer = true)

        assertFalse(review.canOpenAuthor)
        assertFalse(review.canMessageAuthor)
    }

    @Test
    fun theViewerFlagDoesNotChangeWhetherAnAuthorWithAnIdCanBeOpened() {
        assertTrue(reviewBy("a", isByViewer = false).canOpenAuthor)
        assertTrue(reviewBy("a", isByViewer = true).canOpenAuthor)
    }

    private fun String.toCodes(): String = map { it.code }.toString()
}
