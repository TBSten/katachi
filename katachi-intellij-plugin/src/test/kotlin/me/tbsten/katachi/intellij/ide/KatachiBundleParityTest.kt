package me.tbsten.katachi.intellij.ide

import junit.framework.TestCase
import me.tbsten.katachi.intellij.ui.dialog.GenerateDialogStrings
import me.tbsten.katachi.intellij.ui.dialog.PropertiesGenerateDialogStrings
import java.io.File
import java.util.Properties

/**
 * The English bundle (`KatachiBundle.properties`) and the Japanese one (`_ja`, the source) must say the
 * same things: the same keys, and the same `{n}` arguments in each. A key only one of them has shows
 * an untranslated (or missing) text to half of the users.
 */
internal class KatachiBundleParityTest : TestCase() {
    private val dir = File(PropertiesGenerateDialogStrings.MESSAGES_DIR)
    private val english = PropertiesGenerateDialogStrings.load(File(dir, "KatachiBundle.properties"))
    private val japanese = PropertiesGenerateDialogStrings.load(File(dir, "KatachiBundle_ja.properties"))

    fun `test 日英の鍵の集合が一致する`() {
        val onlyEnglish = english.stringPropertyNames() - japanese.stringPropertyNames()
        val onlyJapanese = japanese.stringPropertyNames() - english.stringPropertyNames()
        assertTrue("only in KatachiBundle.properties: $onlyEnglish", onlyEnglish.isEmpty())
        assertTrue("only in KatachiBundle_ja.properties: $onlyJapanese", onlyJapanese.isEmpty())
    }

    fun `test 鍵ごとの引数 の番号の集合が日英で一致する`() {
        val mismatched = english.stringPropertyNames().filter { placeholdersOf(english, it) != placeholdersOf(japanese, it) }
            .map { "$it: en=${placeholdersOf(english, it)} ja=${placeholdersOf(japanese, it)}" }
        assertTrue("arguments differ: $mismatched", mismatched.isEmpty())
    }

    fun `test 同じ鍵を1つのファイルに2回書いていない`() {
        for (name in listOf("KatachiBundle.properties", "KatachiBundle_ja.properties")) {
            val keys = File(dir, name).readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.substringBefore('=') }
            assertEquals("$name has a duplicated key", keys.size, keys.toSet().size)
        }
    }

    fun `test 利用者向けの文言に 帯 を使わない`() {
        for (name in listOf("KatachiBundle.properties", "KatachiBundle_ja.properties")) {
            assertFalse("$name contains the banned word", File(dir, name).readText().contains("帯"))
        }
    }

    fun `test ダイアログの文言はどちらの言語でも空でなく日本語では日本語が出る`() {
        val en = PropertiesGenerateDialogStrings.english(dir)
        val ja = PropertiesGenerateDialogStrings.japanese(dir)
        val enTexts = dialogTextsOf(en)
        val jaTexts = dialogTextsOf(ja)
        assertTrue(enTexts.all { it.isNotBlank() && '{' !in it })
        assertTrue(jaTexts.all { it.isNotBlank() && '{' !in it })
        assertEquals("Template", en.templateLabel)
        assertEquals("テンプレート", ja.templateLabel)
        assertEquals("Enter an integer (1 to 5)", en.notAnInt(1, 5))
        assertEquals("整数で入力してください（1〜5）", ja.notAnInt(1, 5))
    }

    private fun dialogTextsOf(s: GenerateDialogStrings): List<String> = listOf(
        s.title, s.templateLabel, s.definitionLabel, s.targetPathLabel, s.generate, s.noTemplates, s.templateRemoved("X"),
        s.targetNew, s.targetEmpty, s.targetHasContent, s.chooseOne, s.requiredError, s.notAnInt(1, 2), s.notAccepted,
        s.captureSeparatorError, s.captureDotError, s.capturePathHint("a", "b"), s.captureModuleHint("a", "b"), s.generateRefused("r"),
    )

    private fun placeholdersOf(properties: Properties, key: String): Set<Int> =
        PLACEHOLDER.findAll(properties.getProperty(key).orEmpty()).map { it.groupValues[1].toInt() }.toSet()

    private companion object {
        val PLACEHOLDER = Regex("""\{(\d+)""")
    }
}
