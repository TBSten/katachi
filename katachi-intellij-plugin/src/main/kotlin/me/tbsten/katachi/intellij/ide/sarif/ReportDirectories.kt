package me.tbsten.katachi.intellij.ide.sarif

import com.intellij.openapi.vfs.LocalFileSystem
import me.tbsten.katachi.intellij.data.watch.REPORTS
import me.tbsten.katachi.intellij.ide.sdkCall
import me.tbsten.katachi.intellij.model.KatachiModule
import java.nio.file.Files
import java.nio.file.Path

// The VFS reports a file that appears or changes only in a directory whose children it has loaded, so load them here. Blocking: off the EDT.
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
