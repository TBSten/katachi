package me.tbsten.katachi.report.internal

import java.io.File
import java.nio.file.Files
import me.tbsten.katachi.dsl.files.FsPath

/*
 * The disk, as the SARIF report touches it. Every access goes through one of these so that a
 * spec can hand in a tree that only exists in memory, the way `KatachiFileSystem` lets the walk
 * be tested without a disk.
 */

/** Whether a regular file sits at [path]. */
internal fun realIsFile(path: FsPath): Boolean = File(path.value).isFile

/** Whether a directory sits at [path]. */
internal fun realIsDirectory(path: FsPath): Boolean = File(path.value).isDirectory

/** The text of the file at [path], or `null` when there is no file there. */
internal fun realReadText(path: FsPath): String? =
    File(path.value).takeIf { it.isFile }?.readText(Charsets.UTF_8)

/** Every regular file at or below [directory], sorted by path; empty when it is not a directory. */
internal fun realFilesUnder(directory: FsPath): List<FsPath> {
    val root = File(directory.value).takeIf { it.isDirectory } ?: return emptyList()
    return root.walkTopDown()
        .filter { it.isFile }
        .map { FsPath.of(it.path) }
        .sortedBy { it.value }
        .toList()
}

/** The directories directly below [directory], sorted by path; empty when it is not a directory. */
internal fun realDirectoriesUnder(directory: FsPath): List<FsPath> {
    val root = File(directory.value).takeIf { it.isDirectory } ?: return emptyList()
    return root.listFiles().orEmpty()
        .filter { it.isDirectory }
        .map { FsPath.of(it.path) }
        .sortedBy { it.value }
}

/**
 * Writes [text] to [file] as UTF-8, creating the directories above it.
 *
 * @throws java.io.IOException when the file cannot be written.
 */
internal fun realWriteText(file: FsPath, text: String) {
    val target = File(file.value).absoluteFile.toPath()
    target.parent?.let { Files.createDirectories(it) }
    Files.write(target, text.toByteArray(Charsets.UTF_8))
}
