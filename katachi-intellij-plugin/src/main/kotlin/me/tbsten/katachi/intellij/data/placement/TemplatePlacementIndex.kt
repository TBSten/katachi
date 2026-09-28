package me.tbsten.katachi.intellij.data.placement

import me.tbsten.katachi.intellij.data.ProjectFileSystem
import me.tbsten.katachi.intellij.model.DescriptionSnapshot
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.ModuleTemplate
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
    private val entries: List<Entry>,
) {
    /** One indexed template: where its pattern starts and the pattern. */
    private class Entry(val template: ModuleTemplate, val root: Path, val pattern: PathPattern)

    /** The templates [file] (absolute) is the target of. */
    fun matchesForFile(file: Path): List<PlacementMatch> = entries.mapNotNull { entry ->
        val relative = relativeSegments(entry.root, file) ?: return@mapNotNull null
        entry.pattern.matchFile(relative)?.let { entry.toMatch(it) }
    }

    /** The templates that can create a file under [directory] (absolute), with the remaining path. */
    fun matchesForDirectory(directory: Path): List<PlacementMatch> = entries.mapNotNull { entry ->
        val relative = relativeSegments(entry.root, directory) ?: return@mapNotNull null
        entry.pattern.matchDirectory(relative)?.let { entry.toMatch(it) }
    }

    /**
     * [template]'s captures [origin] decides: the dialog's initial values, recomputed when the user
     * picks another template (decision 4). Empty when [template] does not fit [origin]. Backs the
     * real `CaptureSeedPort`.
     */
    fun seedsFor(origin: EntryOrigin, template: TemplateId): Map<String, String> {
        val matches = when (origin) {
            is EntryOrigin.EditorFile -> matchesForFile(origin.path)
            is EntryOrigin.NewMenuDirectory -> matchesForDirectory(origin.path)
        }
        return matches.firstOrNull { it.id == template }?.decided.orEmpty()
    }

    private fun Entry.toMatch(match: SegmentMatch): PlacementMatch = PlacementMatch(
        template = template,
        decided = match.decided,
        undecided = match.undecided,
        remainingPath = match.remaining.joinToString("/") { segment -> segment.parts.joinToString("") { it.shown() } },
        targetUndecided = match.remaining.any { segment -> segment.parts.any { it is PatternPart.Derived } },
    )

    private fun PatternPart.shown(): String = when (this) {
        is PatternPart.Literal -> text
        is PatternPart.Capture -> "<$name>"
        is PatternPart.Derived -> "<$name>"
    }

    companion object {
        val EMPTY: TemplatePlacementIndex = TemplatePlacementIndex(emptyList(), emptyList())

        /** The index of [snapshots] (definition order kept); [rootOf] gives each definition's pattern root. */
        fun build(snapshots: List<DescriptionSnapshot>, rootOf: (KatachiModule) -> Path): TemplatePlacementIndex {
            val entries = snapshots.flatMap { snapshot ->
                val root = rootOf(snapshot.module)
                snapshot.templates.mapNotNull { model -> entryOf(ModuleTemplate(snapshot.module, model), root) }
            }
            return TemplatePlacementIndex(snapshots.map { it.module }, entries)
        }

        private fun entryOf(template: ModuleTemplate, root: Path): Entry? {
            if (!template.template.isAvailable) return null
            val file = template.template.detail?.files?.firstOrNull() ?: return null
            val pattern = PathPattern.parse(file.pattern) ?: return null
            return Entry(template, root, pattern)
        }

        /** [path]'s segments below [root], or `null` when it is [root] itself or outside it. */
        private fun relativeSegments(root: Path, path: Path): List<String>? {
            val normalizedRoot = root.normalize()
            val normalized = path.normalize()
            if (!normalized.startsWith(normalizedRoot)) return null
            val relative = normalizedRoot.relativize(normalized)
            return relative.map { it.toString() }.filter { it.isNotEmpty() }
        }
    }
}

private val ROOT_MARKERS: List<String> = listOf(
    "gradlew",
    "gradlew.bat",
    "gradle/wrapper/gradle-wrapper.jar",
    "gradle/wrapper/gradle-wrapper.properties",
    "mvnw",
    "mvnw.cmd",
    ".mvn/wrapper/maven-wrapper.jar",
    ".mvn/wrapper/maven-wrapper.properties",
    ".git",
    ".git/config",
    ".git/HEAD",
    ".git/refs",
)

/**
 * The directory [module]'s patterns are relative to: katachi's project root, the first directory up
 * from [KatachiModule.directory] with a Gradle wrapper, a Maven wrapper or `.git` (katachi's
 * `findProjectRoot`), else [KatachiModule.linkedRootPath] (spike S1 section 1).
 */
internal fun placementRootOf(module: KatachiModule, fileSystem: ProjectFileSystem): Path {
    var current: Path? = module.directory
    while (current != null) {
        val directory = current
        if (ROOT_MARKERS.any { fileSystem.exists(directory.resolve(it)) }) return directory
        current = directory.parent
    }
    return module.linkedRootPath
}
