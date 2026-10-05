package me.tbsten.katachi.intellij.data.sarif

import me.tbsten.katachi.intellij.model.ModuleId
import me.tbsten.katachi.intellij.model.SarifLevel
import me.tbsten.katachi.intellij.model.SarifLocation
import me.tbsten.katachi.intellij.testing.DefinitionFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Paths

/** The minimal SARIF reader (design 4.7): the results the IDE shows, with their paths made absolute. */
class SarifJsonParserTest {
    private val module = ModuleId(Paths.get("/w"), ":arch")
    private val file = Paths.get("/w/my repo/arch/build/reports/katachi/katachi.sarif")
    private val text = DefinitionFixtures.sarifText("handwritten-katachi-sarif")
    private val results = parseSarifJson(text, file, module)

    private inline fun <reified T : Throwable> failureOf(json: String): T =
        runCatching { parseSarifJson(json, file, module) }.exceptionOrNull() as? T ?: throw AssertionError("expected ${T::class.simpleName}")

    @Test
    fun `すべての run の results が1つの列になる`() {
        assertEquals(listOf("katachi.UnexpectedFile", "katachi.MissingFile", "katachi.MissingDescription", "katachi.Custom"), results.map { it.ruleId })
        assertTrue(results.all { it.module == module })
    }

    @Test
    fun `SRCROOT の uri は基準に続けて segment ごとに percent-decode した絶対パスになる`() {
        val unexpected = results[0]
        assertEquals(SarifLocation(Paths.get("/w/my repo/src/main/kotlin/com/example/Stray File#.kt"), null), unexpected.location)
        assertEquals("UnexpectedFile", unexpected.label)
        assertEquals(SarifLevel.Error, unexpected.level)
        assertEquals("No role is defined for this file.", unexpected.message)
    }

    @Test
    fun `行は region の startLine で relatedLocations は SRCROOT のものも file の絶対のものも読む`() {
        val missing = results[1]
        assertEquals(SarifLocation(Paths.get("/w/my repo/architecture-test/src/test/kotlin/roles/ApplicationConfigRole.kt"), 35), missing.location)
        assertEquals(
            listOf(
                SarifLocation(Paths.get("/w/my repo/src/main/resources/application.conf"), null),
                SarifLocation(Paths.get("/elsewhere/あ.kt"), 3),
            ),
            missing.related,
        )
    }

    @Test
    fun `properties は文字列と数と真偽を文字として持ち null と入れ子は捨てる`() {
        assertEquals(
            mapOf("kind" to "Missing", "severity" to "Error", "path" to "src/main/resources/application.conf", "role" to "app.ApplicationConfig", "declaredAt" to "ApplicationConfigRole.kt:35", "count" to "2", "flag" to "true"),
            results[1].properties,
        )
    }

    @Test
    fun `ruleIndex が無ければ ruleId で rule を引き level の既定は rule から取る`() {
        val description = results[2]
        assertEquals("MissingDescription", description.label)
        assertEquals(SarifLevel.Warning, description.level)
        // A relative uri with no base cannot be made absolute.
        assertNull(description.location)
    }

    @Test
    fun `rules に無い ruleId は後ろの語を label にし level の指定を読む`() {
        val custom = results[3]
        assertEquals("Custom", custom.label)
        assertEquals(SarifLevel.Note, custom.level)
        assertEquals("second run", custom.message)
        assertNull(custom.location)
    }

    @Test
    fun `プラス記号を含む名前は空白にならない`() {
        val json = """{"version":"2.1.0","runs":[{"originalUriBaseIds":{"%SRCROOT%":{"uri":"file:///w/"}},"results":[
            {"ruleId":"katachi.UnexpectedFile","message":{"text":"x"},"locations":[{"physicalLocation":{"artifactLocation":{"uri":"a+b/c%2Bd.kt","uriBaseId":"%SRCROOT%"}}}]}]}]}"""
        assertEquals(Paths.get("/w/a+b/c+d.kt"), parseSarifJson(json, file, module).single().location?.path)
    }

    @Test
    fun `壊れた percent 符号はそのまま残して読む`() {
        val json = """{"version":"2.1.0","runs":[{"originalUriBaseIds":{"%SRCROOT%":{"uri":"file:///w/"}},"results":[
            {"ruleId":"katachi.UnexpectedFile","message":{"text":"x"},"locations":[{"physicalLocation":{"artifactLocation":{"uri":"a%zz.kt","uriBaseId":"%SRCROOT%"}}}]}]}]}"""
        assertEquals(Paths.get("/w/a%zz.kt"), parseSarifJson(json, file, module).single().location?.path)
    }

    @Test
    fun `results の無い run は空で runs が空なら空`() {
        assertEquals(emptyList<Any>(), parseSarifJson("""{"version":"2.1.0","runs":[{}]}""", file, module))
        assertEquals(emptyList<Any>(), parseSarifJson("""{"version":"2.1.0","runs":[]}""", file, module))
    }

    @Test
    fun `version が 2_1_0 でなければ読まない`() {
        assertEquals("2.0.0", failureOf<KatachiUnsupportedSarifVersionException>("""{"version":"2.0.0","runs":[]}""").found)
        assertNull(failureOf<KatachiUnsupportedSarifVersionException>("""{"runs":[]}""").found)
    }

    @Test
    fun `runs が無ければ Incompatible`() {
        assertEquals("runs", failureOf<KatachiIncompatibleSarifException>("""{"version":"2.1.0"}""").location)
    }

    @Test
    fun `書きかけの途中で切れた SARIF は Malformed`() {
        failureOf<KatachiMalformedSarifException>(text.take(text.length / 2))
        failureOf<KatachiMalformedSarifException>("")
    }
}
