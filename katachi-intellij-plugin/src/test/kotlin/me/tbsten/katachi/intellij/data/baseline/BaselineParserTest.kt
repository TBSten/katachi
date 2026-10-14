package me.tbsten.katachi.intellij.data.baseline

import junit.framework.TestCase
import java.nio.file.Files

internal class BaselineParserTest : TestCase() {
    private fun read(text: String): BaselineLedger = (parseBaselineText(text) as? BaselineReading.Read)?.ledger ?: throw AssertionError("not read: ${parseBaselineText(text)}")

    private val version1 = """
        {
          "version": 1,
          "checks": {
            "me.tbsten.katachi.check.FileConstraintCheck": [
              {"rule": "UnsatisfiedFileConstraint", "path": "src/Legacy.kt", "role": "r", "constraint": "c", "declaration": "d"}
            ],
            "me.tbsten.katachi.check.LayoutCheck": [
              {"rule": "CaptureMismatch", "path": "src/Mismatch.kt"},
              {"rule": "UnexpectedDirectory", "path": "data/src/main/old", "count": 2},
              {"rule": "UnexpectedFile", "path": "src/Unknown.kt"}
            ]
          }
        }
    """.trimIndent()

    fun `test version 1 の baseline は rule と path で引ける`() {
        val ledger = read(version1)
        assertTrue(ledger.holdsFile("src/Unknown.kt"))
        assertTrue(ledger.holdsCaptureMismatch("src/Mismatch.kt"))
        // Only LayoutCheck's entries are kept: the three rules are its, and another check's entry is not.
        assertFalse(ledger.holds("UnsatisfiedFileConstraint", "src/Legacy.kt"))
    }

    fun `test 空の checks でも version 1 なら読めて何も許容しない`() {
        val ledger = read("""{"version": 1, "checks": {}}""")
        assertFalse(ledger.holdsFile("src/Unknown.kt"))
    }

    fun `test 知らない版は中身にかかわらず UnknownVersion`() {
        assertEquals(BaselineReading.UnknownVersion(2), parseBaselineText("""{"version": 2, "checks": {"x": [{"rule": "UnexpectedFile", "path": "a"}]}}"""))
        assertEquals(BaselineReading.UnknownVersion(null), parseBaselineText("""{"version": 1.5, "checks": {}}"""))
        // A newer file may be shaped differently: its version is judged before the rest.
        assertEquals(BaselineReading.UnknownVersion(3), parseBaselineText("""{"version": 3, "checks": []}"""))
    }

    fun `test 壊れた JSON や形の違うものは Unreadable`() {
        listOf(
            "",
            "{",
            "<<<<<<< HEAD",
            "[]",
            """{"checks": {}}""",
            """{"version": "1", "checks": {}}""",
            """{"version": 1}""",
            """{"version": 1, "checks": []}""",
            """{"version": 1, "checks": {"x": {}}}""",
            """{"version": 1, "checks": {"x": [1]}}""",
            """{"version": 1, "checks": {"x": [{"rule": "UnexpectedFile"}]}}""",
            """{"version": 1, "checks": {"x": [{"rule": 1, "path": "a"}]}}""",
        ).forEach { assertTrue("$it -> ${parseBaselineText(it)}", parseBaselineText(it) is BaselineReading.Unreadable) }
    }

    fun `test katachi が InvalidBaselineFile にするものは Unreadable`() {
        val layout = "me.tbsten.katachi.check.LayoutCheck"
        fun entries(vararg entry: String) = """{"version": 1, "checks": {"$layout": [${entry.joinToString(",")}]}}"""
        val unreadable = mapOf(
            "知らない欄 (最上位)" to """{"version": 1, "future": true, "checks": {}}""",
            "知らない欄 (項目)" to entries("""{"rule": "UnexpectedFile", "path": "a", "later": 1}"""),
            "同じキーの項目が 2 つ" to entries("""{"rule": "UnexpectedFile", "path": "a"}""", """{"rule": "UnexpectedFile", "path": "a"}"""),
            "同じキーの項目が count 違いで 2 つ" to entries("""{"rule": "UnexpectedFile", "path": "a"}""", """{"rule": "UnexpectedFile", "path": "a", "count": 2}"""),
            "JSON の名前の重複 (最上位)" to """{"version": 1, "checks": {}, "checks": {}}""",
            "JSON の名前の重複 (項目)" to entries("""{"rule": "UnexpectedFile", "path": "a", "path": "b"}"""),
            "JSON の名前の重複 (checks)" to """{"version": 1, "checks": {"$layout": [], "$layout": []}}""",
            "count が 0" to entries("""{"rule": "UnexpectedFile", "path": "a", "count": 0}"""),
            "count が負" to entries("""{"rule": "UnexpectedFile", "path": "a", "count": -1}"""),
            "count が小数" to entries("""{"rule": "UnexpectedFile", "path": "a", "count": 1.5}"""),
            "count が 1.0" to entries("""{"rule": "UnexpectedFile", "path": "a", "count": 1.0}"""),
            "count が文字列" to entries("""{"rule": "UnexpectedFile", "path": "a", "count": "2"}"""),
            "count が大きすぎる" to entries("""{"rule": "UnexpectedFile", "path": "a", "count": 99999999999}"""),
            "role が文字列でない" to entries("""{"rule": "UnexpectedFile", "path": "a", "role": 1}"""),
            "version が 0" to """{"version": 0, "checks": {}}""",
            "version が負" to """{"version": -1, "checks": {}}""",
        )
        for ((name, text) in unreadable) {
            assertTrue("$name -> ${parseBaselineText(text)}", parseBaselineText(text) is BaselineReading.Unreadable)
        }
    }

