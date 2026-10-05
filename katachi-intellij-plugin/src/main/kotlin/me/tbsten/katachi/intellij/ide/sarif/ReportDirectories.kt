package me.tbsten.katachi.intellij.ide.sarif

import com.intellij.openapi.vfs.LocalFileSystem
import me.tbsten.katachi.intellij.data.watch.REPORTS
import me.tbsten.katachi.intellij.ide.sdkCall
import me.tbsten.katachi.intellij.model.KatachiModule
import java.nio.file.Files
import java.nio.file.Path

/**
 * Makes the VFS know where [module]'s SARIF reports are written (design 4.7): the VFS reports a file that
 * appears or changes only in a directory whose children it has loaded, so a report a terminal's Gradle writes
 * would otherwise never reach the file watcher. Refreshes `build/reports/katachi` and loads its children and
 * its task directories' children; while the directory does not exist yet, the nearest ancestor inside the
 * module is refreshed and loaded one level, so that the directory's creation is seen. Blocking: off the EDT.
 *
 * ```kotlin
 * loadReportDirectoriesIntoVfs(module) // before listing the module's SARIF files with nio
 * ```
 */
internal fun loadReportDirectoriesIntoVfs(module: KatachiModule) {
    sdkCall("load the report directories of ${module.gradlePath} into the VFS") {
        val reports = module.directory.resolve(REPORTS)
        val nearest = generateSequence<Path>(reports) { it.parent }
            .takeWhile { it.startsWith(module.directory) }
            .firstOrNull { Files.isDirectory(it) }
        val directory = nearest?.let { LocalFileSystem.getInstance().refreshAndFindFileByNioFile(it) }
        if (directory != null) {
            val children = directory.children
            if (nearest == reports) children.filter { it.isDirectory }.forEach { it.children }
        }
    }
}
