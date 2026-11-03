package me.tbsten.katachi.intellij.ide.authoring.codevision

import com.intellij.openapi.util.TextRange
import me.tbsten.katachi.intellij.ide.authoring.BundleAuthoringStrings
import me.tbsten.katachi.intellij.ide.authoring.gutter.DefinitionGutter
import me.tbsten.katachi.intellij.ide.gutter.GutterPorts
import me.tbsten.katachi.intellij.ide.sdkCall
import me.tbsten.katachi.intellij.presentation.authoring.AnchorKind
import me.tbsten.katachi.intellij.presentation.authoring.CountText
import me.tbsten.katachi.intellij.presentation.authoring.EntryFilesInput
import me.tbsten.katachi.intellij.presentation.gutter.RoleFileCount
import org.jetbrains.kotlin.psi.KtFile

internal data class EntryFilesLens(val range: TextRange, val text: String, val input: EntryFilesInput)

internal object EntryFilesLenses {
    fun of(file: KtFile, ports: GutterPorts): List<EntryFilesLens> {
        val bound = DefinitionGutter.bind(file, ports) ?: return emptyList()
        return bound.anchors.zip(bound.bound).mapNotNull { (psi, binding) ->
            if (binding.anchor.kind != AnchorKind.Entry) return@mapNotNull null
            val entries = binding.declarations.mapNotNull { it.entry }
            val module = binding.declarations.firstOrNull()?.module ?: return@mapNotNull null
            if (entries.isEmpty()) return@mapNotNull null
            val projectRoot = bound.projectRootOf(module) ?: return@mapNotNull null
            val leaf = sdkCall("find the leaf of a layout line") { psi.leaf.element }.getOrNull() ?: return@mapNotNull null
            // A wildcard module key (`":feature:*"`) turns one declaration into an entry per module: the line says how many.
            val modules = if (entries.size > 1) entries.map { it.modulePath ?: it.pattern }.distinct().size else 1
            val input = EntryFilesInput(
                patterns = entries.map { it.pattern },
                files = RoleFileCount.holders(entries).flatMap { it.matchedFiles }.distinct(),
                count = RoleFileCount.of(entries),
                projectRoot = projectRoot,
            )
            EntryFilesLens(leaf.textRange, CountText.of(entries, bound.stale, BundleAuthoringStrings, modules), input)
        }
    }
}
