package me.tbsten.katachi.dsl.internal

// Drive letters count as absolute on every OS so a definition behaves the same on every machine.
internal fun isValidBaselinePath(path: String): Boolean {
    if (path.isBlank()) return false
    if (path.startsWith("/") || '\\' in path) return false
    if (path.length >= 2 && path[0].isLetter() && path[1] == ':') return false
    return path.split('/').none { it.isEmpty() || it == "." || it == ".." }
}
