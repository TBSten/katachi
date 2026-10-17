package me.tbsten.katachi.intellij.data.json

import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.DefinitionErrorModel
import me.tbsten.katachi.intellij.model.DefinitionErrorPhase
import me.tbsten.katachi.intellij.model.ModuleId
import java.nio.file.Path

/** The `schemaVersion` values of `definition-errors.json` this plugin reads. */
internal val SUPPORTED_DEFINITION_ERRORS_SCHEMA_VERSIONS: List<Int> = listOf(1)

/**
 * Reads `definition-errors.json` (design 2.1), which a katachi run given `--definition-errors` writes
 * next to `definition.json`, into the mistakes of [module]'s definition. Unknown keys are ignored.
 *
 * A file that cannot be read -- cut off, of another `schemaVersion`, a key missing or mistyped, an
 * unknown `phase` -- is as good as no file: the answer is `null` and the load is classified from
 * Gradle's output as before. This never throws, so a broken file cannot hide the failure it sits beside.
 *
 * ```kotlin
 * val errors = parseDefinitionErrorsJson(path.readText(), path, module.id) ?: return null
 * val lines = errors.mapNotNull { it.site?.line }
 * ```
 */
internal fun parseDefinitionErrorsJson(text: String, file: Path, module: ModuleId): List<DefinitionErrorModel>? = try {
    val root = DefinitionJsonReader(parseJsonText(text, file), "$", file)
    if (root.int("schemaVersion") !in SUPPORTED_DEFINITION_ERRORS_SCHEMA_VERSIONS) {
        null
    } else {
        root.array("errors").map { it.error(module) }
    }
} catch (failure: KatachiDefinitionJsonException) {
    null
} catch (failure: KatachiTemplateJsonException) {
    null
} catch (failure: UnknownPhaseException) {
    null
} catch (failure: StackOverflowError) {
    // A document nested deeper than the parser's recursion; nothing katachi writes.
    null
}

private class UnknownPhaseException : IllegalStateException()

private fun DefinitionJsonReader.error(module: ModuleId) = DefinitionErrorModel(
    module = module,
    phase = when (string("phase")) {
        "entryPoint" -> DefinitionErrorPhase.EntryPoint
        "processor" -> DefinitionErrorPhase.Processor
        else -> throw UnknownPhaseException()
    },
    processor = nullableString("processor"),
    exceptionClassName = string("exception"),
    message = string("message"),
    site = nullableObj("declaredAt")?.let { DeclarationSiteRef(it.string("fileName"), it.int("line")) },
)
