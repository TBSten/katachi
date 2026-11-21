package me.tbsten.katachi.check.internal

import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.LayoutEntryKind
import me.tbsten.katachi.dsl.internal.DeclaredByGradle
import me.tbsten.katachi.dsl.internal.Glob

// Takes the entries resolved against the project's modules: before that, gradle()'s `build` of
// every module is one `**/build`, which would hide every source package that happens to be named `build`.
internal fun ignoredLayoutsOf(entries: List<LayoutEntry>): List<IgnoredLayout> {
    val ignores = entries.filter { it.kind == LayoutEntryKind.Ignore }.map { it to levelsOf(it.path) }
    if (ignores.isEmpty()) return emptyList()
    return entries
        // A module's injected `build.gradle.kts` is gradle()'s, wherever it lands.
        .filter { (it.kind == LayoutEntryKind.File || it.kind == LayoutEntryKind.AnyFile) && !it.synthetic }
        .mapNotNull { line ->
            val files = levelsOf(line.path) + if (line.kind == LayoutEntryKind.AnyFile) listOf(ANY_NAME) else emptyList()
            val byGradle = line.role[DeclaredByGradle] == true
            val hiding = ignores.filter { (ignore, levels) ->
                // gradle()'s own lines under the build directories gradle() ignores are not the definition's to fix.
                !(byGradle && ignore.synthetic) && levels.hidesEvery(files)
            }
            if (hiding.isEmpty()) return@mapNotNull partlyIgnoredLayout(line, files, ignores, byGradle)
            IgnoredLayout(
                path = line.path,
                role = line.role,
                declaredAt = line.declaredAt,
                hiddenBy = hiding.map { (ignore, _) ->
                    HidingIgnore(
                        path = ignore.path,
                        role = ignore.role,
                        declaredAt = ignore.declaredAt,
                        gradleModule = if (ignore.synthetic) ignore.modulePath ?: ROOT_MODULE else null,
                    )
                },
            )
        }
}

// A wildcard ignore() that can land on a directory under the fixed part of the line (`**/build` under
// `src/**/*.kt`) hides some of its files, and nothing says so. A literal ignore() is the user's own choice
// of place, and gradle()'s are per module, so neither is reported here.
private fun partlyIgnoredLayout(
    line: LayoutEntry,
    files: List<Level>,
    ignores: List<Pair<LayoutEntry, List<Level>>>,
    byGradle: Boolean,
): IgnoredLayout? {
    if (byGradle) return null
    val partly = ignores.filter { (ignore, levels) ->
        !ignore.synthetic && levels.any { it == Level.AnyLevels || (it as Level.One).glob.hasWildcard } &&
            levels.mayLandUnder(files)
    }
    if (partly.isEmpty()) return null
    return IgnoredLayout(
        path = line.path,
        role = line.role,
        declaredAt = line.declaredAt,
        hiddenBy = partly.map { (ignore, _) ->
            HidingIgnore(path = ignore.path, role = ignore.role, declaredAt = ignore.declaredAt, gradleModule = null)
        },
        partly = true,
    )
}

// Whether this ignore can match a directory under a fixed level of the line, above the file's name, where the file's own wildcards
// (`**`, a bare `*`) can stand for whatever name the ignore needs. Two partial wildcards on the
// same level are not compared: that would be a guess.
private fun List<Level>.mayLandUnder(files: List<Level>): Boolean {
    val ignore = this
    val seen = HashSet<Pair<Int, Int>>()
    // Only a directory under a fixed level of the line counts: `**/build` against `**/Foo.kt` is the
    // user leaving every build directory out, which is what they wrote.
    fun fixedAbove(j: Int): Boolean = (0 until j).any { (files[it] as? Level.One)?.literal != null }
    fun walk(i: Int, j: Int): Boolean {
        if (!seen.add(i to j)) return false
        if (i == ignore.size) return j < files.size && fixedAbove(j)
        if (j >= files.size) return false
        val level = ignore[i]
        val file = files[j]
        return when {
            level == Level.AnyLevels && (walk(i + 1, j) || walk(i, j + 1)) -> true
            file == Level.AnyLevels -> walk(i, j + 1) || (level is Level.One && walk(i + 1, j))
            level is Level.One && file is Level.One -> level.couldMatch(file) && walk(i + 1, j + 1)
            else -> false
        }
    }
    return walk(0, 0)
}

