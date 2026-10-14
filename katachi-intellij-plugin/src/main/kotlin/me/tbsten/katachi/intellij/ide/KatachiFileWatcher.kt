package me.tbsten.katachi.intellij.ide

import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import me.tbsten.katachi.intellij.data.watch.FileChanges
import me.tbsten.katachi.intellij.data.watch.classifyChanges
import me.tbsten.katachi.intellij.ide.rules.KatachiBaselineService
import me.tbsten.katachi.intellij.ide.sarif.KatachiViolationsService
import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * Watches the files the plugin depends on through VFS events (design 4.7, 4.8) and tells each open
 * project's services: a definition module's `src/` and build script change the definition (the
 * "definition changed" banner, a stale model, and with "reload when saved" a reload after a save of its
 * test sources), a `build/reports/katachi/` SARIF changes the violations, and anything else under a
 * project root makes the violations stale. A change to a `katachi-baseline.json` the no-role notification read redraws the notifications. Projects whose project service was never created are skipped:
 * nothing of katachi runs there. Only path strings are compared here (design 5 "ファイル監視").
 */
internal class KatachiFileWatcher : AsyncFileListener {
    override fun prepareChange(events: List<VFileEvent>): AsyncFileListener.ChangeApplier? {
        // Missing a change only misses a stale mark; the answers themselves are as they were.
        val watching = sdkCall("find the open katachi projects") {
            ProjectManager.getInstance().openProjects.mapNotNull { project ->
                if (project.isDisposed) return@mapNotNull null
                val service = project.serviceIfCreated<KatachiProjectService>()
                val baselines = project.serviceIfCreated<KatachiBaselineService>()
                if (service == null && baselines == null) null else Watching(service, project.serviceIfCreated<KatachiViolationsService>(), baselines)
            }
        }.getOrNull().orEmpty()
        if (watching.isEmpty()) return null
        val paths = events.mapNotNull { event -> sdkCall("read the path of a VFS event") { event.path }.getOrNull()?.let(::pathOf) }
        val changed = watching.mapNotNull { target ->
            val service = target.service
            // A generation writing into its own definition module (a single-module build) is not a definition change.
            val changes = classifyChanges(
                paths,
                service?.watchedModules().orEmpty(),
                service?.projectRoots().orEmpty(),
                target.baselines?.watchedFiles.orEmpty(),
                service?.viewModel?.let { it::isOwnWrite } ?: { false },
            )
            if (changes.isEmpty) null else target to changes
        }
        if (changed.isEmpty()) return null
        return object : AsyncFileListener.ChangeApplier {
            override fun afterVfsChange() {
                changed.forEach { (target, changes) -> dispatch(target, changes) }
            }
        }
    }

    /** One open project's services that care about file changes; the project service is `null` while nothing of katachi ran there but the baseline read. */
    private class Watching(val service: KatachiProjectService?, val violations: KatachiViolationsService?, val baselines: KatachiBaselineService?)

    private fun dispatch(target: Watching, changes: FileChanges) {
        target.service?.onFilesChanged(changes)
        target.violations?.onFilesChanged(changes)
        if (changes.baselineChanged) target.baselines?.onBaselineChanged()
    }
}

private fun pathOf(text: String): Path? = try {
    Path.of(text)
} catch (_: InvalidPathException) {
    null
}
