package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.ui.dialog.GenerateDialogStrings
import me.tbsten.katachi.intellij.ui.dialog.PropertiesGenerateDialogStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Method

/**
 * The tool window's two languages: every text of [KatachiStrings] is written in both, and the texts it
 * shares with the generate dialog say the same as `KatachiBundle` in each language.
 */
class KatachiStringsTest {
    private val japaneseChars = Regex("[\\u3040-\\u30ff\\u4e00-\\u9fff\\uff01-\\uff5e]")

    @Test
    fun `言語の名前が ja なら日本語でそれ以外は英語の文言を選ぶ`() {
        assertSame(JapaneseKatachiStrings, KatachiStrings.of("ja"))
        assertSame(EnglishKatachiStrings, KatachiStrings.of("en"))
        assertSame(EnglishKatachiStrings, KatachiStrings.of(""))
    }

    @Test
    fun `英語の文言はどれも空でなく日本語の文字を含まない`() {
        val texts = textsOf(EnglishKatachiStrings)
        val bad = texts.filter { (_, text) -> text.isBlank() || japaneseChars.containsMatchIn(text) }
        assertTrue("blank or Japanese: $bad", bad.isEmpty())
    }

    @Test
    fun `日本語の文言はどれも空でなく英語と同じ数だけあり大半が英語と違う`() {
        val english = textsOf(EnglishKatachiStrings).toMap()
        val japanese = textsOf(JapaneseKatachiStrings).toMap()
        assertEquals(english.keys, japanese.keys)
        assertTrue(japanese.values.none { it.isBlank() })
        // A few are the same on purpose (the file count "1 file", the marker "← Opened", "› :task").
        val same = english.filter { (name, text) -> japanese[name] == text }.keys
        assertTrue("the same in both languages: $same", same.size <= 5)
    }

    @Test
    fun `ダイアログと共通の文言はそれぞれの言語で KatachiBundle と同じになる`() {
        assertSharedTexts(EnglishKatachiStrings, PropertiesGenerateDialogStrings.english())
        assertSharedTexts(JapaneseKatachiStrings, PropertiesGenerateDialogStrings.japanese())
    }

    @Test
    fun `英語では数に合わせて単数と複数を書き分ける`() {
        assertEquals("1 minute ago", EnglishKatachiStrings.minutesAgo(1))
        assertEquals("3 minutes ago", EnglishKatachiStrings.minutesAgo(3))
        assertEquals("Generated 1 template", EnglishKatachiStrings.resultAll(1))
        assertEquals("Generated 2 templates", EnglishKatachiStrings.resultAll(2))
        assertEquals("A is gone", EnglishKatachiStrings.removedTemplates(listOf("A")))
        assertEquals("A, B are gone", EnglishKatachiStrings.removedTemplates(listOf("A", "B")))
        assertEquals("withTest (when useCase is on)", EnglishKatachiStrings.collapsedField("withTest", "useCase", "true"))
    }

    private fun assertSharedTexts(tool: KatachiStrings, dialog: GenerateDialogStrings) {
        assertEquals(dialog.chooseOne, tool.chooseOne)
        assertEquals(dialog.requiredError, tool.requiredError)
        assertEquals(dialog.notAnInt(1, 5), tool.notAnInt(1, 5))
        assertEquals(dialog.notAccepted, tool.notAccepted)
        assertEquals(dialog.captureSeparatorError, tool.captureSeparatorError)
        assertEquals(dialog.captureDotError, tool.captureDotError)
        assertEquals(dialog.notAnExistingModule(emptyList()), tool.notAnExistingModule(emptyList()))
        assertEquals(dialog.notAnExistingModule(listOf("a", "b")), tool.notAnExistingModule(listOf("a", "b")))
        assertEquals(dialog.capturePathHint("x", "p/<x>"), tool.capturePathHint("x", "p/<x>"))
        assertEquals(dialog.captureModuleHint("x", ":<x>"), tool.captureModuleHint("x", ":<x>"))
    }

    /** Every text of [strings], by member name: the properties, and the functions with sample arguments. */
    private fun textsOf(strings: KatachiStrings): List<Pair<String, String>> =
        KatachiStrings::class.java.declaredMethods
            .filter { it.returnType == String::class.java }
            .sortedBy { it.name }
            .flatMap { method -> samplesOf(method).map { args -> "${method.name}(${args.joinToString()})" to method.invoke(strings, *args) as String } }

    /** Argument lists for [method]: a nullable String gets both `null` and a value, a count both 1 and 3. */
    private fun samplesOf(method: Method): List<Array<Any?>> = method.parameterTypes.fold(listOf(arrayOf())) { acc, type ->
        val values: List<Any?> = when (type) {
            String::class.java -> listOf("x", null).takeIf { method.name == "outdatedTitle" } ?: listOf("x")
            Int::class.javaPrimitiveType -> listOf(1, 3)
            Long::class.javaPrimitiveType -> listOf(1L, 3L)
            List::class.java -> listOf(emptyList<String>(), listOf("a"), listOf("a", "b"))
            else -> error("no sample for ${type.name} of ${method.name}")
        }
        acc.flatMap { args -> values.map { arrayOf(*args, it) } }
    }
}
