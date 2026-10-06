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

/**
 * The one name katachi's walk skips whatever the definition says (`GIT_DIRECTORY_NAME` of its `Scan`): the repository
 * itself, a directory at the root or a file in a worktree / submodule. `.gradle` and `.idea` are directories like any other
 * (design foreign-directory-scan 1.1).
 */
private const val GIT_DIRECTORY_NAME: String = ".git"

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
     * and hashing one reads all its layout and constraints, on every file asked. [order] is the entry's place among all.
     */
    private class Slot(val roleIndex: Int, val role: RoleModel, val entry: LayoutEntryModel, val glob: KatachiGlob, val order: Int)

    private val slots: List<Slot> = buildList {
        definition.roles.forEachIndexed { roleIndex, role ->
            for (entry in role.layout) {
                val glob = compileOrSkip(role, entry.pattern) ?: continue
                add(Slot(roleIndex, role, entry, glob, order = size))
            }
        }
    }

    /** Directories the walk may descend into: every declared directory and every directory on the way to one. */
    private val knownDirectories: PrefixGlobIndex<KatachiGlob> = PrefixGlobIndex(
        slots.flatMapTo(linkedSetOf<String>()) { directoryPatterns(it.entry) }
            .mapNotNull { pattern -> compile(pattern) },
    ) { it }

    private val allowedFiles = indexOf(LayoutEntryKind.File)
    private val openDirectories = indexOf(LayoutEntryKind.AnyFile)
    private val ignoredDirectories = indexOf(LayoutEntryKind.Ignore)

    /** The places a file could move to, by katachi's rule (design violations-output 2.2 / 2.3). */
    private val places: NearbyPlaces = NearbyPlaces(slots.map { it.role to it.entry })

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
     * 1. Going down from the root, the first directory above it named `.git` ends the question with
     *    [MatchOutcome.NotWalked], and the first that is `ignore()`d with [MatchOutcome.Ignored]: katachi does not go in,
     *    so nothing else is looked at. The path's own last element named `.git` (the file a worktree or submodule has) is
     *    `NotWalked` too. A directory above that one which no role declared ends it first, as in 3. (All decided level by
     *    level, as katachi's walk does.)
     * 2. A `File` glob matches it, or its directory matches an `anyFile()` glob, and the entry's bound captures agree
     *    ([captureMismatchOf]): [MatchOutcome.Matched], one entry per role (the first), with the constraints of those
     *    roles that cover it.
     * 3. Nobody holds it: [MatchOutcome.UnexpectedUnder] the first directory above it that no role declared; else
     *    [MatchOutcome.CaptureMismatch] when a pattern matched but its captures held the file back (the first such entry);
     *    else [MatchOutcome.Unexpected], with [FileMatch.nearby].
     */
    fun matchFile(relativePath: String): FileMatch {
        val directories = ancestorsOf(relativePath)
        // The last element is only asked whether it is `.git`: a file cannot be an ignored directory.
        for ((level, element) in (directories + relativePath).withIndex()) {
            val stop: MatchOutcome? = if (element.substringAfterLast('/') == GIT_DIRECTORY_NAME) {
                MatchOutcome.NotWalked(element)
            } else if (level < directories.size) {
                ignoredDirectories.matching(element).firstOrNull()?.let { MatchOutcome.Ignored(it.entry) }
            } else {
                null
            }
            if (stop == null) continue
            // The walk goes down one directory at a time and stops at the first it does not go into. A directory above this one
            // that no role declared ends it earlier (an ignored directory sits under declared ones as a rule; `.git` can be anywhere).
            val undeclared = directories.take(level).firstOrNull { !isKnown(it) }
            return if (undeclared != null) {
                FileMatch(emptyList(), emptyList(), MatchOutcome.UnexpectedUnder(undeclared), nearbyOf(relativePath))
            } else {
                FileMatch(emptyList(), emptyList(), stop, emptyList())
            }
        }

        val claims = claimsOn(relativePath)
        if (claims.held.isNotEmpty()) {
            val directory = relativePath.parentPath()
            val constraints = claims.held.flatMap { slot ->
                coverages[slot.roleIndex]
                    .filter { it.covers(relativePath, directory) }
                    .map { ConstraintMatch(slot.role, it.constraint) }
            }
            return FileMatch(claims.held.map { EntryMatch(it.role, it.entry) }, constraints, MatchOutcome.Matched, emptyList())
        }

        val unknown = directories.firstOrNull { !isKnown(it) }
        // A pattern matched but its bound captures did not agree, and nobody else holds the file: katachi's `[CaptureMismatch]`,
        // with the one place the file belongs instead of nearby ones. The walk reports an undeclared directory first, as for any file.
        val mismatch = claims.mismatched.firstOrNull()
        if (unknown == null && mismatch != null) {
            val (slot, detail) = mismatch
            return FileMatch(emptyList(), emptyList(), MatchOutcome.CaptureMismatch(slot.role, slot.entry, detail.occurrences, detail.expectedPath), emptyList())
        }
        val outcome = if (unknown != null) MatchOutcome.UnexpectedUnder(unknown) else MatchOutcome.Unexpected
        return FileMatch(emptyList(), emptyList(), outcome, nearbyOf(relativePath))
    }

    /** What the entries say of one file: the ones that hold it, and the ones whose pattern matched but whose captures held it back. */
    private class Claims(val held: List<Slot>, val mismatched: List<Pair<Slot, CaptureMismatchDetail>>)

    /**
     * One entry per role: the roles that named the file first, then the roles that only left its directory open, declaration
     * order within each. An entry whose pattern matches but whose bound captures do not agree does not hold the file; it is
     * kept apart, in declaration order, for [MatchOutcome.CaptureMismatch].
     */
    private fun claimsOn(file: String): Claims {
        val matched = LinkedHashMap<Int, Slot>()
        val mismatched = mutableListOf<Pair<Slot, CaptureMismatchDetail>>()
        fun claim(slot: Slot) {
            val detail = captureMismatchOf(slot.entry, slot.glob, file)
            if (detail == null) matched.putIfAbsent(slot.roleIndex, slot) else mismatched += slot to detail
        }
        for (slot in allowedFiles.matching(file)) claim(slot)
        val directory = file.parentPath()
        // `anyFile()` covers the files directly inside a directory and nothing deeper; the project root is not a directory a role can declare.
        if (directory.isNotEmpty()) {
            for (slot in openDirectories.matching(directory)) claim(slot)
        }
        // The two lists are each in declaration order; the first declared entry is the one the report names.
        mismatched.sortBy { (slot, _) -> slot.order }
        return Claims(matched.values.toList(), mismatched)
    }

    /**
     * Places [file] could be moved to, nearest first, three at most, by katachi's `NearbyCandidates` rule ([NearbyPlaces]):
     * every declared place filled in with the file's own path, the ones that could hold a file of its name ordered by the
     * levels that differ, then the name, then the characters, then declaration order. A place sharing nothing is left out.
     */
    fun nearbyOf(file: String): List<NearbyRole> = places.nearbyOf(file)

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
