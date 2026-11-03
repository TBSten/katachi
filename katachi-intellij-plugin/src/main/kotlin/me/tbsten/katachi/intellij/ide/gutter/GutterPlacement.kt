package me.tbsten.katachi.intellij.ide.gutter

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import me.tbsten.katachi.intellij.ide.definition.DefinitionModelService
import me.tbsten.katachi.intellij.ide.sdkCall
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.FileRoles
import me.tbsten.katachi.intellij.presentation.gutter.GutterFreshness
import me.tbsten.katachi.intellij.presentation.gutter.GutterInput
import me.tbsten.katachi.intellij.presentation.gutter.GutterMark
import me.tbsten.katachi.intellij.presentation.gutter.GutterMarks
import me.tbsten.katachi.intellij.presentation.gutter.MarkTarget
import me.tbsten.katachi.intellij.presentation.gutter.SourceFileKind
import org.jetbrains.kotlin.psi.KtFile

internal data class PlacedMark(val leaf: PsiElement, val mark: GutterMark)

internal data class PlacedFile(val service: DefinitionModelService, val roles: FileRoles, val marks: List<PlacedMark>)

// Call under a read lock, from the background: it asks the definition model, which may read the disk.
internal object GutterPlacement {
    fun of(file: PsiFile, ports: GutterPorts): List<PlacedMark> = ofFile(file, ports)?.marks.orEmpty()

    fun ofFile(file: PsiFile, ports: GutterPorts): PlacedFile? {
        if (!ports.hasDefinitionModule()) return null
        val service = ports.definitions() ?: return null
        HighlightingRefresher.getInstance(file.project).follow(service, ports.violations())
        val virtualFile = file.viewProvider.virtualFile
        val state = service.state.value
        // Nothing was read yet: ask the service to (it does not wait and loads once however often it is asked). The state change draws the ◆ then.
        if (GutterFreshness.shouldEnsureLoaded(state, ports.settings().autoLoadTemplates)) {
            sdkCall("ask the definition to load for the gutter") { service.ensureLoaded() }
        }
        // The definition was written by a katachi older than the IDE reads (design 2.6): nothing it kept is shown, though `previous` still holds an answer.
        if (GutterFreshness.isTooOld(state)) return null
        val roles = service.forFile(virtualFile) ?: return null
        val kind = kindOf(file)
        val sources = if (kind == SourceFileKind.Kotlin && file is KtFile) SourceDeclarations.of(file) else emptyList()
        val explanations = if (kind == SourceFileKind.Kotlin) service.fileDeclarationsOf(virtualFile) else emptyList()
        val fileState = ports.fileState(virtualFile)
        val generatedAt = service.definitionOf(roles.module.id)?.definition?.generatedAt
        val stale = GutterFreshness.isStale(state, roles.freshness, fileState.modifiedAt, generatedAt, fileState.unsaved)
        val marks = GutterMarks.of(GutterInput(roles, explanations, sources.map { it.declaration }, kind, stale), BundleGutterStrings)
        val placed = marks.mapNotNull { mark -> leafOf(file, mark.target, sources)?.let { PlacedMark(it, mark) } }
        return placed.takeIf { it.isNotEmpty() }?.let { PlacedFile(service, roles, it) }
    }

    fun kindOf(file: PsiFile): SourceFileKind = when {
        file !is KtFile -> SourceFileKind.Other
        file.isScript() -> SourceFileKind.KotlinScript
        else -> SourceFileKind.Kotlin
    }

    private fun leafOf(file: PsiFile, target: MarkTarget, sources: List<SourceDeclarationPsi>): PsiElement? = when (target) {
        // By identity: GutterMarks hands back the instances it was given, and two overloads on one line are equal values.
        is MarkTarget.Declaration -> sources.firstOrNull { it.declaration === target.declaration }
            ?.let { source -> sdkCall("find the name of ${source.declaration.name}") { source.leaf.element }.getOrNull() }
        MarkTarget.FileHead -> headOf(file)
    }

    private fun headOf(file: PsiFile): PsiElement? {
        val packageDirective = (file as? KtFile)?.takeUnless { it.isScript() }?.packageDirective?.takeIf { it.textLength > 0 }
        return packageDirective?.let(SourceDeclarations::firstLeafOf) ?: SourceDeclarations.firstLeafOf(file)
    }
}
