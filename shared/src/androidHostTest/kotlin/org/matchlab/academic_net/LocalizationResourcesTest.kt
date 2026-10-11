package org.matchlab.academic_net

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the string resources of every module: `values` is English (the default), `values-ru` is
 * Russian, and a key missing from `values` crashes the app on every non-Russian locale.
 */
class LocalizationResourcesTest {
    private val resourceDirs: List<File> = findResourceDirs(File("..").canonicalFile)

    @Test
    fun modulesWithStringsHaveBothLocales() {
        assertTrue(resourceDirs.size >= 7, "expected the resource directories of all UI modules, got $resourceDirs")
        resourceDirs.forEach { dir ->
            assertTrue(File(dir, "values/strings.xml").exists(), "values/strings.xml is missing in $dir")
            assertTrue(File(dir, "values-ru/strings.xml").exists(), "values-ru/strings.xml is missing in $dir")
        }
    }

    @Test
    fun keysMatchBetweenLocales() {
        forEachModule { dir, en, ru ->
            assertEquals(en.strings.keys, ru.strings.keys, "string keys differ in $dir")
            assertEquals(en.plurals.keys, ru.plurals.keys, "plurals keys differ in $dir")
            assertEquals(en.arrays.keys, ru.arrays.keys, "string-array keys differ in $dir")
        }
    }

    @Test
    fun placeholdersMatchBetweenLocales() {
        forEachModule { dir, en, ru ->
            en.strings.forEach { (key, text) ->
                assertEquals(placeholders(text), placeholders(ru.strings.getValue(key)), "placeholders of $key differ in $dir")
            }
            en.plurals.forEach { (key, items) ->
                val english = items.values.map(::placeholders).toSet()
                val russian = ru.plurals.getValue(key).values.map(::placeholders).toSet()
                assertEquals(english, russian, "placeholders of plurals $key differ in $dir")
            }
        }
    }

    @Test
    fun pluralsHaveTheCategoriesOfTheirLanguage() {
        forEachModule { dir, en, ru ->
            en.plurals.forEach { (key, items) ->
                assertEquals(setOf("one", "other"), items.keys, "English plurals $key in $dir")
            }
            ru.plurals.forEach { (key, items) ->
                assertEquals(setOf("one", "few", "many", "other"), items.keys, "Russian plurals $key in $dir")
            }
        }
    }

    @Test
    fun monthArraysHaveTwelveNamesInBothLocales() {
        val components = resourceDirs.single { it.path.contains("components") }
        listOf("values", "values-ru").forEach { locale ->
            val resources = parse(File(components, "$locale/strings.xml"))
            listOf("date_months_short", "date_months_short_day").forEach { name ->
                assertEquals(12, resources.arrays.getValue(name).size, "$name in $locale")
            }
        }
    }

    @Test
    fun russianMayDiffersBetweenTheMonthAndTheDay() {
        // "май 2026" next to a year, but "9 мая" next to a day number: the only month where the forms differ.
        val ru = parse(File(resourceDirs.single { it.path.contains("components") }, "values-ru/strings.xml"))
        assertEquals("май", ru.arrays.getValue("date_months_short")[4])
        assertEquals("мая", ru.arrays.getValue("date_months_short_day")[4])
        assertEquals("дек", ru.arrays.getValue("date_months_short")[11])
        assertEquals("дек", ru.arrays.getValue("date_months_short_day")[11])
    }

    @Test
    fun noEscapedPercentSigns() {
        // CLAUDE.md: a literal `%` in Compose resource strings is written as `%`, never `%%`.
        resourceDirs.forEach { dir ->
            listOf("values", "values-ru").forEach { locale ->
                assertTrue(!File(dir, "$locale/strings.xml").readText().contains("%%"), "%% in $dir/$locale")
            }
        }
    }

    private fun forEachModule(check: (File, Resources, Resources) -> Unit) {
        resourceDirs.forEach { dir ->
            check(dir, parse(File(dir, "values/strings.xml")), parse(File(dir, "values-ru/strings.xml")))
        }
    }

    private class Resources(
        val strings: Map<String, String>,
        val plurals: Map<String, Map<String, String>>,
        val arrays: Map<String, List<String>>,
    )

    private fun parse(file: File): Resources {
        val text = file.readText()
        val flags = setOf(RegexOption.DOT_MATCHES_ALL)
        val strings = Regex("""<string\s+name="([^"]+)"[^>]*>(.*?)</string>""", flags)
            .findAll(text).associate { it.groupValues[1] to it.groupValues[2] }
        val plurals = Regex("""<plurals\s+name="([^"]+)"[^>]*>(.*?)</plurals>""", flags).findAll(text).associate { block ->
            block.groupValues[1] to Regex("""<item\s+quantity="([^"]+)"[^>]*>(.*?)</item>""", flags)
                .findAll(block.groupValues[2]).associate { it.groupValues[1] to it.groupValues[2] }
        }
        val arrays = Regex("""<string-array\s+name="([^"]+)"[^>]*>(.*?)</string-array>""", flags).findAll(text).associate { block ->
            block.groupValues[1] to Regex("""<item[^>]*>(.*?)</item>""", flags)
                .findAll(block.groupValues[2]).map { it.groupValues[1] }.toList()
        }
        return Resources(strings, plurals, arrays)
    }

    private fun placeholders(text: String): List<String> =
        Regex("""%\d+\$[sd]""").findAll(text).map { it.value }.sorted().toList()

    private fun findResourceDirs(root: File): List<File> =
        root.walkTopDown()
            .onEnter { it.name != "build" && it.name != ".git" && it.name != ".gradle" && it.name != "iosApp" }
            .filter { it.isDirectory && it.name == "composeResources" && File(it, "values").isDirectory }
            .filter { File(it, "values/strings.xml").exists() || File(it, "values-ru/strings.xml").exists() }
            .toList()
}
