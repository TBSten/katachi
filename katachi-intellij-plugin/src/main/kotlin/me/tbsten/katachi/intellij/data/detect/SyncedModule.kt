package me.tbsten.katachi.intellij.data.detect

import java.nio.file.Path

internal data class SyncedModule(
    val gradlePath: String,
    val directory: Path,
    val taskNames: Set<String>,
    val katachiVersion: String?,
)

internal data class SyncedRoot(
    val rootPath: Path,
    val rootName: String,
    val modules: List<SyncedModule>,
)

internal sealed interface SyncedProject {
    data object NotGradle : SyncedProject

    data object NotSynced : SyncedProject

    data class Synced(val roots: List<SyncedRoot>) : SyncedProject
}

internal fun interface SyncedProjectSource {
    fun read(): SyncedProject
}
