package me.tbsten.katachi.docs

/**
 * The written pages, drawn as the tree they form on disk.
 *
 * One line per page, prefixed by one line per directory it opens. A flat list repeats the same
 * prefix on every line -- `feature/README.md`, `feature/Screen.md`, `feature/ViewModel.md` --
 * and a reader scanning twenty of them has to read the same word twenty times to find where one
 * group ends and the next begins. The tree says it once.
 *
 * Paths are taken in the order they were generated, which is the order the definition declares:
 * the root first, then each group with its own pages under it. Nothing is sorted, so what the
 * log shows is the shape of the definition rather than an alphabet.
 *
 * Indentation is two spaces and directories end with `/`, the same way the placement tree in a
 * generated group README is drawn. Box-drawing characters are deliberately absent there and
 * here: this goes through `context.log`, which lands in a Gradle console that is not promised to
 * be UTF-8.
 */
internal fun outputTreeLines(paths: Collection<String>): List<String> {
    val lines = mutableListOf<String>()
    var open: List<String> = emptyList()
    for (path in paths) {
        val segments = path.split('/')
        val directories = segments.dropLast(1)
        val shared = directories.zip(open).takeWhile { (next, current) -> next == current }.size
        for (depth in shared until directories.size) {
            lines += INDENT.repeat(depth) + directories[depth] + "/"
        }
        open = directories
        lines += INDENT.repeat(directories.size) + segments.last()
    }
    return lines
}

/** One level of the tree. Two spaces, as in the placement tree of a generated group README. */
private const val INDENT: String = "  "
