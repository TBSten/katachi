package me.tbsten.katachi.intellij.uitest.pbt.definition

import me.tbsten.katachi.intellij.model.ConstraintModel
import me.tbsten.katachi.intellij.model.DefinitionModel
import me.tbsten.katachi.intellij.model.LayoutEntryKind
import me.tbsten.katachi.intellij.model.LayoutEntryModel
import me.tbsten.katachi.intellij.model.MatchOutcome
import me.tbsten.katachi.intellij.model.RoleModel

/**
 * What katachi's check says of one path, worked out the way its `Scan` walks: down from the root, one directory at a
 * time, and at each directory first "is it `.git`" (not walked; the path's own last element too, for the file a worktree
 * has), then "is it `ignore()`d", then "did any role declare it" (if not, the walk stops there as an unexpected directory).
 * Only a file whose every directory passed is asked which roles hold it. Every entry is tried in turn with
 * [referenceMatches]: no index, no buckets. The matcher under test has to agree with it.
 */
internal class ReferenceAnswer(
    /** Where the walk stops above the file: [MatchOutcome.NotWalked], [MatchOutcome.Ignored] or [MatchOutcome.UnexpectedUnder]; `null` when it reaches the file. */
    val stop: MatchOutcome?,
    /** `(role, entry)` per role: the roles that named the file first, then the roles that left its directory open. Empty when the walk stopped. */
    val claims: List<Pair<RoleModel, LayoutEntryModel>>,
    /** `(role name, directory)` of the places offered for a file nobody holds. */
    val nearby: List<Pair<String, String>>,
    val constraints: List<Pair<RoleModel, ConstraintModel>>,
)

internal fun referenceAnswer(model: DefinitionModel, path: String): ReferenceAnswer {
    val slots = model.roles.flatMap { role -> role.layout.map { role to it } }
    val levels = path.split('/')
    val ancestors = (1 until levels.size).map { levels.take(it).joinToString("/") }
    val directory = ancestors.lastOrNull().orEmpty()

    val known = { dir: String -> slots.any { (_, e) -> directoryPrefixes(e).any { referenceMatches(it, dir) } } }
    val ignoredAt = { dir: String ->
        slots.firstOrNull { (_, entry) -> entry.kind == LayoutEntryKind.Ignore && referenceMatches(entry.pattern, dir) }?.second
    }
    val stop: MatchOutcome? = ancestors.firstNotNullOfOrNull { dir ->
        when {
            dir.substringAfterLast('/') == ".git" -> MatchOutcome.NotWalked(dir)
            else -> ignoredAt(dir)?.let { MatchOutcome.Ignored(it) } ?: if (known(dir)) null else MatchOutcome.UnexpectedUnder(dir)
        }
    } ?: if (levels.last() == ".git") MatchOutcome.NotWalked(path) else null

    val claims = LinkedHashMap<RoleModel, LayoutEntryModel>()
    if (stop == null) {
        val named = slots.filter { (_, e) -> e.kind == LayoutEntryKind.File && referenceMatches(e.pattern, path) }
        val open = if (directory.isEmpty()) emptyList() else slots.filter { (_, e) -> e.kind == LayoutEntryKind.AnyFile && referenceMatches(e.pattern, directory) }
        for ((role, entry) in named + open) claims.putIfAbsent(role, entry)
    }

    val constraints = claims.keys.flatMap { role ->
        role.constraints.filter { c ->
            c.coverage.files.any { referenceMatches(it, path) } ||
                (directory.isNotEmpty() && c.coverage.anyFileDirectories.any { referenceMatches(it, directory) })
        }.map { role to it }
    }

    return ReferenceAnswer(stop, claims.map { it.key to it.value }, referenceNearby(slots, path), constraints)
}

/** Every level of the entry's pattern that is a directory: all of them, minus the last one of a `File`. */
private fun directoryPrefixes(entry: LayoutEntryModel): List<String> {
    val levels = entry.pattern.split('/')
    val count = if (entry.kind == LayoutEntryKind.File) levels.size - 1 else levels.size
    return (1..count).map { levels.take(it).joinToString("/") }
}

// --- nearby: katachi's NearbyCandidates (design violations-output 2.2 / 2.3), written the plain way ---

/** One declared place: the role, the directory pattern and the file name pattern (`null` for `anyFile()`), in declaration order. */
private data class ReferencePlace(val role: String, val directory: String, val filePattern: String?)

/**
 * The places offered for [path], three at most. Every place of a `File` or `anyFile()` entry that is not synthetic, not declared by `gradle()` (a role of the `Gradle` group), not at
 * the root and has at most one `**` is filled in with the file's directory; the ones that could take the file's extension
 * and start where the file starts are sorted by `(level edits, name mismatch, character edits, declaration order)`, every
 * distance computed outright (every alignment is enumerated), with one place per role and filled directory.
 */
