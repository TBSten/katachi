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

private const val GIT_DIRECTORY_NAME: String = ".git"

internal data class SkippedGlob(val role: RoleModel, val pattern: String, val failure: KatachiGlobSyntaxException)

// Must mirror katachi's `LayoutIndex` (same glob, first entry per role, known directories, nearby places).
internal class DefinitionMatcher(private val definition: DefinitionModel) {
    private val skipped = mutableListOf<SkippedGlob>()

    val skippedGlobs: List<SkippedGlob> get() = skipped.toList()

    private class Slot(val roleIndex: Int, val role: RoleModel, val entry: LayoutEntryModel, val glob: KatachiGlob, val order: Int)

    private val slots: List<Slot> = buildList {
        definition.roles.forEachIndexed { roleIndex, role ->
            for (entry in role.layout) {
                val glob = compileOrSkip(role, entry.pattern) ?: continue
                add(Slot(roleIndex, role, entry, glob, order = size))
            }
        }
    }

    private val knownDirectories: PrefixGlobIndex<KatachiGlob> = PrefixGlobIndex(
        slots.flatMapTo(linkedSetOf<String>()) { directoryPatterns(it.entry) }
            .mapNotNull { pattern -> compile(pattern) },
    ) { it }

    private val allowedFiles = indexOf(LayoutEntryKind.File)
    private val openDirectories = indexOf(LayoutEntryKind.AnyFile)
    private val ignoredDirectories = indexOf(LayoutEntryKind.Ignore)

    private val places: NearbyPlaces = NearbyPlaces(slots.map { it.role to it.entry })

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

    fun isIgnored(directory: String): Boolean = ignoredDirectories.anyMatches(directory)

    fun isKnown(directory: String): Boolean = knownDirectories.anyMatches(directory)

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

    private class Claims(val held: List<Slot>, val mismatched: List<Pair<Slot, CaptureMismatchDetail>>)

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

internal fun String.parentPath(): String = substringBeforeLast('/', missingDelimiterValue = "")

private fun ancestorsOf(path: String): List<String> {
    val segments = path.split('/')
    return (1 until segments.size).map { segments.take(it).joinToString("/") }
}

private fun directoryPatterns(entry: LayoutEntryModel): List<String> {
    val segments = entry.pattern.split('/')
    val levels = if (entry.kind == LayoutEntryKind.File) segments.size - 1 else segments.size
    return (1..levels).map { segments.take(it).joinToString("/") }
}
