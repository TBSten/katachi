package me.tbsten.katachi.intellij.data.detect

import java.nio.file.Path

/**
 * One Gradle module of the synced project data, cut loose from the IDE's `DataNode`s so that
 * detection runs without a platform.
 */
internal data class SyncedModule(
    val gradlePath: String,
    val directory: Path,
    /** Task names without the module path (`katachiInternalTemplatesJson`), group or not. */
    val taskNames: Set<String>,
    /** The version of `me.tbsten.katachi:katachi` among its dependencies; `null` when it has none. */
    val katachiVersion: String?,
)

/** One Gradle build linked to the IDE. */
internal data class SyncedRoot(
    val rootPath: Path,
    val rootName: String,
    val modules: List<SyncedModule>,
)

/** What the IDE knows about the project's Gradle builds. */
internal sealed interface SyncedProject {
    /** No Gradle build is linked. */
    data object NotGradle : SyncedProject

    /** A build is linked but there is no synced data yet. */
    data object NotSynced : SyncedProject

    data class Synced(val roots: List<SyncedRoot>) : SyncedProject
}

/** Reads the synced data. Implemented over `ProjectDataManager` in the IDE, faked in tests. */
internal fun interface SyncedProjectSource {
    fun read(): SyncedProject
}
