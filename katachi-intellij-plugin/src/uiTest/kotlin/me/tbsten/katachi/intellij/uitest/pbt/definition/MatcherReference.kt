package me.tbsten.katachi.intellij.uitest.pbt.definition

import me.tbsten.katachi.intellij.model.ConstraintModel
import me.tbsten.katachi.intellij.model.DefinitionModel
import me.tbsten.katachi.intellij.model.LayoutEntryKind
import me.tbsten.katachi.intellij.model.LayoutEntryModel
import me.tbsten.katachi.intellij.model.MatchOutcome
import me.tbsten.katachi.intellij.model.RoleModel

/**
 * What katachi's check says of one path, worked out the way its `Scan` walks: down from the root, one directory at a
 * time, and at each directory first "is it `.git` / `.gradle` / `.idea`" (not walked), then "is it `ignore()`d", then
 * "did any role declare it" (if not, the walk stops there as an unexpected directory). Only a file whose every
 * directory passed is asked which roles hold it. Every entry is tried in turn with [referenceMatches]: no index, no
 * buckets. The matcher under test has to agree with it.
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
            dir.substringAfterLast('/') in setOf(".git", ".gradle", ".idea") -> MatchOutcome.NotWalked(dir)
            else -> ignoredAt(dir)?.let { MatchOutcome.Ignored(it) } ?: if (known(dir)) null else MatchOutcome.UnexpectedUnder(dir)
        }
    }

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

    val places = LinkedHashMap<Pair<String, String>, Pair<String, String>>()
    for ((role, entry) in slots) {
        val place = when (entry.kind) {
            LayoutEntryKind.File -> entry.pattern.substringBeforeLast('/', "")
            LayoutEntryKind.AnyFile -> entry.pattern
            else -> continue
        }
        if (place.isEmpty() || '*' in place) continue
        places.putIfAbsent(role.qualifiedName to place, role.qualifiedName to place)
    }
    val fileLevels = directory.split('/').filter { it.isNotEmpty() }
    fun shared(place: String): Int = fileLevels.zip(place.split('/')).takeWhile { (l, r) -> l == r }.size
    val nearby = places.values.map { it to shared(it.second) }.filter { it.second > 0 }.sortedByDescending { it.second }.take(3).map { it.first }

    return ReferenceAnswer(stop, claims.map { it.key to it.value }, nearby, constraints)
}

/** Every level of the entry's pattern that is a directory: all of them, minus the last one of a `File`. */
private fun directoryPrefixes(entry: LayoutEntryModel): List<String> {
    val levels = entry.pattern.split('/')
    val count = if (entry.kind == LayoutEntryKind.File) levels.size - 1 else levels.size
    return (1..count).map { levels.take(it).joinToString("/") }
}
