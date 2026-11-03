package me.tbsten.katachi.check.internal

import me.tbsten.katachi.dsl.internal.Glob

internal class PrefixGlobIndex<T>(private val items: List<T>, globOf: (T) -> Glob) {
    private val globs: List<Glob> = items.map(globOf)

    // Patterns with another separator (a module path) go under the empty key, which every path reads.
    private val buckets: Map<String, IntArray> = globs.indices
        .groupBy { index ->
            val glob = globs[index]
            if (glob.separator == Glob.PATH_SEPARATOR) glob.literalPrefix.joinToString("/") else ""
        }
        .mapValues { (_, indices) -> indices.toIntArray() }

    fun matching(path: String): List<T> {
        val candidates = candidatesOf(path)
        if (candidates.isEmpty()) return emptyList()
        // Declaration order is part of the answer (the first match per role wins), and the
        // candidates come from several buckets.
        candidates.sort()
        return candidates.filter { globs[it].matches(path) }.map { items[it] }
    }

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
