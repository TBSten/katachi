package me.tbsten.katachi.intellij.ide.dialog

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import me.tbsten.katachi.intellij.data.generate.TargetState
import me.tbsten.katachi.intellij.data.placement.DirectoryLister
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.ModuleId
import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.presentation.dialog.CaptureSeedPort
import me.tbsten.katachi.intellij.presentation.entry.GenerateDialogRequest
import java.nio.file.Path

internal class GenerateDialogEnvironment(
    val request: GenerateDialogRequest,
    val candidates: List<ModuleTemplate>,
    val seeds: CaptureSeedPort,
    val checkTarget: suspend (Path?) -> TargetState,
    val rootOf: (KatachiModule) -> Path,
    val templateChanges: Flow<List<ModuleTemplate>> = emptyFlow(),
    val filesChanged: Flow<Unit> = emptyFlow(),
    val directoryLister: DirectoryLister = DirectoryLister { emptyList() },
)

// Roots are found once: asking at each keystroke or file event on the EDT reads the disk; a wrapper or `.git` that appears while the dialog is open is not noticed.
internal fun rootOnceOf(modules: List<KatachiModule>, resolve: (KatachiModule) -> Path): (KatachiModule) -> Path {
    val roots = HashMap<ModuleId, Path>()
    for (module in modules) roots.getOrPut(module.id) { resolve(module) }
    return { module -> roots.getOrPut(module.id) { resolve(module) } }
}
