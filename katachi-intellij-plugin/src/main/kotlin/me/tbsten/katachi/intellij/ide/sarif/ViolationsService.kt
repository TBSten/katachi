package me.tbsten.katachi.intellij.ide.sarif

import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.StateFlow
import me.tbsten.katachi.intellij.data.gradle.GradleRunOutcome
import me.tbsten.katachi.intellij.model.ModuleId
import me.tbsten.katachi.intellij.model.ViolationModel
import me.tbsten.katachi.intellij.model.ViolationsState
import java.nio.file.Path

internal interface ViolationsService {
    val state: StateFlow<ViolationsState>

    fun forFile(file: VirtualFile): List<ViolationModel>

    fun forModule(module: ModuleId): List<ViolationModel>

    /** The results whose `locations[0]` is [path] (absolute). */
    fun forPath(path: Path): List<ViolationModel>

    fun refresh()

    // Needs `report { sarif = true }` to leave a SARIF.
    fun runCheck(module: ModuleId): Deferred<GradleRunOutcome>

    val runningChecks: StateFlow<Set<ModuleId>>
}
