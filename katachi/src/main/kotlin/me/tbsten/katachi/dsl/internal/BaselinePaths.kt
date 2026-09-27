package me.tbsten.katachi.dsl.internal

/**
 * Whether [path] can name a baseline file: relative to the project root, `/` separated, never
 * leaving the root, and written the one way it can be -- no `.` segment, no empty segment, no
 * trailing `/` -- so that the path the definition names, the path a report prints and the path
 * `layout { }` is compared with are the same string. A drive letter counts as absolute on every
 * OS, so a definition does not pass on one machine and fail on another.
 */
internal fun isValidBaselinePath(path: String): Boolean {
    if (path.isBlank()) return false
    if (path.startsWith("/") || '\\' in path) return false
    if (path.length >= 2 && path[0].isLetter() && path[1] == ':') return false
    return path.split('/').none { it.isEmpty() || it == "." || it == ".." }
}
