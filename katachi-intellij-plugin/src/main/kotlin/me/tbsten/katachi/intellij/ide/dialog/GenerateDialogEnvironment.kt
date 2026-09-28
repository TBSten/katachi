package me.tbsten.katachi.intellij.ide.dialog

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import me.tbsten.katachi.intellij.data.generate.TargetState
import me.tbsten.katachi.intellij.model.KatachiModule
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
    /** The directory a definition's patterns are relative to. */
    val rootOf: (KatachiModule) -> Path,
    /** The shared list after every change (a reload); the dialog follows it while open. */
    val templateChanges: Flow<List<ModuleTemplate>> = emptyFlow(),
    /** Emits when files change outside the dialog, so the target is checked again. */
    val filesChanged: Flow<Unit> = emptyFlow(),
)
