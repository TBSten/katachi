package me.tbsten.katachi.test.dsl

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import kotlin.random.Random
import me.tbsten.katachi.check.Severity
import me.tbsten.katachi.check.UnexpectedDirectory
import me.tbsten.katachi.check.UnexpectedFile
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.check.validate
import me.tbsten.katachi.test.check.layoutArchitecture
import me.tbsten.katachi.test.check.repositoryOf
import me.tbsten.katachi.test.pbt.checkTrials
import me.tbsten.katachi.test.pbt.pbtSeed
import me.tbsten.katachi.test.pbt.pbtTrials
import me.tbsten.katachi.test.pbt.pick
import me.tbsten.katachi.test.pbt.without

/*
 * Deny by default over random layouts and random trees, against a model a reader can hold in their
 * head: a layout is the list of paths it spells out (each `/` chain and nested block joined into one
 * line of level patterns), a file is allowed when one of those lines matches it level by level, and
 * a directory is known when one of those lines can pass through it.
 *
 * The model reads the layout's own pieces (RSeg / RNode), not the flattened entries, Glob or
 * LayoutIndex, so the check and the model come to their answers by two different roads.
 */

/** Values a whole-level `*` or a partial wildcard takes in a random tree. None holds a `.`. */
private val DIRECTORY_VALUES: List<String> = listOf("home", "settings", "x")

/** Directory names a random tree draws from besides the concretized ones, to miss on purpose. */
private val OTHER_DIRECTORIES: List<String> = LITERAL_DIRECTORIES + DIRECTORY_VALUES + listOf("feat", "featx", "a-impl", "-impl", "other")

/** File names a random tree draws from besides the concretized ones. Every one holds a `.`, so no file is ever a directory's name. */
private val OTHER_FILES: List<String> = listOf("Stray.txt", "Home.kt", "HomeViewModel.kt", "a.kt", "README.md", "Foo.kts", "Home.kts")

// region model

/** What a line of a layout says about the place its levels spell. */
private enum class LineKind {
    /** A directory block: the directory is known. */
    Directory,

    /** `"name".file()`: files matching [LayoutLine.name] are allowed there. */
    File,

    /** `anyFile()`: any file directly inside is allowed. */
    AnyFile,

    /** `ignore()`: nothing at or below it is checked. */
    Ignore,
}

/** One line a layout spells out: the level patterns of its directories, and a file name pattern for a [LineKind.File]. */
private data class LayoutLine(val dirs: List<RSeg>, val kind: LineKind, val name: String? = null)

private fun linesOf(nodes: List<RNode>, prefix: List<RSeg> = emptyList()): List<LayoutLine> = nodes.flatMap { node ->
    when (node) {
        is RNode.Dir -> listOf(LayoutLine(prefix + node.segs, LineKind.Directory)) + linesOf(node.children, prefix + node.segs)
        is RNode.File -> listOf(LayoutLine(prefix + node.dirs, LineKind.File, node.name))
        RNode.AnyFile -> listOf(LayoutLine(prefix, LineKind.AnyFile))
        RNode.Ignore -> listOf(LayoutLine(prefix, LineKind.Ignore))
    }
}

/** `*` as zero characters or more, everything else literally: the only syntax the generators use in one level. */
private fun levelMatches(pattern: String, level: String): Boolean {
    val pieces = pattern.split('*')
    if (pieces.size == 1) return pattern == level
    if (!level.startsWith(pieces.first()) || !level.endsWith(pieces.last())) return false
    if (level.length < pieces.first().length + pieces.last().length) return false
    var at = pieces.first().length
    val end = level.length - pieces.last().length
    for (piece in pieces.subList(1, pieces.size - 1)) {
        val found = level.indexOf(piece, at)
        if (found < 0 || found + piece.length > end) return false
        at = found + piece.length
    }
    return true
}

private fun segMatches(seg: RSeg, level: String): Boolean = when (seg) {
    is RSeg.Lit -> seg.name == level
    is RSeg.Star -> level.isNotEmpty()
    is RSeg.Partial -> levelMatches(seg.glob, level)
    is RSeg.Capture -> level.isNotEmpty()
    RSeg.DoubleStar -> error("handled by the caller")
}

