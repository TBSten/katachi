package me.tbsten.katachi.intellij.data.definition

/**
 * [items] bucketed by the literal levels their pattern starts with, so a path is only tried against
 * the patterns that could match it. A port of katachi's `PrefixGlobIndex`.
 *
 * A pattern can only match a path whose leading levels are its [KatachiGlob.literalPrefix], so the
 * buckets to read are the path's own leading levels, from none up to all of them. Every candidate is
 * still matched in full: the buckets only skip patterns, they never decide a match.
 *
 * ```kotlin
 * val index = PrefixGlobIndex(entries) { KatachiGlob.compile(it.pattern) }
 * index.matching("core/domain/Foo.kt") // the entries whose pattern matches, in the order given
 * ```
 */
internal class PrefixGlobIndex<T>(private val items: List<T>, globOf: (T) -> KatachiGlob) {
    private val globs: List<KatachiGlob> = items.map(globOf)

    /**
     * Indices into [items], ascending, keyed by the literal levels joined with `/`. A pattern with
     * another separator (a module path) goes to the empty key, which every path reads.
     */
    private val buckets: Map<String, IntArray> = globs.indices
        .groupBy { index ->
            val glob = globs[index]
            if (glob.separator == KatachiGlob.PATH_SEPARATOR) glob.literalPrefix.joinToString("/") else ""
        }
        .mapValues { (_, indices) -> indices.toIntArray() }

    /** The items whose pattern matches [path], in the order they were given. */
    fun matching(path: String): List<T> {
        val candidates = candidatesOf(path)
        if (candidates.isEmpty()) return emptyList()
        // Declaration order is part of the answer, and the candidates come from several buckets.
        candidates.sort()
        return candidates.filter { globs[it].matches(path) }.map { items[it] }
    }

    /** Whether any item's pattern matches [path]. */
    fun anyMatches(path: String): Boolean = candidatesOf(path).any { globs[it].matches(path) }

    private fun candidatesOf(path: String): IntArray {
        // Always a fresh array: the caller sorts it, and the buckets are shared.
        var candidates = EMPTY + (buckets[""] ?: EMPTY)
        var end = path.indexOf('/')
        while (true) {
            val prefix = if (end < 0) path else path.substring(0, end)
            // The empty prefix was read above; reading it again would list its items twice.
            if (prefix.isNotEmpty()) buckets[prefix]?.let { candidates += it }
            if (end < 0) return candidates
            end = path.indexOf('/', end + 1)
        }
    }

    private companion object {
        val EMPTY: IntArray = IntArray(0)
    }
}
