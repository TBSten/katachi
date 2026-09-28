package me.tbsten.katachi.intellij.model

import java.nio.file.Path

internal enum class WrittenKind { New, Overwritten }

/** A file `katachiTemplate` reported writing, as an absolute path. */
internal data class GeneratedFile(val path: Path, val kind: WrittenKind)

/** Why one template of a generation failed. */
internal sealed interface GenerationFailure {
    /** katachi ran and said `[FAILED] template`; [body] is its indented message, dedented. */
    data class Katachi(val body: List<String>) : GenerationFailure

    /** The run never reached katachi's processor (compilation, unknown argument, ...). */
    data class NotReached(val failure: GradleFailure) : GenerationFailure
}

/** How one checked template of a generation ended. */
internal sealed interface GenerationItemResult {
    /**
     * Written. [writesUnknown]: katachi said `[OK]` without naming files (the `template` key runs
     * another processor, E-42). [outputIncomplete]: the run succeeded but the summary line never
     * came (E-37). In both cases [existingExpected] lists the expected paths that exist.
     */
    data class Generated(
        val files: List<GeneratedFile>,
        val writesUnknown: Boolean = false,
        val outputIncomplete: Boolean = false,
        val existingExpected: List<Path> = emptyList(),
    ) : GenerationItemResult

    /** Nothing written because files existed: `onExisting=skip`, or "skip" in the conflict dialog. */
    data class Skipped(val existing: List<Path>) : GenerationItemResult

    data class Failed(val failure: GenerationFailure, val output: List<String>) : GenerationItemResult

    /** "Stop here" in the conflict dialog: nothing written for this one, the rest not run (E-17). */
    data class StoppedAtConflict(val existing: List<Path>) : GenerationItemResult

    /** Cancelled while running; whether it wrote is unknown (E-21). */
    data class Interrupted(val existingExpected: List<Path>) : GenerationItemResult

    data object NotRun : GenerationItemResult
}

/**
 * One `katachiTemplate` run: every checked row it covered ([templateIds], several when they share
 * a module, design draft section 6 "IDE の複数選択") and how it ended, shared by all of them.
 */
internal data class GenerationItemReport(val templateIds: List<TemplateId>, val result: GenerationItemResult)

/** Every run of a generation, in list order, with how it ended. */
internal data class GenerationReport(val items: List<GenerationItemReport>) {
    /** Every checked template across every run, in list order. */
    val templateIds: List<TemplateId> get() = items.flatMap { it.templateIds }

    val succeededCount: Int
        get() = items.sumOf { if (it.result is GenerationItemResult.Generated) it.templateIds.size else 0 }

    val isComplete: Boolean
        get() = items.all { it.result is GenerationItemResult.Generated || it.result is GenerationItemResult.Skipped }

    /** "Retry the rest" keeps these checked: failed, stopped, interrupted, not run (E-38). */
    val retryTargets: List<TemplateId>
        get() = items.filter {
            it.result !is GenerationItemResult.Generated && it.result !is GenerationItemResult.Skipped
        }.flatMap { it.templateIds }

    /** Every written file, in list order and then output order. */
    val writtenFiles: List<GeneratedFile>
        get() = items.flatMap { (it.result as? GenerationItemResult.Generated)?.files.orEmpty() }
}

/** The user's answer in the conflict dialog. */
internal enum class ConflictChoice { Overwrite, SkipAndContinue, Stop }

/** What the conflict dialog shows: which run (k of n), every row it covers, and the files already there. */
internal data class ConflictQuestion(
    val templateIds: List<TemplateId>,
    val index: Int,
    val total: Int,
    val existing: List<Path>,
)
