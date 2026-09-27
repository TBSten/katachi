package me.tbsten.katachi.intellij.ide

import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import me.tbsten.katachi.intellij.model.KatachiModule
import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * Watches the definition modules' `src/` and build scripts through VFS events (E-44) and tells the
 * project's tool window, which shows the "definition changed" banner. Projects whose tool window was
 * never shown are skipped: their service does not exist. The files the latest generation wrote are
 * skipped too.
 */
internal class DefinitionChangeWatcher : AsyncFileListener {
    override fun prepareChange(events: List<VFileEvent>): AsyncFileListener.ChangeApplier? {
        // Missing a change only misses the banner; the list itself is as it was.
        val services = sdkCall("find the open katachi tool windows") {
            ProjectManager.getInstance().openProjects.mapNotNull { project ->
                if (project.isDisposed) return@mapNotNull null
                project.serviceIfCreated<KatachiProjectService>()
            }
        }.getOrNull().orEmpty()
        if (services.isEmpty()) return null
        val paths = events.mapNotNull { event -> sdkCall("read the path of a VFS event") { event.path }.getOrNull()?.let(::pathOf) }
        val changed = services.filter { service ->
            val viewModel = service.viewModel
            // A generation writing into its own definition module (a single-module build) is not a change.
            paths.any { isDefinitionChange(it, viewModel.definitionModules) && !viewModel.isOwnWrite(it) }
        }
        if (changed.isEmpty()) return null
        return object : AsyncFileListener.ChangeApplier {
            override fun afterVfsChange() {
                changed.forEach { it.onDefinitionChanged() }
            }
        }
    }
}

/** Whether [path] is part of a definition among [modules]: under `src/`, or the build script. `build/` never is. */
internal fun isDefinitionChange(path: Path, modules: List<KatachiModule>): Boolean = modules.any { module ->
    val directory = module.directory
    path.startsWith(directory.resolve("src")) || path.parent == directory && path.fileName.toString() in BUILD_SCRIPTS
}

private val BUILD_SCRIPTS = setOf("build.gradle.kts", "build.gradle")

private fun pathOf(text: String): Path? = try {
    Path.of(text)
} catch (_: InvalidPathException) {
    null
}
