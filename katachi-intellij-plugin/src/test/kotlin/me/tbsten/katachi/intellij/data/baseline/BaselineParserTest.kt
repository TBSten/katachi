package me.tbsten.katachi.intellij.data.baseline

import junit.framework.TestCase
import java.nio.file.Files

internal class BaselineParserTest : TestCase() {
    private fun read(text: String): BaselineLedger = (parseBaselineText(text) as? BaselineReading.Read)?.ledger ?: throw AssertionError("not read: ${parseBaselineText(text)}")

    private val version1 = """
        {
          "version": 1,
          "checks": {
            "a.FileConstraintCheck": [
              {"rule": "UnsatisfiedFileConstraint", "path": "src/Legacy.kt", "role": "r", "constraint": "c", "declaration": "d"}
            ],
            "a.LayoutCheck": [
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
        assertTrue(ledger.holds("UnsatisfiedFileConstraint", "src/Legacy.kt"))
    }

    fun `test 空の checks でも version 1 なら読めて何も許容しない`() {
        val ledger = read("""{"version": 1, "checks": {}}""")
        assertFalse(ledger.holdsFile("src/Unknown.kt"))
    }

    fun `test 知らない版は中身にかかわらず UnknownVersion`() {
        assertEquals(BaselineReading.UnknownVersion(2), parseBaselineText("""{"version": 2, "checks": {"x": [{"rule": "UnexpectedFile", "path": "a"}]}}"""))
        assertEquals(BaselineReading.UnknownVersion(0), parseBaselineText("""{"version": 0, "checks": {}}"""))
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

    fun `test 知らない項目は読み飛ばす`() {
        val ledger = read("""{"version": 1, "future": true, "checks": {"x": [{"rule": "UnexpectedFile", "path": "a", "later": 1}]}}""")
        assertTrue(ledger.holdsFile("a"))
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

    fun `test UnexpectedDirectory の項目はそのディレクトリとその下のファイルを許容し 名前が前方一致なだけの別ディレクトリは許容しない`() {
        val ledger = read(version1)
        assertTrue(ledger.holdsDirectoryAbove("data/src/main/old"))
        assertTrue(ledger.holdsDirectoryAbove("data/src/main/old/Deep/File.kt"))
        assertFalse(ledger.holdsDirectoryAbove("data/src/main/older/File.kt"))
        assertFalse(ledger.holdsDirectoryAbove("data/src/main"))
        // An UnexpectedFile entry never holds a directory, and a directory entry never holds a file by exact match.
        assertFalse(ledger.holdsDirectoryAbove("src/Unknown.kt"))
        assertFalse(ledger.holdsFile("data/src/main/old"))
    }
}
