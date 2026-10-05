package me.tbsten.katachi.intellij.data.definition

import me.tbsten.katachi.intellij.model.ConstraintMatch
import me.tbsten.katachi.intellij.model.ConstraintModel
import me.tbsten.katachi.intellij.model.DefinitionModel
import me.tbsten.katachi.intellij.model.EntryMatch
import me.tbsten.katachi.intellij.model.FileMatch
import me.tbsten.katachi.intellij.model.LayoutEntryKind
import me.tbsten.katachi.intellij.model.LayoutEntryModel
import me.tbsten.katachi.intellij.model.MatchOutcome
import me.tbsten.katachi.intellij.model.NearbyRole
import me.tbsten.katachi.intellij.model.RoleModel

/** How many places [DefinitionMatcher] offers for a file nobody holds, as katachi's `[UnexpectedFile]` block does. */
private const val NEARBY_LIMIT: Int = 3

/**
 * Directories that are not the project's files at all, whatever the definition says: katachi's walk never descends into a
 * directory of this name at any depth (`FOREIGN_DIRECTORY_NAMES` of its `Scan`).
 */
internal val FOREIGN_DIRECTORY_NAMES: Set<String> = setOf(".git", ".gradle", ".idea")

/** An entry or constraint glob the matcher could not read, and so left out. */
internal data class SkippedGlob(val role: RoleModel, val pattern: String, val failure: KatachiGlobSyntaxException)

/**
 * Answers "which role, entry and constraint holds this file" from a [DefinitionModel] alone, by the
 * rules of katachi's `LayoutIndex` (design 4.4): the same glob, the same first-entry-per-role, the
 * same known directories, the same nearby places. A pure match of paths: no read lock, no index, no
 * disk.
 *
 * Build one per model and ask for any number of files; the index is built once.
 *
 * ```kotlin
 * val matcher = DefinitionMatcher(model)
 * val match = matcher.matchFile("core/domain/src/main/kotlin/GetUserUseCase.kt")
 * match.matches.map { it.role.qualifiedName }
 * ```
 */
internal class DefinitionMatcher(private val definition: DefinitionModel) {
    private val skipped = mutableListOf<SkippedGlob>()

    /** The entries and constraints left out because their glob could not be read. Normally empty. */
    val skippedGlobs: List<SkippedGlob> get() = skipped.toList()

    /**
     * One entry of the flattened layout: the role it belongs to, in the order the definition lists them. [roleIndex] is the
     * role's place in the definition, which is what the per-role tables below are keyed by: a [RoleModel] is a data class
     * and hashing one reads all its layout and constraints, on every file asked.
     */
    private class Slot(val roleIndex: Int, val role: RoleModel, val entry: LayoutEntryModel, val glob: KatachiGlob)

    private val slots: List<Slot> = definition.roles.flatMapIndexed { roleIndex, role ->
        role.layout.mapNotNull { entry -> compileOrSkip(role, entry.pattern)?.let { Slot(roleIndex, role, entry, it) } }
    }

    /** Directories the walk may descend into: every declared directory and every directory on the way to one. */
    private val knownDirectories: PrefixGlobIndex<KatachiGlob> = PrefixGlobIndex(
        slots.flatMapTo(linkedSetOf<String>()) { directoryPatterns(it.entry) }
            .mapNotNull { pattern -> compile(pattern) },
    ) { it }

    private val allowedFiles = indexOf(LayoutEntryKind.File)
    private val openDirectories = indexOf(LayoutEntryKind.AnyFile)
    private val ignoredDirectories = indexOf(LayoutEntryKind.Ignore)

    /** The places a file could move to, with a pattern in none of them, in declaration order. */
    private val locations: List<NearbyRole> = nearbyLocations()

    /** Constraint globs, compiled once: by the role's index in the definition, in the role's order. */
    private val coverages: List<List<CompiledCoverage>> = definition.roles.map { role ->
        role.constraints.map { constraint ->
            CompiledCoverage(
                constraint,
                constraint.coverage.files.mapNotNull { compileOrSkip(role, it) },
                constraint.coverage.anyFileDirectories.mapNotNull { compileOrSkip(role, it) },
            )
        }
    }

    private class CompiledCoverage(val constraint: ConstraintModel, val files: List<KatachiGlob>, val anyFileDirectories: List<KatachiGlob>) {
        fun covers(file: String, directory: String): Boolean =
            files.any { it.matches(file) } || (directory.isNotEmpty() && anyFileDirectories.any { it.matches(directory) })
    }

    /** Whether a role said to stop at [directory] (`ignore()`). */
    fun isIgnored(directory: String): Boolean = ignoredDirectories.anyMatches(directory)

    /** Whether any role declared [directory] itself, or something below it. */
    fun isKnown(directory: String): Boolean = knownDirectories.anyMatches(directory)

