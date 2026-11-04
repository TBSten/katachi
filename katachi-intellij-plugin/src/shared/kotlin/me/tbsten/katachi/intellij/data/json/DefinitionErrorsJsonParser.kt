package me.tbsten.katachi.intellij.data.json

import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.DefinitionErrorModel
import me.tbsten.katachi.intellij.model.DefinitionErrorPhase
import me.tbsten.katachi.intellij.model.ModuleId
import java.nio.file.Path

internal val SUPPORTED_DEFINITION_ERRORS_SCHEMA_VERSIONS: List<Int> = listOf(1)

// Never throws: a broken file must not hide the failure it sits beside; `null` means classify from Gradle's output.
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