private fun referenceNearby(slots: List<Pair<RoleModel, LayoutEntryModel>>, path: String): List<Pair<String, String>> {
    val directory = path.substringBeforeLast('/', "")
    if (directory.isEmpty()) return emptyList()
    val fileLevels = directory.split('/')
    val name = path.substringAfterLast('/')

    val places = LinkedHashSet<ReferencePlace>()
    for ((role, entry) in slots) {
        if (entry.synthetic || role.groupPath.firstOrNull() == "Gradle") continue
        val place = when (entry.kind) {
            LayoutEntryKind.File -> ReferencePlace(role.qualifiedName, entry.pattern.substringBeforeLast('/', ""), entry.pattern.substringAfterLast('/'))
            LayoutEntryKind.AnyFile -> ReferencePlace(role.qualifiedName, entry.pattern, null)
            else -> continue
        }
        if (place.directory.isEmpty() || place.directory.split('/').count { it == "**" } > 1) continue
        places += place
    }

    data class Ranked(val place: ReferencePlace, val filled: List<String>, val levels: Int, val nameMismatch: Int, val characters: Int, val order: Int)

    val ranked = places.withIndex().mapNotNull { (order, place) ->
        if (!referenceAcceptsExtension(place.filePattern, name)) return@mapNotNull null
        val filled = referenceInstantiate(place.directory.split('/'), fileLevels) ?: return@mapNotNull null
        if (filled.first() != fileLevels.first()) return@mapNotNull null
        val (levels, characters) = referenceDistance(fileLevels, filled)
        val nameMismatch = if (place.filePattern == null || referenceMatches(place.filePattern, name)) 0 else 1
        Ranked(place, filled, levels, nameMismatch, characters, order)
    }
    val sorted = ranked.sortedWith(compareBy({ it.levels }, { it.nameMismatch }, { it.characters }, { it.order }))
    val chosen = LinkedHashMap<Pair<String, List<String>>, Ranked>()
    for (candidate in sorted) chosen.putIfAbsent(candidate.place.role to candidate.filled, candidate)
    return chosen.values.take(3).map { it.place.role to it.filled.joinToString("/") }
}

private fun referenceAcceptsExtension(filePattern: String?, fileName: String): Boolean {
    if (filePattern == null) return true
    val extension = fileName.substringAfterLast('.', "")
    if ('.' !in filePattern) return '*' in filePattern || extension.isEmpty()
    val patternExtension = filePattern.substringAfterLast('.')
    return '*' in patternExtension || patternExtension == extension
}

/** The pattern's levels with the file's directory filled in: `null` when a wildcard level has nothing to take. */
private fun referenceInstantiate(pattern: List<String>, directory: List<String>): List<String>? {
    val filled = mutableListOf<String>()
    var next = 0
    for ((index, level) in pattern.withIndex()) {
        when {
            level == "**" -> {
                val start = minOf(next, directory.size)
                val after = pattern.size - index - 1
                val taken = maxOf(directory.size - start - after, 0)
                filled += directory.subList(start, start + taken)
                next += taken
            }

            hasUnescapedStar(level) -> {
                val taken = directory.getOrNull(next) ?: return null
                filled += if (referenceMatches(level, taken)) taken else fillStars(level, taken)
                next++
            }

            else -> {
                filled += unescape(level)
                next++
            }
        }
    }
    return filled
}

private fun hasUnescapedStar(level: String): Boolean {
    var index = 0
    while (index < level.length) {
        if (level[index] == '\\') {
            index += 2
            continue
        }
        if (level[index] == '*') return true
        index++
    }
    return false
}

private fun fillStars(level: String, value: String): String = buildString {
    var index = 0
    while (index < level.length) {
        when {
            level[index] == '\\' && index + 1 < level.length -> {
                append(level[index + 1])
                index += 2
            }
            level[index] == '*' -> {
                append(value)
                index++
            }
            else -> {
                append(level[index])
                index++
            }
        }
    }
}

private fun unescape(level: String): String = buildString {
    var index = 0
    while (index < level.length) {
        if (level[index] == '\\' && index + 1 < level.length) {
            append(level[index + 1])
            index += 2
        } else {
            append(level[index])
            index++
        }
    }
}

/**
 * `(level edits, characters touched)` of the best alignment of [left] and [right]: every alignment is enumerated, the
 * fewest level edits win and among those the fewest characters (a replaced level counts its Levenshtein distance, an
 * inserted or deleted one its whole length).
 */
private fun referenceDistance(left: List<String>, right: List<String>): Pair<Int, Int> {
    fun go(i: Int, j: Int): Pair<Int, Int> {
        if (i == left.size) return (right.size - j) to right.subList(j, right.size).sumOf { it.length }
        if (j == right.size) return (left.size - i) to left.subList(i, left.size).sumOf { it.length }
        val same = left[i] == right[j]
        val replaced = go(i + 1, j + 1).let { (l, c) -> if (same) l to c else (l + 1) to (c + referenceLevenshtein(left[i], right[j])) }
        val deleted = go(i + 1, j).let { (l, c) -> (l + 1) to (c + left[i].length) }
        val inserted = go(i, j + 1).let { (l, c) -> (l + 1) to (c + right[j].length) }
        return listOf(replaced, deleted, inserted).minWith(compareBy({ it.first }, { it.second }))
    }
    return go(0, 0)
}

private fun referenceLevenshtein(left: String, right: String): Int {
    fun go(i: Int, j: Int): Int = when {
        i == left.length -> right.length - j
        j == right.length -> left.length - i
        left[i] == right[j] -> go(i + 1, j + 1)
        else -> 1 + minOf(go(i + 1, j + 1), go(i + 1, j), go(i, j + 1))
    }
    return go(0, 0)
}
