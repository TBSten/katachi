package me.tbsten.katachi.intellij.data.sarif

import me.tbsten.katachi.intellij.model.ModuleId
import me.tbsten.katachi.intellij.model.SarifLevel
import me.tbsten.katachi.intellij.model.SarifLocation
import me.tbsten.katachi.intellij.testing.DefinitionFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Paths

/**
 * The reader against the `katachi.sarif` that `sample/jvm`'s `report { sarif = true }` really writes
 * (`contract/json/real-jvm-*-sarif.json`; only the `%SRCROOT%` base is replaced by `file:///__ROOT__/`).
 * `clean` is the file of a green run, `violations` the one of the same run with `katachi-baseline.json` emptied,
 * so that the three violations the baseline holds back come out (a `CaptureMismatch`, an `UnexpectedFile` and an
 * `UnsatisfiedFileConstraint`).
 */
class RealSarifContractTest {
    private val root = Paths.get("/__ROOT__")
    private val module = ModuleId(root, ":architecture-test")
    private val file = root.resolve("architecture-test/build/reports/katachi/katachi.sarif")

    @Test
    fun `違反の無い実行の katachi sarif は空の列として読める`() {
        assertEquals(emptyList<Any>(), parseSarifJson(DefinitionFixtures.sarifText("real-jvm-clean-sarif"), file, module))
    }

    @Test
    fun `baseline を空にした実行の違反は ruleId と label と level と場所と properties まで読める`() {
        val results = parseSarifJson(DefinitionFixtures.sarifText("real-jvm-violations-sarif"), file, module)

        assertEquals(
            listOf(
                Triple("katachi.CaptureMismatch", "CaptureMismatch", SarifLevel.Error),
                Triple("katachi.UnexpectedFile", "UnexpectedFile", SarifLevel.Error),
                Triple("katachi.UnsatisfiedFileConstraint", "UnsatisfiedFileConstraint", SarifLevel.Error),
            ),
            results.map { Triple(it.ruleId, it.label, it.level) },
        )
        assertEquals(
            listOf(
                SarifLocation(root.resolve("src/main/kotlin/com/example/controller/health/LegacyStatusController.kt"), null),
                SarifLocation(root.resolve("src/main/kotlin/com/example/service/LegacyHealthCheck.kt"), null),
                SarifLocation(root.resolve("src/main/kotlin/com/example/service/LegacyStatusService.kt"), 11),
            ),
            results.map { it.location },
        )

        val constraint = results[2]
        assertEquals(listOf(SarifLocation(root.resolve("architecture-test/src/test/kotlin/com/example/domain/ServiceRole.kt"), 76)), constraint.related)
        assertEquals("domain.Service", constraint.properties["role"])
        assertEquals("Must be public and import models and Repositories only, never HTTP (ktFileContent)", constraint.properties["constraint"])
        assertEquals("LegacyStatusService", constraint.properties["declaration"])
        assertEquals("ServiceRole.kt:76", constraint.properties["declaredAt"])
        assertEquals("Unexpected", results[0].properties["kind"])
        assertEquals("Unexpected", results[1].properties["kind"])
        assertTrue(results[0].message.startsWith("The file matches a pattern of role api.Controller, but the levels named by capture(\"resource\") do not agree."))
    }
}
