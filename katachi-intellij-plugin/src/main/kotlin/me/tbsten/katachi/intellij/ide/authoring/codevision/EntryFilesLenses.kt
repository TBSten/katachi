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

/** One Code Vision of a definition file: the [text] at the end of the line of [range], and what its popup is made from. */
internal data class EntryFilesLens(val range: TextRange, val text: String, val input: EntryFilesInput)

/**
 * The Code Vision of one definition file (ide-authoring design 4.3): for each file entry (`"*Service".ktFile()`, `"x".file()`) that the
 * definition model has a declaration for, how many files it holds. The same anchors and binding as the ◆ ([DefinitionGutter.bind]),
 * so a line and its count are the same everywhere.
 *
 * Call under a read lock, from the background. It asks the definition model, so it answers nothing in a file that is not in a
 * definition module's test sources.
 *
 * ```kotlin
 * val lenses = EntryFilesLenses.of(ktFile, GutterPorts.of(project))   // "*Service".ktFile()  ->  "3 files"
 * ```
 */
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
