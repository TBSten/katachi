package me.tbsten.katachi.intellij.data.sarif

import me.tbsten.katachi.intellij.model.ModuleId
import me.tbsten.katachi.intellij.model.SarifLevel
import me.tbsten.katachi.intellij.model.SarifLocation
import me.tbsten.katachi.intellij.testing.DefinitionFixtures
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

/**
 * The reader against the SARIF katachi's own golden holds (`every-kind.sarif`, a copy in `contract/json/`): every kind of
 * result, the repository prefix (`android/...`) of a uri, and a base that is the repository root rather than the project.
 */
class SarifJsonParserGoldenTest {
    private val module = ModuleId(Paths.get("/w/repo/android"), ":architecture-test")
    private val file = Paths.get("/w/repo/android/architecture-test/build/reports/katachi/katachi.sarif")
    private val results = parseSarifJson(DefinitionFixtures.sarifText("katachi-every-kind-sarif"), file, module)

    private fun at(path: String, line: Int? = null) = SarifLocation(Paths.get("/w/repo/$path"), line)

    @Test
    fun `14 件すべての ruleId と label と level が rules から復元できる`() {
        assertEquals(
            listOf(
                Triple("katachi.UnexpectedFile", "UnexpectedFile", SarifLevel.Error),
                Triple("katachi.UnexpectedDirectory", "UnexpectedDirectory", SarifLevel.Error),
                Triple("katachi.CaptureMismatch", "CaptureMismatch", SarifLevel.Error),
                Triple("katachi.MissingFile", "MissingFile", SarifLevel.Error),
                Triple("katachi.UnsatisfiedFileConstraint", "UnsatisfiedFileConstraint", SarifLevel.Error),
                Triple("TodoRule", "TodoRule", SarifLevel.Error),
                Triple("BareRule", "BareRule", SarifLevel.Warning),
                Triple("katachi.StaleBaselineEntry", "StaleBaselineEntry", SarifLevel.Error),
                Triple("katachi.AmbiguousLayout", "AmbiguousLayout", SarifLevel.Warning),
                Triple("katachi.MissingDescription", "MissingDescription", SarifLevel.Warning),
                Triple("katachi.UncheckedFile", "UncheckedFile", SarifLevel.Error),
                Triple("katachi.UncheckedDirectory", "UncheckedDirectory", SarifLevel.Error),
                Triple("katachi.UncheckedCheck", "UncheckedCheck", SarifLevel.Error),
                Triple("katachi.UncheckedFileConstraint", "UncheckedFileConstraint", SarifLevel.Error),
            ),
            results.map { Triple(it.ruleId, it.label, it.level) },
        )
    }

    @Test
    fun `location は base に repository の prefix を続けた絶対パスと行になる`() {
        val roles = "android/architecture-test/src/test/kotlin/com/example"
        assertEquals(
            listOf(
                at("android/app/src/main/kotlin/com/example/Helper.kt"),
                at("$roles/ProjectArchitecture.kt", 12),
                at("android/feature/home/src/main/kotlin/com/example/feature/settings/HomeScreen.kt"),
                at("$roles/roles/UseCaseRole.kt", 16),
                at("android/core/domain/src/main/kotlin/com/example/domain/GetUserUseCase.kt", 7),
                at("android/app/src/main/kotlin/com/example/Broken.kt"),
                at("android/app/src/main/kotlin/com/example/Broken.kt"),
                at("android/katachi-baseline.json", 5),
                at("android/core/domain/src/main/kotlin/com/example/domain/Shared.kt"),
                at("$roles/roles/RepositoryRole.kt", 9),
                at("android/app/src/main/kotlin/com/example/Broken.kt"),
                at("$roles/ProjectArchitecture.kt", 12),
                at("$roles/ProjectArchitecture.kt", 12),
                at("$roles/roles/RepositoryRole.kt", 9),
            ),
            results.map { it.location },
        )
    }

    @Test
    fun `relatedLocations も同じ base で復元される`() {
        assertEquals(listOf(at("android/legacy")), results[1].related)
        assertEquals(listOf(at("android/architecture-test/src/test/kotlin/com/example/roles/UseCaseRole.kt", 16)), results[2].related)
        assertEquals(listOf(at("android/core/domain/src/main/kotlin/com/example/domain/GetOrderUseCase.kt")), results[3].related)
        assertEquals(listOf(at("android/architecture-test/src/test/kotlin/com/example/roles/RepositoryRole.kt", 9)), results[8].related)
    }

    @Test
    fun `プロジェクトルートそのものを指す uri の dot は取り除かれる`() {
        val json = """{"version":"2.1.0","runs":[{"originalUriBaseIds":{"%SRCROOT%":{"uri":"file:///w/repo/"}},"results":[
            {"ruleId":"katachi.UnexpectedDirectory","message":{"text":"x"},"locations":[{"physicalLocation":{"artifactLocation":{"uri":".","uriBaseId":"%SRCROOT%"}}}],
             "relatedLocations":[{"physicalLocation":{"artifactLocation":{"uri":"./a/./b.kt","uriBaseId":"%SRCROOT%"}}}]}]}]}"""
        val result = parseSarifJson(json, file, module).single()
        assertEquals(Paths.get("/w/repo"), result.location?.path)
        assertEquals(listOf(Paths.get("/w/repo/a/b.kt")), result.related.map { it.path })
    }

    @Test
    fun `contract の複製は katachi の golden と同じ`() {
        val golden = Paths.get("..", "katachi", "src", "test", "resources", "sarif", "golden", "every-kind.sarif")
        assumeTrue("katachi's golden is not next to the plugin", Files.isRegularFile(golden))
        assertEquals(Files.readString(golden), DefinitionFixtures.sarifText("katachi-every-kind-sarif"))
    }
}
