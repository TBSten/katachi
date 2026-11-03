package me.tbsten.katachi.report.internal

import java.io.File
import java.nio.file.Files
import me.tbsten.katachi.dsl.files.FsPath

internal fun realIsFile(path: FsPath): Boolean = File(path.value).isFile

internal fun realIsDirectory(path: FsPath): Boolean = File(path.value).isDirectory

internal fun realReadText(path: FsPath): String? =
    File(path.value).takeIf { it.isFile }?.readText(Charsets.UTF_8)

internal fun realFilesUnder(directory: FsPath): List<FsPath> {
    val root = File(directory.value).takeIf { it.isDirectory } ?: return emptyList()
    return root.walkTopDown()
        .filter { it.isFile }
        .map { FsPath.of(it.path) }
        .sortedBy { it.value }
        .toList()
}

internal fun realDirectoriesUnder(directory: FsPath): List<FsPath> {
    val root = File(directory.value).takeIf { it.isDirectory } ?: return emptyList()
    return root.listFiles().orEmpty()
        .filter { it.isDirectory }
        .map { FsPath.of(it.path) }
        .sortedBy { it.value }
}

internal fun realWriteText(file: FsPath, text: String) {
    val target = File(file.value).absoluteFile.toPath()
    target.parent?.let { Files.createDirectories(it) }
    Files.write(target, text.toByteArray(Charsets.UTF_8))
}
