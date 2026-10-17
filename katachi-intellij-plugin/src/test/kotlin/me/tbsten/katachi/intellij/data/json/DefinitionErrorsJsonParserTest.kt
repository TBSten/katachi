package me.tbsten.katachi.intellij.data.json

import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.DefinitionErrorPhase
import me.tbsten.katachi.intellij.testing.ContractFixtures
import me.tbsten.katachi.intellij.testing.module
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * `definition-errors.json` (ide-authoring design 2.1) as katachi writes it. The two fixtures are katachi's own goldens
 * (`katachi/src/test/resources/definition/golden/`), copied byte for byte, so the plugin reads what katachi writes.
 */
class DefinitionErrorsJsonParserTest {
    private val file: Path = Paths.get("/fixture/definition-errors.json")
    private val moduleId = module(":arch").id

    private fun parse(text: String) = parseDefinitionErrorsJson(text, file, moduleId)

    private fun golden(name: String) = ContractFixtures.text("authoring/$name.json")

    @Test
    fun `katachi の golden を読むと誤りの列になり 宣言元の行と phase と例外名が取れる`() {
        val errors = parse(golden("definition-errors")) ?: throw AssertionError("not read")

        assertEquals(2, errors.size)
        val first = errors[0]
        assertEquals(DefinitionErrorPhase.Processor, first.phase)
        assertEquals("internalDefinitionJson", first.processor)
        assertEquals("me.tbsten.katachi.dsl.KatachiTemplateOnWildcardException", first.exceptionClassName)
        assertEquals(DeclarationSiteRef("ScreenRole.kt", 4), first.site)
        assertEquals(moduleId, first.module)
        assertTrue(first.message.lines().size == 3)
        assertTrue(first.message.startsWith("`.template { }` at ScreenRole.kt:4"))
    }

    @Test
    fun `宣言元が null の誤りと entryPoint の誤りと改行や引用符を含む本文をそのまま読む`() {
        val second = (parse(golden("definition-errors")) ?: throw AssertionError()).last()

        assertEquals(DefinitionErrorPhase.EntryPoint, second.phase)
        assertNull(second.processor)
        assertNull(second.site)
        assertEquals("first line\nsecond \"quoted\" line with a \\ backslash", second.message)
    }

    @Test
    fun `誤りが無い golden は空の列で 読めなかった null とは区別する`() {
        assertEquals(emptyList<Any>(), parse(golden("definition-errors-empty")))
    }

    @Test
    fun `知らないキーは読み飛ばす`() {
        val withExtra = golden("definition-errors-empty").replace("\"errors\": []", "\"errors\": [], \"future\": {\"a\": [1, 2]}")
        assertEquals(emptyList<Any>(), parse(withExtra))
    }

    @Test
    fun `読めないファイルは読まなかったことにして例外を外に出さない`() {
        val text = golden("definition-errors")
        val unreadable = mapOf(
            "空" to "",
            "途中で切れた" to text.take(text.length / 2),
            "JSON ではない" to "not json",
            "未知の schemaVersion" to text.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2"),
            "errors が無い" to text.replace("\"errors\"", "\"renamed\""),
            "未知の phase" to text.replace("\"entryPoint\"", "\"somewhere\""),
            "message が文字列ではない" to text.replace("\"message\": \"first line", "\"message\": 5, \"x\": \"first line"),
            "line が整数ではない" to text.replace("\"line\": 4", "\"line\": \"four\""),
            "ルートが配列" to "[]",
        )
        for ((reason, broken) in unreadable) assertNull(reason, parse(broken))
    }

    @Test
    fun `深く入れ子になった文書でも例外を外に出さない`() {
        val deep = "[".repeat(200_000)
        assertNull(parse(deep))
    }

    @Test
    fun `コピーした golden は katachi 側の golden と一字も違わない`() {
        val katachi = Paths.get("..", "katachi", "src", "test", "resources", "definition", "golden")
        assumeTrue("katachi/ is not next to the plugin here", Files.isDirectory(katachi))
        for (name in listOf("definition-errors", "definition-errors-empty")) {
            assertEquals("$name.json drifted from katachi's golden; copy it again", Files.readString(katachi.resolve("$name.json")), golden(name))
        }
        assertNotNull(parse(golden("definition-errors")))
    }
}
