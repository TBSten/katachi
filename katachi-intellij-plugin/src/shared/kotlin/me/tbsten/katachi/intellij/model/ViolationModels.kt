package me.tbsten.katachi.intellij.model

import java.nio.file.Path
import java.time.Instant

/**
 * The violations as `ViolationsService.state` publishes them (design 4.7), read from `katachi.sarif`.
 *
 * ```kotlin
 * val violations = (service.state.value as? ViolationsState.Ready)?.runs.orEmpty()
 * ```
 */
internal sealed interface ViolationsState {
    /** No SARIF file exists: the check has not run (or `report { sarif = true }` is not set). */
    data object NotRun : ViolationsState

    /**
     * [runs] is every result of every `runs[]` of every SARIF file read, in one list. [stale] is
     * whether the project changed after [readAt]. [paths] are the files read; the time of the check
     * the user sees, and the key of a dismissed notice, is [readAt].
     */
    data class Ready(
        val runs: List<ViolationModel>,
        val readAt: Instant,
        val stale: Boolean,
        val paths: List<Path>,
    ) : ViolationsState

    /** A SARIF file could not be read. The earlier answer, [previous], is kept. */
    data class Unreadable(val path: Path, val reason: String, val previous: Ready?) : ViolationsState
}

/** The [ViolationsState.Ready] to answer from: the state itself, or the one [ViolationsState.Unreadable] still carries. */
internal val ViolationsState.answer: ViolationsState.Ready?
    get() = when (this) {
        ViolationsState.NotRun -> null
        is ViolationsState.Ready -> this
        is ViolationsState.Unreadable -> previous
    }

/** A place in a file. [path] is the SARIF `uri` made absolute with `originalUriBaseIds`; it need not exist (`MissingFile` points at a file that is not there). */
internal data class SarifLocation(val path: Path, val startLine: Int?)

/** SARIF's `level`. A level this plugin does not know is read as [Warning]. */
internal enum class SarifLevel(val jsonName: String) {
    Error("error"),
    Warning("warning"),
    Note("note"),
    None("none"),
    ;

    companion object {
        fun of(name: String?): SarifLevel = entries.firstOrNull { it.jsonName == name } ?: Warning
    }
}

/** One result of the check: a `results[]` entry of `katachi.sarif`. */
internal data class ViolationModel(
    val module: ModuleId,
    /** `katachi.UnexpectedFile`. */
    val ruleId: String,
    /** The rule's `name`, katachi's `Violation.label` such as `UnsatisfiedFileConstraint`. */
    val label: String,
    val level: SarifLevel,
    val message: String,
    /** `locations[0]`. */
    val location: SarifLocation?,
    val related: List<SarifLocation>,
    /** `kind` / `severity` / `path`, and per kind `role` / `constraint` / `declaration` / `layoutPath` / `declaredAt` / `check` / `baseline`. */
    val properties: Map<String, String>,
)
