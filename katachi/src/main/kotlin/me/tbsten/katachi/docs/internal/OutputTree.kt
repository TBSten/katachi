package me.tbsten.katachi.docs.internal

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

private const val INDENT: String = "  "
