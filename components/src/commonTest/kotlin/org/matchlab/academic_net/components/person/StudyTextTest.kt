package org.matchlab.academic_net.components.person

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The pure parts of the line "Бакалавриат, 3 курс" under a person's name. API-GAP G10: the level is a free text, shown as
 * it came; only the first letter is capital and a missing or blank part is left out.
 */
class StudyTextTest {
    // ---- the level ----

    @Test
    fun theLevelStartsWithACapitalAndKeepsTheRestAsItIs() {
        assertEquals("Бакалавриат", studyLevelText("бакалавриат"))
        assertEquals("Master", studyLevelText("master"))
        assertEquals("PhD student", studyLevelText("PhD student"))
        assertEquals("Ёлка", studyLevelText("ёлка"))
        assertEquals("Аспирантура (очная) МГУ", studyLevelText("аспирантура (очная) МГУ"))
    }

    @Test
    fun theLevelIsTrimmed() {
        assertEquals("Магистратура", studyLevelText("  магистратура \n"))
    }

    @Test
    fun aMissingOrBlankLevelIsNone() {
        assertNull(studyLevelText(null))
        assertNull(studyLevelText(""))
        assertNull(studyLevelText("   "))
        assertNull(studyLevelText("\t\n"))
    }

    @Test
    fun aLevelThatStartsWithADigitOrASignIsShownAsItIs() {
        assertEquals("1 курс магистратуры", studyLevelText("1 курс магистратуры"))
        assertEquals("-", studyLevelText("-"))
    }

    // ---- the year ----

    @Test
    fun aYearOfOneOrMoreIsAYear() {
        assertEquals(1, studyYearOrNull(1))
        assertEquals(3, studyYearOrNull(3))
        assertEquals(6, studyYearOrNull(6))
    }

    @Test
    fun zeroNegativeAndMissingYearsAreNone() {
        assertNull(studyYearOrNull(0))
        assertNull(studyYearOrNull(-1))
        assertNull(studyYearOrNull(Int.MIN_VALUE))
        assertNull(studyYearOrNull(null))
    }

    // ---- the line ----

    @Test
    fun theLineJoinsTheThreePartsWithADot() {
        assertEquals("Stanford · Бакалавриат, 3 курс · Computer Science", joinSubtitle("Stanford", "Бакалавриат, 3 курс", "Computer Science"))
    }

    @Test
    fun aMissingPartIsLeftOutWithoutADanglingSeparator() {
        assertEquals("Stanford · Computer Science", joinSubtitle("Stanford", null, "Computer Science"))
        assertEquals("Бакалавриат, 3 курс · Computer Science", joinSubtitle("", "Бакалавриат, 3 курс", "Computer Science"))
        assertEquals("Stanford · Бакалавриат, 3 курс", joinSubtitle("Stanford", "Бакалавриат, 3 курс", ""))
        assertEquals("Stanford", joinSubtitle("Stanford", null, ""))
        assertEquals("Computer Science", joinSubtitle("", null, "Computer Science"))
    }

    @Test
    fun aBlankPartIsLeftOutToo() {
        assertEquals("Stanford", joinSubtitle("Stanford", "   ", "\n"))
        assertEquals("Stanford · Computer Science", joinSubtitle("Stanford", " ", "Computer Science"))
    }

    @Test
    fun aPersonWithNoneOfTheThreeHasAnEmptyLine() {
        assertEquals("", joinSubtitle("", null, ""))
        assertEquals("", joinSubtitle("  ", "  ", "  "))
    }

    @Test
    fun theTextsAreNotTrimmedByTheJoinItself() {
        // The texts come trimmed from the data layer; the join only decides what is left out.
        assertEquals(" Stanford  · Computer Science", joinSubtitle(" Stanford ", null, "Computer Science"))
    }
}
