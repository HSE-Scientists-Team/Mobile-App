package org.matchlab.academic_net.components.person

import kotlin.test.Test
import kotlin.test.assertEquals

/** The initials of the avatar of a person with no picture: the first letters of the first and the last word of the name. */
class InitialsTest {
    @Test
    fun aNameAndASurnameGiveTheirFirstLetters() {
        assertEquals("АМ", initialsOf("Арджун Мехта"))
        assertEquals("EV", initialsOf("Elena Vasquez"))
    }

    @Test
    fun aNameOfOneWordGivesOneLetter() {
        assertEquals("А", initialsOf("Арджун"))
        assertEquals("S", initialsOf("Sam"))
    }

    @Test
    fun theLettersAreCapitals() {
        assertEquals("ИП", initialsOf("иван петров"))
        assertEquals("SS", initialsOf("sam smith"))
        assertEquals("Ё", initialsOf("ёжик"))
    }

    @Test
    fun aBlankNameGivesNothing() {
        assertEquals("", initialsOf(""))
        assertEquals("", initialsOf("   "))
        assertEquals("", initialsOf("\t\n"))
    }

    @Test
    fun morePartsGiveTheFirstAndTheLast() {
        assertEquals("МП", initialsOf("Мария Ивановна Петрова"))
        assertEquals("AS", initialsOf("Anna Maria Smith"))
    }

    @Test
    fun spacesAroundAndBetweenTheWordsDoNotMatter() {
        assertEquals("АМ", initialsOf("  Арджун    Мехта  "))
        assertEquals("А", initialsOf("  Арджун "))
        // A non-breaking space is a space too.
        assertEquals("АМ", initialsOf("Арджун\u00A0Мехта"))
        assertEquals("АМ", initialsOf("Арджун\tМехта"))
    }

    @Test
    fun aHyphenatedNameIsOneWord() {
        assertEquals("AS", initialsOf("Anna-Maria Smith"))
        assertEquals("К", initialsOf("Корсаков-Римский"))
    }

    @Test
    fun aSignInFrontOfAWordIsSkippedToItsFirstLetter() {
        assertEquals("ИП", initialsOf("(Иван) «Петров»"))
        assertEquals("S", initialsOf("@sam"))
    }

    @Test
    fun aWordWithNoLetterIsNotAWordOfTheName() {
        assertEquals("ИП", initialsOf("Иван — Петров"))
        assertEquals("И", initialsOf("Иван &"))
        assertEquals("", initialsOf("— &"))
    }

    @Test
    fun anEmojiIsNotAnInitial() {
        assertEquals("S", initialsOf("😀 Sam"))
        assertEquals("S", initialsOf("Sam 😀"))
        assertEquals("", initialsOf("😀"))
    }

    @Test
    fun aDigitCanBeTheInitialOfAWordWithNoLetters() {
        assertEquals("7", initialsOf("7"))
        assertEquals("A7", initialsOf("Agent 7"))
    }
}
