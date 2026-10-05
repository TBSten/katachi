package me.tbsten.katachi.intellij.ide

import junit.framework.TestCase
import me.tbsten.katachi.intellij.ui.dialog.PropertiesGenerateDialogStrings
import java.io.File

/**
 * Whether the keys the code and plugin.xml ask the bundle for are in both languages, and the other way round for the
 * no-role notification's texts. [KatachiBundleParityTest] only compares the two files, so a key that is missing from
 * both (a typo in a literal) passes it and shows the key itself on screen.
 */
internal class KatachiBundleKeysTest : TestCase() {
    private val dir = File(PropertiesGenerateDialogStrings.MESSAGES_DIR)
    private val english = PropertiesGenerateDialogStrings.load(File(dir, "KatachiBundle.properties"))
    private val japanese = PropertiesGenerateDialogStrings.load(File(dir, "KatachiBundle_ja.properties"))
    private val sources = listOf(File("src/main/kotlin"), File("src/shared/kotlin"))
    private val pluginXml = File("src/main/resources/META-INF/plugin.xml")

    private val prefixes = english.stringPropertyNames().map { it.substringBefore('.') }.toSet()
    private val literal = Regex("\"((?:${prefixes.joinToString("|")})(?:\\.[A-Za-z0-9]+)+)\"")

    private fun keysInCode(): Map<String, Set<String>> = sources.flatMap { it.walkTopDown().toList() }.filter { it.extension == "kt" }
        .associate { it.path to literal.findAll(it.readText()).map { match -> match.groupValues[1] }.toSet() }
        .filterValues { it.isNotEmpty() }

    fun `test コードが参照する鍵が日英の両方にある`() {
        assertTrue("the scan finds the keys in the code", keysInCode().values.flatten().toSet().size > MIN_KEYS_IN_CODE)
        val missing = keysInCode().flatMap { (file, keys) -> keys.filter { !english.containsKey(it) || !japanese.containsKey(it) }.map { "$file: $it" } }
        assertTrue("keys the code asks for but a bundle lacks: $missing", missing.isEmpty())
    }

    fun `test plugin xml のアクションとグループの文字が日英の両方にある`() {
        val ids = Regex("""<(action|group) id="([^"]+)"""").findAll(pluginXml.readText()).map { it.groupValues[1] to it.groupValues[2] }.toList()
        assertTrue("plugin.xml has actions", ids.isNotEmpty())
        val missing = ids.map { (kind, id) -> "$kind.$id.text" }.filter { !english.containsKey(it) || !japanese.containsKey(it) }
        assertTrue("an action or group without its text: $missing", missing.isEmpty())
    }

    fun `test 当たる Role が無いファイルの通知の鍵はどれもコードか plugin xml から使われている`() {
        val used = keysInCode().values.flatten().toSet() + Regex("""<action id="([^"]+)"""").findAll(pluginXml.readText()).flatMap {
            listOf("action.${it.groupValues[1]}.text", "action.${it.groupValues[1]}.description")
        }
        val unused = rulesKeys().filter { it !in used }
        assertTrue("keys nothing asks for: $unused", unused.isEmpty())
    }

    fun `test 当たる Role が無いファイルの通知の文言は英語側に日本語が無く日本語側に日本語がある`() {
        val keys = rulesKeys()
        assertTrue("the notification's keys are there", keys.size >= RULES_KEYS)
        val japaneseLetters = Regex("[\\u3040-\\u30ff\\u4e00-\\u9fff]")
        assertEquals("japanese in the English bundle", emptyList<String>(), keys.filter { japaneseLetters.containsMatchIn(english.getProperty(it)) })
        // The row "{0} → {1}" is arguments and an arrow in both languages.
        val untranslated = keys.filter { !japaneseLetters.containsMatchIn(japanese.getProperty(it)) }
        assertEquals("no japanese in the Japanese bundle", listOf("popup.rules.nearby.row"), untranslated)
    }

    fun `test 当たる Role が無いファイルの通知の文言は設計の表の鍵と引数の個数どおり`() {
        val expected = mapOf(
            "notification.rules.noRole" to 0,
            "notification.rules.noRole.untracked" to 0,
            "notification.rules.noRole.nearby" to 2,
            "notification.rules.noRole.stale" to 0,
            "notification.rules.noRole.unexpectedUnder" to 1,
            "notification.rules.nearbyRoles" to 0,
            "notification.rules.openDefinition" to 0,
            "notification.rules.dismiss.tooltip" to 0,
            "popup.rules.section.nearby" to 0,
            "popup.rules.nearby.row" to 2,
            "popup.rules.nearby.none" to 0,
            "popup.rules.copySuggestion" to 1,
            "popup.rules.unresolved" to 1,
            "popup.rules.action.refresh" to 0,
            "settings.rules.noRole" to 0,
            "action.katachi.CopyRoleSuggestion.text" to 0,
        )
        val argument = Regex("""\{(\d+)""")
        for ((key, count) in expected) {
            for ((name, bundle) in listOf("en" to english, "ja" to japanese)) {
                val text = bundle.getProperty(key) ?: throw AssertionError("$key is not in the $name bundle")
                assertEquals("$name $key", count, argument.findAll(text).map { it.groupValues[1] }.toSet().size)
            }
        }
    }

    fun `test 古さの注記と近い Role のリンクは日英とも文言そのものが決まっている`() {
        assertEquals("(stale definition)", english.getProperty("notification.rules.noRole.stale"))
        assertEquals("（定義が古い）", japanese.getProperty("notification.rules.noRole.stale"))
        assertEquals("近い Role…", japanese.getProperty("notification.rules.nearbyRoles"))
        assertEquals("Nearby roles…", english.getProperty("notification.rules.nearbyRoles"))
    }

    private fun rulesKeys(): List<String> = english.stringPropertyNames().filter {
        it.startsWith("notification.rules.") || it.startsWith("popup.rules.") || it.startsWith("settings.rules.") || it.startsWith("action.katachi.CopyRoleSuggestion.")
    }.sorted()

    private companion object {
        const val RULES_KEYS = 16
        const val MIN_KEYS_IN_CODE = 40
    }
}
