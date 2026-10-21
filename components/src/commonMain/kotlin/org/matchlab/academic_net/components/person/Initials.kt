package org.matchlab.academic_net.components.person

/**
 * The initials an avatar without a picture shows: the first letters of the first and of the last word of [name], in upper
 * case, with no space between them ("Арджун Мехта" is "АМ", "Anna Maria Smith" is "AS"). A name of one word gives one
 * letter ("Арджун" is "А"), and a blank name gives an empty string.
 *
 * Words are told by white space. A word that has no letter or digit ("—", "&", an emoji) is not a word of the name, and a
 * word that begins with a sign gives its first letter or digit ("(Иван)" gives "И").
 */
fun initialsOf(name: String): String {
    val words = name.words().mapNotNull { it.firstLetterOrDigit() }
    return when (words.size) {
        0 -> ""
        1 -> words.single().toString()
        else -> "${words.first()}${words.last()}"
    }
}

/** The pieces of this string between white space. */
private fun String.words(): List<String> {
    val words = mutableListOf<String>()
    val word = StringBuilder()
    for (char in this) {
        if (char.isWhitespace()) {
            if (word.isNotEmpty()) words += word.toString()
            word.clear()
        } else {
            word.append(char)
        }
    }
    if (word.isNotEmpty()) words += word.toString()
    return words
}

/** The first letter or digit of this word in upper case, `null` if it has none (a surrogate pair, e.g. an emoji, is not one). */
private fun String.firstLetterOrDigit(): Char? = firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()
