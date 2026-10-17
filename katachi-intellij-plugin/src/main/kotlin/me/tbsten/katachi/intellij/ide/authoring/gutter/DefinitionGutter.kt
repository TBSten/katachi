package me.tbsten.katachi.intellij.ide.authoring.gutter

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import me.tbsten.katachi.intellij.ide.definition.DefinitionModelService
import me.tbsten.katachi.intellij.ide.gutter.GutterPorts
import me.tbsten.katachi.intellij.ide.gutter.HighlightingRefresher
import me.tbsten.katachi.intellij.ide.sdkCall
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.Freshness
import me.tbsten.katachi.intellij.model.ModuleId
import me.tbsten.katachi.intellij.model.answer
import me.tbsten.katachi.intellij.presentation.authoring.AnchorBinding
import me.tbsten.katachi.intellij.presentation.authoring.BoundAnchor
import me.tbsten.katachi.intellij.presentation.authoring.DefinitionGutterMark
import me.tbsten.katachi.intellij.presentation.authoring.DefinitionGutterMarks
import me.tbsten.katachi.intellij.presentation.gutter.GutterFreshness
import org.jetbrains.kotlin.psi.KtFile
import java.nio.file.Path

/** A ◆ of a definition file with the leaf it is keyed on. */
internal data class PlacedDefinitionGutterMark(val leaf: PsiElement, val mark: DefinitionGutterMark)

/** What [DefinitionGutter.bind] found in one definition file: [bound] is [anchors] tied to the model, in the same order. */
internal class BoundDefinitionFile(
    private val service: DefinitionModelService,
    /** The definition file, absolute. */
    val definitionFile: Path,
    /** The counts may be older than the source (design 4.4). */
    val stale: Boolean,
    val anchors: List<PsiAnchor>,
    val bound: List<BoundAnchor>,
) {
    /** The directory the files of [module]'s definition are relative to, or `null` when that definition is not known. */
    fun projectRootOf(module: ModuleId): Path? =
        service.definitionOf(module)?.let { it.definition?.projectRoot ?: it.module.linkedRootPath }
}

/**
 * The ◆ of one definition file (ide-authoring design 4.1-4.4): its anchors, bound to what the definition declares there, put
 * through [DefinitionGutterMarks]. The line marker provider draws them and the debug bridge describes them.
 *
 * Nothing is drawn for a file that is not in a definition module's test sources, nor while the definition was written by a
 * katachi older than the IDE reads. Answers in dumb mode: the PSI is walked without an index. Call under a read lock, from the
 * background: it asks the definition model.
 *
 * ```kotlin
 * val marks = DefinitionGutter.of(ktFile, GutterPorts.of(project))
 * ```
 */
internal object DefinitionGutter {
    fun of(file: KtFile, ports: GutterPorts): List<PlacedDefinitionGutterMark> {
        val bound = bind(file, ports) ?: return emptyList()
        return bound.anchors.zip(bound.bound).mapNotNull { (psi, binding) ->
            val module = binding.declarations.firstOrNull()?.module ?: return@mapNotNull null
            val projectRoot = bound.projectRootOf(module) ?: return@mapNotNull null
            val mark = DefinitionGutterMarks.of(binding, bound.definitionFile, projectRoot, bound.stale) ?: return@mapNotNull null
            val leaf = sdkCall("find the leaf of a definition anchor") { psi.leaf.element }.getOrNull() ?: return@mapNotNull null
            PlacedDefinitionGutterMark(leaf, mark)
        }
    }

    /**
     * The anchors of [file] tied to what the definition declares there, or `null` when the definition side draws nothing on the file
     * (not a definition file, the definition too old, nothing declared). The ◆ and the Code Vision of one file start from this.
     */
    fun bind(file: KtFile, ports: GutterPorts): BoundDefinitionFile? {
        if (InjectedLanguageManager.getInstance(file.project).isInjectedFragment(file)) return null
        if (!ports.hasDefinitionModule()) return null
        val service = ports.definitions() ?: return null
        val virtualFile = file.viewProvider.virtualFile
        if (service.definitionModuleOf(virtualFile) == null) return null
        HighlightingRefresher.getInstance(file.project).follow(service, ports.violations())
        val state = service.state.value
        if (GutterFreshness.shouldEnsureLoaded(state, ports.settings().autoLoadTemplates)) {
            sdkCall("ask the definition to load for the definition gutter") { service.ensureLoaded() }
        }
        if (GutterFreshness.isTooOld(state)) return null
        val declarations = service.declarationsIn(virtualFile)
        if (declarations.isEmpty()) return null
        val anchors = DefinitionAnchors.of(file)
        val bound = AnchorBinding.bind(anchors.map { it.anchor }, declarations)
        val stale = isStale(service, state, ports.fileState(virtualFile).unsaved)
        val definitionFile = pathOf(virtualFile) ?: return null
        return BoundDefinitionFile(service, definitionFile, stale, anchors, bound)
    }

    /**
     * Whether the counts may be older than the source: the foundation's rule ([GutterFreshness.isStale], which also counts an
     * edit not saved yet: the file is the one being written), or katachi found a mistake in the definition since (then the answer
     * is the one from before the mistake, design 4.4). The file's write time is not compared: saving it is what makes the
     * service read the definition again, and that arrives as [Freshness.Stale].
     */
    private fun isStale(service: DefinitionModelService, state: DefinitionState, unsaved: Boolean): Boolean =
        GutterFreshness.isStale(state, state.answer?.freshness ?: Freshness.Fresh, null, null, unsaved) ||
            service.definitionErrors.value.errors.isNotEmpty()

    private fun pathOf(file: VirtualFile): Path? = sdkCall("read the path of ${file.path}") { file.fileSystem.getNioPath(file) ?: Path.of(file.path) }.getOrNull()
}
