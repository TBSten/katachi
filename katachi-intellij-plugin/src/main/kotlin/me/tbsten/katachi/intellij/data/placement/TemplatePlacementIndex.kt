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
 * Templates without captures are in, as fixed paths. A template below a module capture that katachi
 * describes (`modulePlacements`) is matched against its path in each existing module instead of its
 * declared pattern: a file or directory in no existing module does not fit it, and one in a module
 * decides the module capture -- wherever katachi's `ModuleResolver` put that module.
 *
 * A template set is indexed by its first member's first file, for directories only ([matchesForFile] leaves it out).
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
    /**
     * One indexed template: where its patterns start, its declared [pattern], and -- below a module
     * capture katachi described (`modulePlacements`) -- one pattern per existing module ([choices]),
     * which then decide instead of [pattern]. `null` [choices] for every other template.
     * [inMemberNames]: a template set's file the set's names could not write, so the names it has are the member's
     * and decide nothing for the set (they go to [PlacementMatch.undecided]).
     */
    private class Entry(val template: ModuleTemplate, val root: Path, val pattern: PathPattern?, val choices: List<Choice>?, val inMemberNames: Boolean = false)

    /** One existing module a module capture can pick: its values, and the file's path there as a pattern. */
    private class Choice(val values: Map<String, String>, val pattern: PathPattern)

    /**
     * The templates [file] (absolute) is the target of. A template set is never one: it is not "the file that fits"
     * but several files at once, so the editor notification and the gutter do not offer it (template-set design 8.4).
     */
    fun matchesForFile(file: Path): List<PlacementMatch> = entries.mapNotNull { entry ->
        if (entry.template.template.set != null) return@mapNotNull null
        val hit = entry.fileHit(file) ?: return@mapNotNull null
        entry.toMatch(hit.match, hit.moduleValues)
    }

    /** How [Entry] matches [file] (absolute): the match, the module capture values of the module it is in, and the pattern that matched. */
    private class FileHit(val match: SegmentMatch, val moduleValues: Map<String, String>, val pattern: PathPattern)

    private fun Entry.fileHit(file: Path): FileHit? {
        val relative = relativeSegments(root, file) ?: return null
        val choices = this.choices ?: return pattern?.let { p -> p.matchFile(relative)?.let { FileHit(it, emptyMap(), p) } }
        // Only a file in a module that exists, at the place katachi writes it.
        return choices.firstNotNullOfOrNull { choice ->
            choice.pattern.matchFile(relative)?.let { FileHit(it, choice.values, choice.pattern) }
        }
    }

    /** The templates that can create a file under [directory] (absolute), with the remaining path. */
    fun matchesForDirectory(directory: Path): List<PlacementMatch> = entries.mapNotNull { entry ->
        val relative = relativeSegments(entry.root, directory) ?: return@mapNotNull null
        val choices = entry.choices ?: return@mapNotNull entry.pattern?.matchDirectory(relative)?.let { entry.toMatch(it) }
        val hits = choices.mapNotNull { choice -> choice.pattern.matchDirectory(relative)?.let { choice to it } }
        when {
            hits.isEmpty() -> null
            // In (or on the way into) one module: that module is decided.
            hits.size == 1 -> hits.single().let { (choice, match) -> entry.toMatch(match, choice.values) }
            // Above the modules (`feature/`): which one is still open, as the declared pattern says.
            else -> entry.pattern?.matchDirectory(relative)?.let { entry.toMatch(it) }
        }
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
            is EntryOrigin.SiblingOf -> return siblingSeeds(origin.path, template)
        }
        return matches.firstOrNull { it.id == template }?.decided.orEmpty()
    }

    /**
     * [template]'s captures a sibling file at [file] decides, without the ones that name the file: those in the pattern's
     * last segment and in no segment above it (`${name}` of `${name}Screen.kt`). A capture the last segment shares with a
     * directory above (`feature`) is the sibling's, and carries over.
     */
    private fun siblingSeeds(file: Path, template: TemplateId): Map<String, String> {
        val hit = entries.firstOrNull { it.template.id == template }?.fileHit(file) ?: return emptyMap()
        val segments = hit.pattern.segments
        fun captures(segment: PatternSegment) = segment.parts.filterIsInstance<PatternPart.Capture>().map { it.name }
        val above = segments.dropLast(1).flatMap(::captures).toSet()
        val naming = captures(segments.last()).filter { it !in above }.toSet()
        return (hit.moduleValues + hit.match.decided).filterKeys { it !in naming }
    }

    private fun Entry.toMatch(match: SegmentMatch, moduleValues: Map<String, String> = emptyMap()): PlacementMatch = PlacementMatch(
        template = template,
        decided = if (inMemberNames) emptyMap() else moduleValues + match.decided,
        undecided = if (inMemberNames) (moduleValues.keys + match.decided.keys + match.undecided).distinct() else match.undecided.filter { it !in moduleValues },
        remainingPath = match.remaining.joinToString("/") { segment -> segment.parts.joinToString("") { it.shown() } },
        targetUndecided = match.remaining.any { segment -> segment.parts.any { it is PatternPart.Derived } },
    )

    private fun PatternPart.shown(): String = when (this) {
        is PatternPart.Literal -> text
        is PatternPart.Capture -> "<$name>"
        is PatternPart.Derived -> if (transform == null) "<$name>" else "<$name.$transform>"
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
            // A template set fits where its first member's first file fits (template-set design 8.3); its files
            // are already written in the set's parameter names, so `decided` is keyed by them.
            val set = template.template.set
            val file = (if (set != null) set.members.firstOrNull()?.files?.firstOrNull() else template.template.detail?.files?.firstOrNull())
                ?: return null
            val pattern = PathPattern.parse(file.pattern)
            val choices = file.modulePlacement?.let { placement ->
                placement.modules.mapNotNull { module ->
                    PathPattern.parse(module.path)?.let { Choice(placement.captureNames.zip(module.values).toMap(), it) }
                }
            }
            if (pattern == null && choices.isNullOrEmpty()) return null
            val inMemberNames = set != null && file.captures.isEmpty() && pattern?.segments.orEmpty().any { segment -> segment.parts.any { it is PatternPart.Capture } }
            return Entry(template, root, pattern, choices, inMemberNames)
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