/** How many of [segs] can be used up exactly by [levels] (from the start), each answer once. */
private fun consumed(segs: List<RSeg>, levels: List<String>): Set<Int> {
    // reachable[i] = the counts of segs that can have matched levels[0 until i].
    var reachable: Set<Int> = closeOverDoubleStars(segs, setOf(0))
    for (level in levels) {
        val next = mutableSetOf<Int>()
        for (s in reachable) {
            if (s >= segs.size) continue
            val seg = segs[s]
            if (seg == RSeg.DoubleStar) {
                next += s // one more level for the same `**`
            } else if (segMatches(seg, level)) {
                next += s + 1
            }
        }
        reachable = closeOverDoubleStars(segs, next)
        if (reachable.isEmpty()) return emptySet()
    }
    return reachable
}

/** [counts] plus every count reachable by letting a `**` match no level at all. */
private fun closeOverDoubleStars(segs: List<RSeg>, counts: Set<Int>): Set<Int> {
    val result = counts.toMutableSet()
    var changed = true
    while (changed) {
        changed = false
        for (s in result.toList()) {
            if (s < segs.size && segs[s] == RSeg.DoubleStar && result.add(s + 1)) changed = true
        }
    }
    return result
}

/** Whether [line]'s directory levels spell exactly [directory]. */
private fun spells(line: LayoutLine, directory: List<String>): Boolean = line.dirs.size in consumed(line.dirs, directory)

/** Whether a line of the model allows the file at [path]: one names it, or one opens the directory it is directly in. */
private fun allowed(lines: List<LayoutLine>, path: String): Boolean {
    val levels = path.split('/')
    val directories = levels.dropLast(1)
    return lines.any { line ->
        when (line.kind) {
            LineKind.File -> levelMatches(line.name!!, levels.last()) && spells(line, directories)
            LineKind.AnyFile -> directories.isNotEmpty() && spells(line, directories)
            else -> false
        }
    }
}

/** Whether a line of the model ignores the directory at [directory] (its levels). */
private fun ignored(lines: List<LayoutLine>, directory: List<String>): Boolean = lines.any { line ->
    line.kind == LineKind.Ignore && spells(line, directory)
}

/** Whether a line of the model can pass through the directory at [directory] (its levels). */
private fun known(lines: List<LayoutLine>, directory: List<String>): Boolean = lines.any { line ->
    consumed(line.dirs, directory).isNotEmpty()
}

/**
 * What the model says the check reports as unexpected. The walk goes down a file's directories from the
 * top and stops at the first one that is ignored (nothing to report) or that no line passes through (that
 * directory is reported); a file it reaches is reported unless a line allows it.
 */
private fun expectedUnexpected(lines: List<LayoutLine>, files: Set<String>): Set<String> = files.map { path ->
    val levels = path.split('/')
    val stop = (1 until levels.size).firstNotNullOfOrNull { depth ->
        val directory = levels.take(depth)
        when {
            ignored(lines, directory) -> ""
            !known(lines, directory) -> "[UnexpectedDirectory] " + directory.joinToString("/")
            else -> null
        }
    }
    when {
        stop != null -> stop.ifEmpty { null }
        allowed(lines, path) -> null
        else -> "[UnexpectedFile] $path"
    }
}.filterNotNullTo(sortedSetOf())

private fun List<Violation>.unexpected(): Set<String> =
    filter { it.severity == Severity.Error && (it is UnexpectedFile || it is UnexpectedDirectory) }
        .mapTo(sortedSetOf()) { "[${it.label}] ${it.path}" }

// endregion

// region generation

/** One trial: a layout and the files of a tree to check it against. */
private data class DenyTrial(val layout: List<RNode>, val files: Set<String>)

/** Paths the layout spells out, made concrete by choosing a value for every wildcard. */
private fun Random.concreteFiles(lines: List<LayoutLine>): List<String> = lines.filter { it.kind != LineKind.Directory }.flatMap { line ->
    List(nextInt(0, 3)) {
        val directories = line.dirs.flatMap { seg ->
            when (seg) {
                is RSeg.Lit -> listOf(seg.name)
                is RSeg.Star, is RSeg.Capture -> listOf(pick(DIRECTORY_VALUES))
                RSeg.DoubleStar -> List(nextInt(0, 3)) { pick(DIRECTORY_VALUES) }
                is RSeg.Partial -> listOf(seg.glob.replace("*", pick(listOf("", "x", "home"))))
            }
        }
        when (line.kind) {
            LineKind.File -> {
                val name = if (line.name == "*") pick(OTHER_FILES) else line.name!!.replace("*", pick(listOf("", "Home", "Foo")))
                (directories + name).joinToString("/")
            }

            LineKind.AnyFile -> (directories + pick(OTHER_FILES)).joinToString("/")
            // Anything at any depth below an ignored directory.
            else -> (directories + List(nextInt(0, 3)) { pick(OTHER_DIRECTORIES) } + pick(OTHER_FILES)).joinToString("/")
        }
    }
}

