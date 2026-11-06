package me.tbsten.katachi.intellij.ide.mcp

import me.tbsten.katachi.intellij.model.DefinitionErrorModel
import me.tbsten.katachi.intellij.model.DefinitionErrorPhase
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.Freshness
import me.tbsten.katachi.intellij.model.GradleFailure
import me.tbsten.katachi.intellij.model.LoadFailure
import me.tbsten.katachi.intellij.model.StaleReason
import me.tbsten.katachi.intellij.model.answer

// Gradle's output can run to thousands of lines; the tool window shows all of it.
private const val MAX_DETAIL_LINES = 40

internal val DefinitionState.jsonName: String
    get() = when (this) {
        DefinitionState.NotLoaded -> "notLoaded"
        is DefinitionState.Loading -> "loading"
        is DefinitionState.Ready -> "ready"
        is DefinitionState.Unavailable -> "unavailable"
    }

/** The failure behind this state: a reload that failed over an earlier answer, else `null`. */
internal val DefinitionState.reloadFailure: LoadFailure?
    get() = ((answer?.freshness as? Freshness.Stale)?.reason as? StaleReason.ReloadFailed)?.failure

internal fun LoadFailure.toDto(): LoadFailureDto = when (this) {
    is LoadFailure.Gradle -> LoadFailureDto(failure.kindName, failure.details.take(MAX_DETAIL_LINES))
    is LoadFailure.JsonMissing -> LoadFailureDto("jsonMissing", listOf("no JSON was written at $path"))
    is LoadFailure.MalformedJson -> LoadFailureDto("malformedJson", listOf("$path: $message"))
    is LoadFailure.IncompatibleJson -> LoadFailureDto("incompatibleJson", listOf("$path at $location: $message"))
    LoadFailure.Cancelled -> LoadFailureDto("cancelled", emptyList())
}

private val GradleFailure.kindName: String
    get() = when (this) {
        is GradleFailure.CompilationFailed -> "compilationFailed"
        is GradleFailure.TaskNotFound -> "taskNotFound"
        is GradleFailure.ArchitectureNotSet -> "architectureNotSet"
        is GradleFailure.ProcessorRejected -> "processorRejected"
        is GradleFailure.DefinitionRejected -> "definitionRejected"
        is GradleFailure.Other -> "gradle"
    }

internal fun DefinitionErrorModel.toDto(): DefinitionErrorDto = DefinitionErrorDto(
    module = module.gradlePath,
    phase = when (phase) {
        DefinitionErrorPhase.EntryPoint -> "entryPoint"
        DefinitionErrorPhase.Processor -> "processor"
    },
    processor = processor,
    exception = exceptionClassName,
    message = message,
    declaredAt = site?.let { "${it.fileName}:${it.line}" },
)
