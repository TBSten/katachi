package me.tbsten.katachi.intellij.ide

import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import me.tbsten.katachi.intellij.data.watch.FileChanges
import me.tbsten.katachi.intellij.data.watch.classifyChanges
import me.tbsten.katachi.intellij.ide.sarif.KatachiViolationsService
import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * Watches the files the plugin depends on through VFS events (design 4.7, 4.8) and tells each open
 * project's services: a definition module's `src/` and build script change the definition (the
 * "definition changed" banner, a stale model, and with "reload when saved" a reload after a save of its
 * test sources), a `build/reports/katachi/` SARIF changes the violations, and anything else under a
 * project root makes the violations stale. Projects whose project service was never created are skipped:
 * nothing of katachi runs there. Only path strings are compared here (design 5 "ファイル監視").
 */
internal class KatachiFileWatcher : AsyncFileListener {
    override fun prepareChange(events: List<VFileEvent>): AsyncFileListener.ChangeApplier? {
        // Missing a change only misses a stale mark; the answers themselves are as they were.
        val services = sdkCall("find the open katachi projects") {
            ProjectManager.getInstance().openProjects.mapNotNull { project ->
                if (project.isDisposed) return@mapNotNull null
                project.serviceIfCreated<KatachiProjectService>()?.let { it to project.serviceIfCreated<KatachiViolationsService>() }
            }
        }.getOrNull().orEmpty()
        if (services.isEmpty()) return null
        val paths = events.mapNotNull { event -> sdkCall("read the path of a VFS event") { event.path }.getOrNull()?.let(::pathOf) }
        val changed = services.mapNotNull { (service, violations) ->
            val viewModel = service.viewModel
            // A generation writing into its own definition module (a single-module build) is not a definition change.
            val changes = classifyChanges(paths, service.watchedModules(), service.projectRoots(), viewModel::isOwnWrite)
            if (changes.isEmpty) null else Triple(service, violations, changes)
        }
        if (changed.isEmpty()) return null
        return object : AsyncFileListener.ChangeApplier {
            override fun afterVfsChange() {
                changed.forEach { (service, violations, changes) -> dispatch(service, violations, changes) }
            }
        }
    }

    private fun dispatch(service: KatachiProjectService, violations: KatachiViolationsService?, changes: FileChanges) {
        service.onFilesChanged(changes)
        violations?.onFilesChanged(changes)
    }
}

private fun pathOf(text: String): Path? = try {
    Path.of(text)
} catch (_: InvalidPathException) {
    null
}