private fun Level.One.couldMatch(file: Level.One): Boolean = when {
    text == file.text || file.text == "*" || text == "*" -> true
    file.literal != null -> glob.matches(file.literal)
    literal != null -> file.glob.matches(literal)
    else -> false
}

private const val ROOT_MODULE: String = ":"

private sealed interface Level {
    // `**`: any number of levels, none included.
    object AnyLevels : Level

    class One(val text: String) : Level {
        val glob: Glob = Glob.compile(text)

        // The one name this level matches, or null when it holds a wildcard.
        val literal: String? = if (glob.hasWildcard) null else glob.literalPrefix.singleOrNull()
    }
}

// Every file name directly inside an `anyFile()` directory.
private val ANY_NAME: Level.One = Level.One("*")

// A layout path never escapes its separator, so splitting it leaves every level a pattern of its own.
private fun levelsOf(path: String): List<Level> =
    path.split('/').map { if (it == "**") Level.AnyLevels else Level.One(it) }

// Says yes only when it is certain: a warning is never raised on a guess. [Level.AnyLevels] here
// is one level of a `**`, which can be any name.
private fun Level.alwaysMatches(expected: Level.One): Boolean = when {
    expected.text == "*" -> true
    this !is Level.One -> false
    text == expected.text -> true
    else -> literal?.let { expected.glob.matches(it) } == true
}

private data class Progress(val position: Int, val ignorePositions: Set<Int>, val consumed: Boolean)

// Whether every path [files] matches has a directory above it that this ignore matches. The walk
// never enters such a directory, the project root aside, so none of those files is looked at.
// The ignore is followed as a set of positions (a `**` keeps several open) and [files] one shape
// of path at a time; one shape that gets through is enough to answer no.
private fun List<Level>.hidesEvery(files: List<Level>): Boolean {
    val seen = HashSet<Progress>()
    val pending = ArrayDeque<Progress>()
    fun enqueue(progress: Progress) {
        var current = progress
        while (true) {
            if (seen.add(current)) pending += current
            // A `**` of the files may stand for no level at all.
            if (current.position < files.size && files[current.position] == Level.AnyLevels) {
                current = current.copy(position = current.position + 1)
            } else {
                break
            }
        }
    }
    enqueue(Progress(position = 0, ignorePositions = closure(setOf(0)), consumed = false))
    while (pending.isNotEmpty()) {
        val progress = pending.removeFirst()
        // The path ends with no directory above it matched: the file itself is never ignored.
        if (progress.position == files.size) return false
        // A directory matched, and the path goes on below it.
        if (progress.consumed && size in progress.ignorePositions) continue
        val level = files[progress.position]
        val next = closure(step(progress.ignorePositions, level))
        // Nothing below can match any more, and every shape can still reach its end.
        if (next.isEmpty()) return false
        val position = if (level == Level.AnyLevels) progress.position else progress.position + 1
        enqueue(Progress(position = position, ignorePositions = next, consumed = true))
    }
    return true
}

private fun List<Level>.closure(positions: Set<Int>): Set<Int> {
    val reached = HashSet<Int>()
    for (start in positions) {
        var position = start
        reached += position
        while (position < size && this[position] == Level.AnyLevels) reached += ++position
    }
    return reached
}

private fun List<Level>.step(positions: Set<Int>, level: Level): Set<Int> {
    val next = HashSet<Int>()
    for (position in positions) {
        if (position == size) continue
        when (val expected = this[position]) {
            Level.AnyLevels -> next += position
            is Level.One -> if (level.alwaysMatches(expected)) next += position + 1
        }
    }
    return next
}
