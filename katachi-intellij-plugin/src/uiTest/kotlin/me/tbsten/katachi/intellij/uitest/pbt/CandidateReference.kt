package me.tbsten.katachi.intellij.uitest.pbt

import me.tbsten.katachi.intellij.data.placement.DirectoryLister
import java.nio.file.Path

/**
 * A directory tree in memory that the property tests change between steps, and that the plugin reads through
 * [lister] like the IDE's project tree. It records nothing but the directories.
 *
 * ```kotlin
 * val disk = FakeDisk()
 * disk.add(ROOT.resolve("feature/home"), ROOT)
 * disk.lister.childDirectories(ROOT.resolve("feature")) // ["home"]
 * ```
 */
internal class FakeDisk {
    /** Every directory that exists, parents included. */
    val dirs: MutableSet<Path> = linkedSetOf()

    /** Adds [dir] and every directory between it and [root]. */
    fun add(dir: Path, root: Path) {
        var current: Path? = dir
        while (current != null && current != root && current.startsWith(root)) {
            dirs.add(current)
            current = current.parent
        }
    }

    /** Removes [dir] and everything below it. */
    fun remove(dir: Path) {
        dirs.removeAll { it.startsWith(dir) }
    }

    fun childrenOf(dir: Path): List<String> = dirs.filter { it.parent == dir }.map { it.fileName.toString() }

    val lister: DirectoryLister = DirectoryLister { directory -> childrenOf(directory) }
}

/**
 * The candidates a capture field should offer, worked out the plain way: every directory of the whole [FakeDisk] is looked at
 * (no walking down from the levels above, no early stop) and its name is matched against the pattern's level with a regular
 * expression made from the text of the pattern. Independent of `CaptureCandidates` and `PathPattern`, as `MatcherReference` is
 * of the matcher.
 *
 * Cases it does not judge return `null` ("no opinion"): a `<x>` (derived part) at or above the capture's level, and two captures
 * on the capture's own level (where splitting a name is ambiguous). The generators avoid them on purpose most of the time.
 *
 * ```kotlin
 * CandidateReference.of("feature/\${feature}/src/\${name}.kt", "feature", emptyMap(), ROOT, disk) // Candidates(["home", "settings"], 0)
 * ```
 */
internal object CandidateReference {
    /** The reference's answer: [values] (at most 100) and how many more there were. */
    data class Candidates(val values: List<String>, val hidden: Int)

    private val capturePart = Regex("""\$\{([^}]*)}""")
    private const val GLOB = "*?[{"

    /** `null`: the reference has no opinion. An empty [Candidates.values]: nothing to offer. */
    fun of(pattern: String, capture: String, entered: Map<String, String>, root: Path, disk: FakeDisk): Candidates? {
        val segments = pattern.split('/')
        val level = segments.indexOfFirst { "\${$capture}" in it }
        if (level < 0 || level == segments.lastIndex) return Candidates(emptyList(), 0)
        if (segments.take(level + 1).any { '<' in it }) return null
        val own = segments[level]
        if (capturePart.findAll(own).count() != 1 || capturePart.findAll(own).any { it.groupValues[1] != capture }) return null
        val above = (0 until level).map { concrete(segments[it], entered) ?: return Candidates(emptyList(), 0) }
        val ownRegex = Regex(
            buildString {
                var last = 0
                for (match in capturePart.findAll(own)) {
                    append(Regex.escape(own.substring(last, match.range.first)))
                    append("(.+)")
                    last = match.range.last + 1
                }
                append(Regex.escape(own.substring(last)))
            },
        )
        val found = disk.dirs.asSequence()
            .filter { it.startsWith(root) && it != root }
            .map { dir -> root.relativize(dir).map { it.toString() } }
            .filter { relative -> relative.size == level + 1 && relative.take(level) == above && !relative.last().startsWith(".") }
            .mapNotNull { relative -> ownRegex.matchEntire(relative.last())?.groupValues?.get(1) }
            // A capture is one directory level, never `.` or `..` (katachi refuses it), so a directory only part of whose name is `..` is no candidate.
            .filter { it.isNotBlank() && it != "." && it != ".." }
            .distinct()
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
            .toList()
        return Candidates(found.take(100), (found.size - 100).coerceAtLeast(0))
    }

    /** The directory name a level makes when every capture of it is entered; `null` when one is not or the name is not one level. */
    fun concrete(segment: String, entered: Map<String, String>): String? {
        if ('<' in segment) return null
        val literals = capturePart.split(segment)
        if (literals.any { part -> part.any { it in GLOB } }) return null
        val text = buildString {
            var last = 0
            for (match in capturePart.findAll(segment)) {
                append(segment.substring(last, match.range.first))
                append(entered[match.groupValues[1]]?.takeIf { it.isNotBlank() } ?: return null)
                last = match.range.last + 1
            }
            append(segment.substring(last))
        }
        if (text.isEmpty() || text == "." || text == ".." || text.any { it == '/' || it == '\\' }) return null
        return text
    }

    /** The first pattern of [patterns] that holds `${capture}`, as the plugin picks the file whose path a capture sits in. */
    fun patternHolding(patterns: List<String>, capture: String): String? = patterns.firstOrNull { "\${$capture}" in it }

    /** The directory [pattern]'s levels above [capture]'s own make, under [root]; `null` when a value is missing. */
    fun parentOf(pattern: String, capture: String, entered: Map<String, String>, root: Path): Path? {
        val segments = pattern.split('/')
        val level = segments.indexOfFirst { "\${$capture}" in it }
        if (level < 0 || level == segments.lastIndex) return null
        return (0 until level).fold(root) { path, i -> path.resolve(concrete(segments[i], entered) ?: return null) }
    }
}
