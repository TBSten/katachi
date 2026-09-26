package me.tbsten.katachi.internal

import java.io.File
import java.net.URI

/**
 * [absolutePath] as a `file:///...` URI, percent-encoded down to ASCII.
 *
 * Neither `Path.toUri()` nor `File.toURI()`: the first asks the disk whether the path is a
 * directory (so `/repo` on a fake file system prints differently from one machine to the next),
 * and the second writes `file:/` with a single slash, which terminals do not pick up as a link.
 * Nothing here touches the disk, and symbolic links are left as they are for the same reason.
 */
internal fun fileUri(absolutePath: String): String {
    val slashed = absolutePath.replace('\\', '/')
    // `C:/repo` has no leading slash; a URI path needs one to read as `file:///C:/repo`.
    val path = if (slashed.startsWith("/")) slashed else "/$slashed"
    return URI("file", "", path, null).toASCIIString()
}

/**
 * [relative] under [root], an absolute `/` separated path, as a `file:///...` URI. `.` and the
 * empty path are [root] itself.
 *
 * Roots are plain strings rather than `FsPath` because this package sits below `fs` and may not
 * import it; an `FsPath` hands over its `value`.
 */
internal fun fileUri(root: String, relative: String): String =
    if (relative.isEmpty() || relative == ".") {
        fileUri(root)
    } else {
        fileUri(root.trimEnd('/') + "/" + relative)
    }

/**
 * [path], an OS path such as `File.path`, as the absolute `/` separated root a message resolves
 * paths against: made absolute against the working directory first, since a URI has no notion of
 * relative.
 */
internal fun absolutePathOf(path: String): String = File(path).absoluteFile.normalize().invariantSeparatorsPath

/**
 * How a report prints [path], a path relative to [root] (absolute, `/` separated) as violations
 * carry it.
 *
 * A `file:///...` URI when there is a root to resolve it against, so a reader can open it from
 * the terminal as it stands. Left as it is when there is no root, when it is a pattern (a
 * `*` in a URI points at nothing), and when it is already absolute in URI form.
 */
internal fun displayPath(root: String?, path: String): String = when {
    root == null -> path
    path.any { it in PATTERN_CHARACTERS } -> path
    path.startsWith("file:") -> path
    isAbsolute(path) -> fileUri(path)
    else -> fileUri(root, path)
}

/**
 * How a report prints [path], a path relative to [root] that names a file or directory found on
 * disk — never a declaration, so never a pattern.
 *
 * [displayPath] without the pattern check: a file really named `[id].kt` or `a{b}.kt` is still a
 * file, and its `[` `{` are percent-encoded into the URI like any other character.
 */
internal fun displayExistingPath(root: String?, path: String): String = when {
    root == null -> path
    path.startsWith("file:") -> path
    isAbsolute(path) -> fileUri(path)
    else -> fileUri(root, path)
}

private const val PATTERN_CHARACTERS: String = "*?[{"

private fun isAbsolute(path: String): Boolean {
    val slashed = path.replace('\\', '/')
    return slashed.startsWith("/") ||
        (slashed.length >= 3 && slashed[0].isLetter() && slashed[1] == ':' && slashed[2] == '/')
}
