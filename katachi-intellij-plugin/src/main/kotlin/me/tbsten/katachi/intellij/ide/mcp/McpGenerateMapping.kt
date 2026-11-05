package me.tbsten.katachi.intellij.ide.mcp

import me.tbsten.katachi.intellij.data.generate.ArgumentProblem
import me.tbsten.katachi.intellij.model.GenerationFailure
import me.tbsten.katachi.intellij.model.GenerationItemReport
import me.tbsten.katachi.intellij.model.GenerationItemResult
import me.tbsten.katachi.intellij.model.GenerationReport
import me.tbsten.katachi.intellij.model.WrittenKind
import me.tbsten.katachi.intellij.presentation.CaptureProblem
import me.tbsten.katachi.intellij.presentation.FieldError
import me.tbsten.katachi.intellij.presentation.GenerateBlocker

// katachi's own message is short; a failure that never reached it carries Gradle's output, which the IDE log keeps whole.
private const val MAX_MESSAGE_LINES = 40

internal fun GenerationReport.toDto(module: String, timedOut: Boolean): GenerateResult = GenerateResult(
    module = module,
    items = items.map { it.toDto() },
    writtenFiles = writtenFiles.map { it.path.toString() },
    timedOut = timedOut,
)

private fun GenerationItemReport.toDto(): GenerateItemDto {
    val templates = templateIds.map { it.template }
    return when (val result = result) {
        is GenerationItemResult.Generated -> GenerateItemDto(
            templates,
            "generated",
            files = result.files.map { GeneratedFileDto(it.path.toString(), if (it.kind == WrittenKind.New) "new" else "overwritten") },
        )
        is GenerationItemResult.Skipped -> GenerateItemDto(templates, "skipped", existingPaths = result.existing.map { it.toString() })
        is GenerationItemResult.Failed -> GenerateItemDto(templates, "failed", message = result.failure.text())
        is GenerationItemResult.StoppedAtConflict -> GenerateItemDto(templates, "stoppedAtConflict", existingPaths = result.existing.map { it.toString() })
        is GenerationItemResult.Interrupted -> GenerateItemDto(templates, "interrupted", existingPaths = result.existingExpected.map { it.toString() })
        GenerationItemResult.NotRun -> GenerateItemDto(templates, "notRun")
    }
}

private fun GenerationFailure.text(): String = when (this) {
    is GenerationFailure.Katachi -> body.take(MAX_MESSAGE_LINES).joinToString("\n")
    is GenerationFailure.NotReached -> failure.details.take(MAX_MESSAGE_LINES).joinToString("\n").ifEmpty { "Gradle failed before katachi ran" }
}

/** One problem of a katachi_generate request, as one ASCII sentence fragment. */
internal fun ArgumentProblem.text(): String = when (this) {
    is ArgumentProblem.UnknownArgument -> "$name is a parameter of none of the templates"
    is ArgumentProblem.Field -> when (val blocker = blocker) {
        is GenerateBlocker.InvalidField -> "$template: ${blocker.error.text(blocker.parameterName)}"
        is GenerateBlocker.Unavailable -> "$template cannot be generated: its preview failed, or a parameter has a kind this plugin does not know"
        is GenerateBlocker.UnresolvedPath -> "$template: the arguments do not decide where ${blocker.fileName} goes"
        else -> "$template: $blocker"
    }
}

private fun FieldError.text(parameter: String): String = when (this) {
    FieldError.Required -> "$parameter is required"
    is FieldError.NotAnInt -> "$parameter must be a decimal Int"
    is FieldError.NotAcceptedValue -> "$parameter must be one of ${acceptedValues.joinToString(", ")}, not $value"
    is FieldError.InvalidCapture -> when (problem) {
        CaptureProblem.Separator -> "$parameter must be one directory level, without / or \\"
        CaptureProblem.Dot -> "$parameter cannot be . or .."
    }
    is FieldError.NotAnExistingModule ->
        "$parameter must name an existing module (${existing.joinToString(", ").ifEmpty { "none exists" }})"
}