    /**
     * What [relativePath] (relative to the project root, `/` separated) is to the definition.
     *
     * 1. Going down from the root, the first directory above it that is `.git` / `.gradle` / `.idea` ends the question
     *    with [MatchOutcome.NotWalked], and the first that is `ignore()`d with [MatchOutcome.Ignored]: katachi does not
     *    go in, so nothing else is looked at. A directory above that one which no role declared ends it first, as in 3.
     *    (All decided level by level, as katachi's walk does.)
     * 2. A `File` glob matches it, or its directory matches an `anyFile()` glob: [MatchOutcome.Matched],
     *    one entry per role (the first), with the constraints of those roles that cover it.
     * 3. Nobody holds it: [MatchOutcome.UnexpectedUnder] the first directory above it that no role
     *    declared, or [MatchOutcome.Unexpected] when all of them are known; with [FileMatch.nearby].
     */
    fun matchFile(relativePath: String): FileMatch {
        val directories = ancestorsOf(relativePath)
        for ((level, directory) in directories.withIndex()) {
            val stop: MatchOutcome? = if (directory.substringAfterLast('/') in FOREIGN_DIRECTORY_NAMES) {
                MatchOutcome.NotWalked(directory)
            } else {
                ignoredDirectories.matching(directory).firstOrNull()?.let { MatchOutcome.Ignored(it.entry) }
            }
            if (stop == null) continue
            // The walk goes down one directory at a time and stops at the first it does not go into. A directory above this one
            // that no role declared ends it earlier (an ignored directory sits under declared ones as a rule; a foreign one can be anywhere).
            val undeclared = directories.take(level).firstOrNull { !isKnown(it) }
            return if (undeclared != null) {
                FileMatch(emptyList(), emptyList(), MatchOutcome.UnexpectedUnder(undeclared), nearbyOf(relativePath))
            } else {
                FileMatch(emptyList(), emptyList(), stop, emptyList())
            }
        }

        val claims = claimsOn(relativePath)
        if (claims.isNotEmpty()) {
            val directory = relativePath.parentPath()
            val constraints = claims.flatMap { slot ->
                coverages[slot.roleIndex]
                    .filter { it.covers(relativePath, directory) }
                    .map { ConstraintMatch(slot.role, it.constraint) }
            }
            return FileMatch(claims.map { EntryMatch(it.role, it.entry) }, constraints, MatchOutcome.Matched, emptyList())
        }

        val unknown = directories.firstOrNull { !isKnown(it) }
        val outcome = if (unknown != null) MatchOutcome.UnexpectedUnder(unknown) else MatchOutcome.Unexpected
        return FileMatch(emptyList(), emptyList(), outcome, nearbyOf(relativePath))
    }

    /** One entry per role: the roles that named the file first, then the roles that only left its directory open. Declaration order within each. */
    private fun claimsOn(file: String): List<Slot> {
        val matched = LinkedHashMap<Int, Slot>()
        for (slot in allowedFiles.matching(file)) matched.putIfAbsent(slot.roleIndex, slot)
        val directory = file.parentPath()
        // `anyFile()` covers the files directly inside a directory and nothing deeper; the project root is not a directory a role can declare.
        if (directory.isNotEmpty()) {
            for (slot in openDirectories.matching(directory)) matched.putIfAbsent(slot.roleIndex, slot)
        }
        return matched.values.toList()
    }

    /**
     * Places [file] could be moved to, the ones sharing the most leading path with its directory
     * first, at most three. A place sharing nothing is left out; places that tie keep declaration
     * order (the sort is stable).
     */
    fun nearbyOf(file: String): List<NearbyRole> {
        val directory = file.parentPath()
        return locations
            .map { it to sharedLeadingSegments(directory, it.directory) }
            .filter { (_, shared) -> shared > 0 }
            .sortedByDescending { (_, shared) -> shared }
            .take(NEARBY_LIMIT)
            .map { (location, _) -> location }
    }

    /**
     * The directories a role accepts a file into: where it declared a `File` (its parent) and where
     * it wrote `anyFile()`. A directory that only exists to hold another is not one of them, nor is
     * one the role `ignore()`s, the project root, or a pattern.
     */
    private fun nearbyLocations(): List<NearbyRole> {
        val seen = LinkedHashMap<Pair<Int, String>, NearbyRole>()
        for (slot in slots) {
            val directory = when (slot.entry.kind) {
                LayoutEntryKind.File -> slot.entry.pattern.parentPath()
                LayoutEntryKind.AnyFile -> slot.entry.pattern
                LayoutEntryKind.Directory, LayoutEntryKind.Ignore, is LayoutEntryKind.Unknown -> continue
            }
            if (directory.isEmpty() || '*' in directory) continue
            seen.getOrPut(slot.roleIndex to directory) { NearbyRole(slot.role, directory, slot.entry) }
        }
        return seen.values.toList()
    }

    private fun indexOf(kind: LayoutEntryKind): PrefixGlobIndex<Slot> =
        PrefixGlobIndex(slots.filter { it.entry.kind == kind }) { it.glob }

    private fun compile(pattern: String): KatachiGlob? = try {
        KatachiGlob.compile(pattern)
    } catch (failure: KatachiGlobSyntaxException) {
        null
    }

    private fun compileOrSkip(role: RoleModel, pattern: String): KatachiGlob? = try {
        KatachiGlob.compile(pattern)
    } catch (failure: KatachiGlobSyntaxException) {
        skipped += SkippedGlob(role, pattern, failure)
        null
    }
}

/** `core/domain` for `core/domain/UseCase.kt`, and the empty string at the root. */
internal fun String.parentPath(): String = substringBeforeLast('/', missingDelimiterValue = "")

/** The directories above [path], outermost first: `a`, `a/b` for `a/b/c.kt`. */
private fun ancestorsOf(path: String): List<String> {
    val segments = path.split('/')
    return (1 until segments.size).map { segments.take(it).joinToString("/") }
}

/**
 * Every level of the entry's pattern that is a directory: all of them except the last one of a
 * `File` entry.
 */
private fun directoryPatterns(entry: LayoutEntryModel): List<String> {
    val segments = entry.pattern.split('/')
    val levels = if (entry.kind == LayoutEntryKind.File) segments.size - 1 else segments.size
    return (1..levels).map { segments.take(it).joinToString("/") }
}

private fun sharedLeadingSegments(left: String, right: String): Int {
    if (left.isEmpty() || right.isEmpty()) return 0
    val leftSegments = left.split('/')
    val rightSegments = right.split('/')
    var shared = 0
    while (shared < leftSegments.size && shared < rightSegments.size) {
        if (leftSegments[shared] != rightSegments[shared]) break
        shared++
    }
    return shared
}
