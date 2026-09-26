package me.tbsten.katachi.intellij.model

import java.nio.file.Path

/** What the synced Gradle data says about katachi, before anything runs (spec 01). */
internal sealed interface DetectionResult {
    /** Definition modules, grouped per linked root and ordered by root, then Gradle path (E-05, E-32). */
    data class Found(val modules: List<KatachiModule>, val katachiVersions: Map<ModuleId, String>) : DetectionResult

    /** `katachiTemplates` exists but `katachiInternalTemplatesJson` does not: katachi before 0.3 (E-02). */
    data class Outdated(val katachiVersion: String?) : DetectionResult

    /** No module has either task (E-01). */
    data object NotInstalled : DetectionResult

    /** Gradle is linked but has not synced yet (E-03). */
    data object NotSynced : DetectionResult

    /** No Gradle build is linked at all (E-03). */
    data object NotGradle : DetectionResult

    /**
     * The sync carries no task list at all (Android Studio's "do not build the task list during
     * sync", E-30). [candidates] are the modules that depend on katachi; loading tries each and drops
     * the ones answering "task not found". Provisional until spike S-5.
     */
    data class TaskListMissing(val candidates: List<KatachiModule>) : DetectionResult
}

/** Why a Gradle run failed, read from its output. */
internal sealed interface GradleFailure {
    /** Lines worth showing under "Details": `e: …` lines, or the last lines of the output. */
    val details: List<String>

    /** `compile*Kotlin` failed: the definition does not compile (E-34, E-40). */
    data class CompilationFailed(override val details: List<String>) : GradleFailure

    /** Gradle knows no such task or project: the sync is stale (E-04). */
    data class TaskNotFound(val taskPath: String?, override val details: List<String>) : GradleFailure

    /** `katachi { architecture = ... }` is missing in the definition module (E-34). */
    data class ArchitectureNotSet(override val details: List<String>) : GradleFailure

    /**
     * katachi refused the command line before any processor ran, e.g. an unknown `--arg` (E-39).
     * [details] starts with the exception message.
     */
    data class ProcessorRejected(val exceptionClassName: String, override val details: List<String>) : GradleFailure

    data class Other(override val details: List<String>) : GradleFailure
}

/** Why loading the template list failed. */
internal sealed interface LoadFailure {
    data class Gradle(val failure: GradleFailure) : LoadFailure

    /** The task succeeded but wrote no JSON where the contract says (someone replaced the processor). */
    data class JsonMissing(val path: Path) : LoadFailure

    /** Empty or cut off (E-37). */
    data class MalformedJson(val path: Path, val message: String) : LoadFailure

    /** A required key is missing or has another type: plugin and katachi versions do not match (E-35). */
    data class IncompatibleJson(val path: Path, val location: String, val message: String) : LoadFailure

    data object Cancelled : LoadFailure
}
