package me.tbsten.katachi.dokka.internal.link

/**
 * Turns a path relative to the published output root into the link written in an llms file.
 *
 * The one place a URL is put together: the path always comes from Dokka's location provider or
 * from a module's `relativePathToOutputDirectory`, and [baseUrl] from the configuration. With no
 * [baseUrl] the path is made relative to the directory of the file it is written in.
 */
internal class LinkBase(baseUrl: String?) {
    private val prefix: String? = baseUrl?.trim()?.takeIf { it.isNotEmpty() }?.let { if (it.endsWith("/")) it else "$it/" }

    /** Whether links are made absolute at all. */
    val isAbsolute: Boolean get() = prefix != null

    /** The link to [path], written in a file in [fromDirectory]; both are relative to the output root. */
    fun link(path: String, fromDirectory: String = ""): String = when {
        // The location provider answers with a full URL for declarations of other libraries.
        isAbsolute(path) -> path
        prefix == null -> relativePath(fromDirectory, path)
        else -> prefix + path.removePrefix("/")
    }

    companion object {
        private val ABSOLUTE = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")

        /** Whether [path] is already a full URL, such as a link to another library's docs. */
        fun isAbsolute(path: String): Boolean = ABSOLUTE.containsMatchIn(path)
    }
}

/**
 * [path] as seen from [fromDirectory], both relative to the same root and without `..` in them.
 *
 * `relativePath("a/b", "a/c/d.html")` is `../c/d.html`. An anchor after `#` is kept.
 */
internal fun relativePath(fromDirectory: String, path: String): String {
    val from = segmentsOf(fromDirectory)
    val target = segmentsOf(path.substringBefore('#'))
    val anchor = path.substringAfter('#', "").let { if (it.isEmpty()) "" else "#$it" }
    // The last segment of the target is the file, which never matches a directory of [from].
    val common = from.zip(target.dropLast(1)).takeWhile { (a, b) -> a == b }.size
    return (List(from.size - common) { ".." } + target.drop(common)).joinToString("/") + anchor
}

/** [directory] and [path] joined, either of them possibly empty. */
internal fun joinPath(directory: String, path: String): String =
    (segmentsOf(directory) + segmentsOf(path)).joinToString("/")

/** The directory of the file at [path], empty for a file at the root. */
internal fun directoryOf(path: String): String = path.substringBefore('#').substringBeforeLast('/', "")

private fun segmentsOf(path: String): List<String> = path.split('/').filter { it.isNotEmpty() }
