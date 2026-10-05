package me.tbsten.katachi.intellij.ide.sarif

import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.StateFlow
import me.tbsten.katachi.intellij.data.gradle.GradleRunOutcome
import me.tbsten.katachi.intellij.model.ModuleId
import me.tbsten.katachi.intellij.model.ViolationModel
import me.tbsten.katachi.intellij.model.ViolationsState

/**
 * The violations of the last check, read from `katachi.sarif` (design 4.7). The real one is
 * `KatachiViolationsService`; the two features read violations only through this, and a fake stands in
 * for it in their tests. Disk and Gradle are behind it: `forFile` and `forModule` read the last state. The
 * implementation guards its SDK calls with `sdkCall`.
 *
 * ```kotlin
 * val here = violations.forFile(file)                  // locations[0] is in this file
 * if (violations.state.value is ViolationsState.NotRun) offerRunCheck(module)
 * ```
 */
internal interface ViolationsService {
    val state: StateFlow<ViolationsState>

    /** The violations whose `locations[0]` is [file]. A violation that fell back to the definition's file shows in that file. */
    fun forFile(file: VirtualFile): List<ViolationModel>

    /** Every violation of [module]'s SARIF. */
    fun forModule(module: ModuleId): List<ViolationModel>

    /** Reads the SARIF files again. Does not run Gradle. */
    fun refresh()

    /**
     * Runs `:<module>:test` through the plugin's Gradle runner. Needs `report { sarif = true }` to leave a SARIF.
     * When [module]'s check is already running, returns that run instead of starting another.
     */
    fun runCheck(module: ModuleId): Deferred<GradleRunOutcome>

    /** The modules whose check [runCheck] is running now. The features show "checking..." from this instead of keeping their own. */
    val runningChecks: StateFlow<Set<ModuleId>>
}
