package me.tbsten.katachi.internal

import java.io.File
import java.net.URI

// Neither Path.toUri() (asks the disk) nor File.toURI() (one slash, terminals do not link it); nothing here
// touches the disk.
internal fun fileUri(absolutePath: String): String {
    val slashed = absolutePath.replace('\\', '/')
    // `C:/repo` has no leading slash; a URI path needs one to read as `file:///C:/repo`.
    val path = if (slashed.startsWith("/")) slashed else "/$slashed"
    return URI("file", "", path, null).toASCIIString()
}

// Roots are strings: this package sits below fs and may not import FsPath.
internal fun fileUri(root: String, relative: String): String =
    if (relative.isEmpty() || relative == ".") {
        fileUri(root)
    } else {
        fileUri(root.trimEnd('/') + "/" + relative)
    }

internal fun absolutePathOf(path: String): String = File(path).absoluteFile.normalize().invariantSeparatorsPath

// Patterns are left as written: a * in a URI points at nothing.
internal fun displayPath(root: String?, path: String): String = when {
    root == null -> path
    path.any { it in PATTERN_CHARACTERS } -> path
    path.startsWith("file:") -> path
    isAbsolute(path) -> fileUri(path)
    else -> fileUri(root, path)
}

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
