package me.tbsten.katachi.intellij.testing

import me.tbsten.katachi.intellij.data.sarif.parseSarifJson
import me.tbsten.katachi.intellij.model.ModuleId
import me.tbsten.katachi.intellij.model.ViolationModel
import java.nio.file.Paths

/**
 * The SARIF a failing `ktFileContent { }` writes, as katachi-konsist's own test wrote it
 * (`contract/json/katachi-ktfilecontent-sarif.json`, a copy of `katachi-konsist/src/test/resources/sarif/ktfilecontent.sarif`).
 * The tests of the editor's annotations take their messages and properties from it, so they cannot drift away from what katachi writes.
 *
 * ```kotlin
 * val missing = KtFileContentGolden.byDeclaration("DefaultGetUserUseCase")
 * ```
 */
internal object KtFileContentGolden {
    private val module = ModuleId(Paths.get("/w/repo/android"), ":architecture-test")

    /** Every result of the golden, in the order katachi wrote them. */
    val results: List<ViolationModel> by lazy {
        parseSarifJson(
            DefinitionFixtures.sarifText("katachi-ktfilecontent-sarif"),
            Paths.get("/w/repo/android/architecture-test/build/reports/katachi/katachi.sarif"),
            module,
        )
    }

    /** The result about [declaration] (its `properties.declaration`). */
    fun byDeclaration(declaration: String): ViolationModel =
        results.single { it.properties["declaration"] == declaration }

    /**
     * [result] as a [SarifResult] for a file at [uri]: the same message and properties (but the path of the file), the line the golden has.
     */
    fun sarifResultAt(result: ViolationModel, uri: String): SarifResult = SarifResult(
        kind = result.label,
        uri = uri,
        line = result.location?.startLine,
        message = result.message,
        properties = result.properties - "kind" - "path",
    )
}