    fun `test 別の check の項目や role 違いの項目は別のキーで 読める`() {
        val text = """{"version": 1, "checks": {
            "me.tbsten.katachi.check.LayoutCheck": [{"rule": "UnexpectedFile", "path": "a"}, {"rule": "UnexpectedFile", "path": "a", "role": "r"}, {"rule": "UnexpectedFile", "path": "b", "role": null, "count": 3}],
            "other.Check": [{"rule": "UnexpectedFile", "path": "a"}, {"rule": "UnexpectedFile", "path": "zzz"}]
        }}"""
        val ledger = read(text)
        assertTrue(ledger.holdsFile("a"))
        assertTrue(ledger.holdsFile("b"))
        // Another check's UnexpectedFile is not the layout's.
        assertFalse(ledger.holdsFile("zzz"))
    }

    fun `test ファイルが無ければ Missing 読めれば Read`() {
        val dir = Files.createTempDirectory("baseline")
        try {
            assertEquals(BaselineReading.Missing, readBaselineFile(dir.resolve("katachi-baseline.json")))
            Files.writeString(dir.resolve("katachi-baseline.json"), version1)
            assertTrue(readBaselineFile(dir.resolve("katachi-baseline.json")) is BaselineReading.Read)
            Files.write(dir.resolve("bad.json"), byteArrayOf(0xC3.toByte(), 0x28))
            assertTrue(readBaselineFile(dir.resolve("bad.json")) is BaselineReading.Unreadable)
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    fun `test 実物の sample jvm の baseline は LegacyHealthCheck を UnexpectedFile で許容する`() {
        val text = BaselineParserTest::class.java.getResourceAsStream("/contract/gutter/sources/jvm/katachi-baseline.json")!!.use { String(it.readBytes()) }
        val ledger = read(text)
        assertTrue(ledger.holdsFile("src/main/kotlin/com/example/service/LegacyHealthCheck.kt"))
        assertTrue(ledger.holdsCaptureMismatch("src/main/kotlin/com/example/controller/health/LegacyStatusController.kt"))
        assertFalse(ledger.holdsFile("src/main/kotlin/com/example/service/LegacyStatusService.kt"))
    }

    fun `test 照合は katachi と同じく path の文字列が完全に等しいものだけ 先頭一致や大小無視はしない`() {
        val ledger = read(version1)
        assertFalse(ledger.holdsFile("src/Unknown.kt/"))
        assertFalse(ledger.holdsFile("./src/Unknown.kt"))
        assertFalse(ledger.holdsFile("src/unknown.kt"))
        assertFalse(ledger.holdsFile("src"))
        // The rule counts: the path of a CaptureMismatch entry is not an UnexpectedFile.
        assertFalse(ledger.holdsFile("src/Mismatch.kt"))
    }

    fun `test UnexpectedDirectory の項目はそのディレクトリと完全に等しい path だけ 下のファイルや祖先は許容しない`() {
        val ledger = read(version1)
        assertTrue(ledger.holdsDirectory("data/src/main/old"))
        assertFalse(ledger.holdsDirectory("data/src/main/old/Deep/File.kt"))
        assertFalse(ledger.holdsDirectory("data/src/main/old/Deep"))
        assertFalse(ledger.holdsDirectory("data/src/main/older"))
        assertFalse(ledger.holdsDirectory("data/src/main"))
        // An UnexpectedFile entry never holds a directory, and a directory entry never holds a file by exact match.
        assertFalse(ledger.holdsDirectory("src/Unknown.kt"))
        assertFalse(ledger.holdsFile("data/src/main/old"))
    }
}
