package me.tbsten.katachi.intellij.ide.authoring.livetemplate

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import me.tbsten.katachi.intellij.ide.sdkCall
import java.nio.file.Path

// Project service so that tests can replace it with `project.replaceService`: the light project's files are not on the local file system.
@Service(Service.Level.PROJECT)
internal class LiveTemplatePorts(private val fileOf: (Path) -> VirtualFile?) {
    @Suppress("unused") // Called by the platform.
    constructor(@Suppress("UNUSED_PARAMETER") project: Project) : this({ path ->
        sdkCall("find $path for the live template") { LocalFileSystem.getInstance().findFileByNioFile(path) }.getOrNull()
    })

    /** The file at an absolute [path], or `null` when it is gone: a definition read a while ago may name a file since deleted. */
    fun file(path: Path): VirtualFile? = fileOf(path)
}
