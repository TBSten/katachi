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

/**
 * What [KatachiGenerateDialog] reads from the project: the templates it offers, the captures a
 * place decides, the existing-file check and the changes it should follow. Plain values and
 * functions, so a test builds the dialog without a `KatachiProjectService`.
 *
 * ```kotlin
 * val environment = GenerateDialogEnvironment(request, candidates, seeds, generation::checkTarget, rootOf)
 * ```
 */
internal class GenerateDialogEnvironment(
    val request: GenerateDialogRequest,
    /** Every template of every definition, in list order, as of now. */
    val candidates: List<ModuleTemplate>,
    val seeds: CaptureSeedPort,
    /** `SingleFileGeneration.checkTarget`: the check the ViewModel and [Generate] share. */
    val checkTarget: suspend (Path?) -> TargetState,
    /** The directory a definition's patterns are relative to ([rootOnceOf] for the real one: found once, when the dialog opens). */
    val rootOf: (KatachiModule) -> Path,
    /** The shared list after every change (a reload); the dialog follows it while open. */
    val templateChanges: Flow<List<ModuleTemplate>> = emptyFlow(),
    /** Emits when files change outside the dialog, so the target is checked again. */
    val filesChanged: Flow<Unit> = emptyFlow(),
    /** Reads the directories behind the capture fields' candidates (design 3.3); none by default. */
    val directoryLister: DirectoryLister = DirectoryLister { emptyList() },
)

/**
 * [resolve] asked once for each definition of [modules] now, and never again for those: the answer of [rootOf] is a lookup.
 * A definition that was not among [modules] (the list changed while the dialog is open) is asked on first use, then remembered.
 * The root is found by looking at files on disk, so asking at each keystroke or file event on the EDT is what this avoids
 * (design D-4); a wrapper or `.git` that appears while the dialog is open is not noticed until the dialog is opened again.
 *
 * ```kotlin
 * val rootOf = rootOnceOf(candidates.map { it.module }) { placementRootOf(it, NioProjectFileSystem) }
 * ```
 */
internal fun rootOnceOf(modules: List<KatachiModule>, resolve: (KatachiModule) -> Path): (KatachiModule) -> Path {
    val roots = HashMap<ModuleId, Path>()
    for (module in modules) roots.getOrPut(module.id) { resolve(module) }
    return { module -> roots.getOrPut(module.id) { resolve(module) } }
}
