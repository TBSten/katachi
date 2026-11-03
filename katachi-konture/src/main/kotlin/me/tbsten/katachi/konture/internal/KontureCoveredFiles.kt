package me.tbsten.katachi.konture.internal

import java.io.File

// Konture reports the path its parse cache remembers (first parse in this JVM: `/tmp` vs `/private/tmp`, symlinks): strip the root first,
// and canonicalise both sides, lazily, only when that fails.
internal class KontureCoveredFiles(
    private val projectRoot: String,
    files: Collection<String>,
) {
    private val relative: Set<String> = files.toHashSet()

    private val relativeByCanonicalPath: Map<String, String> by lazy {
        relative.associateBy { File(projectRoot, it).canonicalPath }
    }

    fun relativeOf(absolutePath: String): String? {
        val reported = File(absolutePath)
        val direct = reported.invariantSeparatorsPath.removePrefix("$projectRoot/")
        if (direct in relative) return direct
        return relativeByCanonicalPath[reported.canonicalPath]
    }
}