/** [path] changed in one place, so that it may just miss the line it came from. */
private fun Random.nearMiss(path: String): String {
    val levels = path.split('/').toMutableList()
    when (nextInt(4)) {
        0 -> levels[levels.lastIndex] = levels.last().substringBeforeLast('.') + pick(listOf(".txt", ".kts", ".md"))
        1 -> if (levels.size > 1) levels.removeAt(nextInt(levels.size - 1))
        2 -> levels.add(nextInt(levels.size), pick(OTHER_DIRECTORIES))
        else -> if (levels.size > 1) levels[nextInt(levels.size - 1)] = pick(OTHER_DIRECTORIES)
    }
    return levels.joinToString("/")
}

private fun Random.trial(): DenyTrial {
    val layout = randomLayout(openAndIgnored = true)
    val declared = concreteFiles(linesOf(layout))
    val files = linkedSetOf<String>()
    files += declared
    declared.forEach { if (nextInt(3) == 0) files += nearMiss(it) }
    repeat(nextInt(0, 4)) {
        files += (List(nextInt(0, 4)) { pick(OTHER_DIRECTORIES) } + pick(OTHER_FILES)).joinToString("/")
    }
    return DenyTrial(layout, files)
}

/** Every trial one step smaller: a declaration, a child, a level or a file fewer. */
private fun DenyTrial.smaller(): List<DenyTrial> =
    smallerLayouts(layout).map { copy(layout = it) }.toList() +
        files.toList().indices.map { copy(files = files.toList().without(it).toSet()) }

private fun DenyTrial.describe(): String = buildString {
    append("layout:\n").append(renderDsl(layout, named = false))
    append("files:\n").append(files.sorted().joinToString("\n") { "  $it" })
}

// endregion

/**
 * Whether the trial says something: the DSL accepts [layout] (some random layouts are refused, as a user's
 * would be), and no line spells two `**` in a row. `a` / `**` / `**` does not match `a` although `a` / `**` does, a
 * known TODO that GlobConformanceFixtureSpec pins and this model does not follow.
 */
private fun accepted(layout: List<RNode>): Boolean =
    linesOf(layout).none { line -> line.dirs.zipWithNext().any { (a, b) -> a == RSeg.DoubleStar && b == RSeg.DoubleStar } } &&
        runCatching { layoutOf { declare(layout, named = false) } }.isSuccess

private fun DenyTrial.check() {
    if (!accepted(layout)) return
    val tree = repositoryOf { files.forEach { it() } }
    val actual = layoutArchitecture { declare(layout, named = false) }.validate(tree).unexpected()
    val expected = expectedUnexpected(linesOf(layout), files)
    if (actual != expected) {
        throw AssertionError(
            "the check and the model disagree\n" +
                "  only the check: ${actual - expected}\n" +
                "  only the model: ${expected - actual}",
        )
    }
}

/**
 * Every file a layout spells out is allowed, and everything else is reported once -- as the file,
 * or as the first directory on its way that no declaration passes through -- unless a directory on
 * its way is ignored. Random layouts (nested blocks, `/` chains, `*`, `**`, partial wildcards, file
 * globs, `anyFile()` and `ignore()`) over random trees that hold concretized copies of the
 * declarations, near misses of them and files nothing declares.
 */
class LayoutDenyByDefaultPropertySpec : FreeSpec({
    "宣言したファイルは通り、それ以外はファイルか最初の未知のディレクトリとしてちょうど 1 回報告される" {
        var accepted = 0
        var withUnexpected = 0
        var withAllowed = 0
        checkTrials(
            "deny by default",
            trials = pbtTrials(300),
            seed = pbtSeed(20261121L),
            generate = { random ->
                random.trial().also { trial ->
                    if (accepted(trial.layout)) {
                        accepted++
                        val expected = expectedUnexpected(linesOf(trial.layout), trial.files)
                        if (expected.isNotEmpty()) withUnexpected++
                        if (trial.files.any { allowed(linesOf(trial.layout), it) }) withAllowed++
                    }
                }
            },
            shrinks = { it.smaller() },
            describe = { it.describe() },
        ) { it.check() }
        println("LayoutDenyByDefaultPropertySpec: $accepted layouts accepted, $withUnexpected with something unexpected, $withAllowed with an allowed file")
        // A property over trees where nothing is ever allowed, or nothing ever reported, would say little.
        accepted shouldBeGreaterThan pbtTrials(300) / 2
        withUnexpected shouldBeGreaterThan accepted / 4
        withAllowed shouldBeGreaterThan accepted / 4
    }
})
