package me.tbsten.katachi.intellij.data.placement

import me.tbsten.katachi.intellij.data.ProjectFileSystem
import me.tbsten.katachi.intellij.model.DescriptionSnapshot
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.presentation.entry.EntryOrigin
import me.tbsten.katachi.intellij.presentation.entry.PlacementMatch
import java.nio.file.Path

/**
 * Which templates fit a file or a directory, for every loaded architecture definition (spike S1).
 *
 * Built off the EDT from the loaded snapshots (issue 4) and immutable, so the notification's
 * background thread and the New menu's `update()` read it without locks. Per definition, paths are
 * taken relative to that definition's project root ([placementRootOf]) and matched with [PathPattern].
 *
 * Left out: templates whose preview failed (`conflict: true`, no details) or that have a parameter
 * kind this plugin does not know, patterns with an unnamed `*` / `**`, templates without files.
 * Templates without captures are in, as fixed paths.
 *
 * Matches come in index order: definitions in the order given, then JSON order within each.
 *
 * ```kotlin
 * val index = TemplatePlacementIndex.build(snapshots) { placementRootOf(it, NioProjectFileSystem) }
 * index.matchesForFile(file).firstOrNull()?.decided // every capture of the file
 * ```
 */
internal class TemplatePlacementIndex private constructor(
    /** The definitions indexed, in order. */
    val definitions: List<KatachiModule>,
) {
    /** The templates [file] (absolute) is the target of. */
    fun matchesForFile(file: Path): List<PlacementMatch> {
        // TODO(A1)
        return emptyList()
    }

    /** The templates that can create a file under [directory] (absolute), with the remaining path. */
    fun matchesForDirectory(directory: Path): List<PlacementMatch> {
        // TODO(A1)
        return emptyList()
    }

    /**
     * [template]'s captures [origin] decides: the dialog's initial values, recomputed when the user
     * picks another template (decision 4). Empty when [template] does not fit [origin]. Backs the
     * real `CaptureSeedPort`.
     */
    fun seedsFor(origin: EntryOrigin, template: TemplateId): Map<String, String> {
        // TODO(A1)
        return emptyMap()
    }

    companion object {
        val EMPTY: TemplatePlacementIndex = TemplatePlacementIndex(emptyList())

        /** The index of [snapshots] (definition order kept); [rootOf] gives each definition's pattern root. */
        fun build(snapshots: List<DescriptionSnapshot>, rootOf: (KatachiModule) -> Path): TemplatePlacementIndex {
            // TODO(A1)
            return EMPTY
        }
    }
}

/**
 * The directory [module]'s patterns are relative to: katachi's project root, the first directory up
 * from [KatachiModule.directory] with a Gradle wrapper, a Maven wrapper or `.git` (katachi's
 * `findProjectRoot`), else [KatachiModule.linkedRootPath] (spike S1 §1).
 */
internal fun placementRootOf(module: KatachiModule, fileSystem: ProjectFileSystem): Path {
    // TODO(A1): walk up with the markers.
    return module.linkedRootPath
}
