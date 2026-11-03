package me.tbsten.katachi.intellij.ide.authoring.gutter

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import me.tbsten.katachi.intellij.ide.authoring.BundleAuthoringStrings
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

internal data class PlacedDefinitionGutterMark(val leaf: PsiElement, val mark: DefinitionGutterMark)

internal class BoundDefinitionFile(
    private val service: DefinitionModelService,
    val definitionFile: Path,
    val stale: Boolean,
    val anchors: List<PsiAnchor>,
    val bound: List<BoundAnchor>,
) {
    fun projectRootOf(module: ModuleId): Path? =
        service.definitionOf(module)?.let { it.definition?.projectRoot ?: it.module.linkedRootPath }
}

internal object DefinitionGutter {
    fun of(file: KtFile, ports: GutterPorts): List<PlacedDefinitionGutterMark> {
        val bound = bind(file, ports) ?: return emptyList()
        return bound.anchors.zip(bound.bound).mapNotNull { (psi, binding) ->
            val module = binding.declarations.firstOrNull()?.module ?: return@mapNotNull null
            val projectRoot = bound.projectRootOf(module) ?: return@mapNotNull null
            val mark = DefinitionGutterMarks.of(binding, bound.definitionFile, projectRoot, bound.stale, BundleAuthoringStrings) ?: return@mapNotNull null
            val leaf = sdkCall("find the leaf of a definition anchor") { psi.leaf.element }.getOrNull() ?: return@mapNotNull null
            PlacedDefinitionGutterMark(leaf, mark)
        }
    }

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

    private fun isStale(service: DefinitionModelService, state: DefinitionState, unsaved: Boolean): Boolean =
        GutterFreshness.isStale(state, state.answer?.freshness ?: Freshness.Fresh, null, null, unsaved) ||
            service.definitionErrors.value.errors.isNotEmpty()

    private fun pathOf(file: VirtualFile): Path? = sdkCall("read the path of ${file.path}") { file.fileSystem.getNioPath(file) ?: Path.of(file.path) }.getOrNull()
}
