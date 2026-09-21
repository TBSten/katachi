package me.tbsten.katachi.check

import java.io.File
import java.nio.file.Files
import me.tbsten.katachi.dsl.InternalKatachiApi

/**
 * The [KatachiFileSystem] used when a user calls `assert()`: plain `java.io.File`, with the
 * separator differences absorbed by [FsPath].
 *
 * @param workingDirectoryFile where the project root search starts. Defaults to the JVM's
 *   working directory, which under Gradle is the module directory of the test being run.
 */
@InternalKatachiApi
public class RealFileSystem(
    workingDirectoryFile: File = File("").absoluteFile,
) : KatachiFileSystem {
    override val workingDirectory: FsPath = FsPath.of(workingDirectoryFile.absolutePath)

    override fun exists(path: FsPath): Boolean = path.toFile().exists()

    override fun isDirectory(path: FsPath): Boolean {
        val file = path.toFile()
        // A link is reported as a plain file so that the traversal never follows one. It
        // would otherwise be possible to walk a cycle, and a link pointing outside the
        // project would drag foreign files into the report.
        if (Files.isSymbolicLink(file.toPath())) return false
        return file.isDirectory
    }

    override fun list(directory: FsPath): List<FsPath> {
        if (!isDirectory(directory)) return emptyList()
        val names = directory.toFile().list() ?: return emptyList()
        // Sorted by the natural order of the name, not by a locale aware collator, so the
        // order is the same on every machine.
        return names.sorted().map { directory / it }
    }

    override fun toString(): String = "RealFileSystem($workingDirectory)"

    private fun FsPath.toFile(): File = File(value)
}
