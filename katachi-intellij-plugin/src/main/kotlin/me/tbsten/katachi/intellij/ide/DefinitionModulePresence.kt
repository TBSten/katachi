package me.tbsten.katachi.intellij.ide

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorNotifications
import me.tbsten.katachi.intellij.data.detect.detectDefinitionModules
import me.tbsten.katachi.intellij.ide.gutter.HighlightingRefresher
import me.tbsten.katachi.intellij.model.DetectionResult
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

// Cached per project because reading walks every module's tasks; a sync invalidates it, and a failed or empty read is not kept.
@Service(Service.Level.PROJECT)
internal class DefinitionModulePresence(
    private val project: Project,
    private val readSynced: () -> Boolean?,
) : Disposable {
    @Suppress("unused") // Called by the platform.
    constructor(project: Project) : this(project, { readSyncedData(project) })

    private val generation = AtomicInteger()
    private val kept = AtomicReference<Kept?>(null)

    private data class Kept(val generation: Int, val found: Boolean)

    init {
        sdkCall("listen to Gradle syncs for the katachi gutter") {
            project.messageBus.connect(this).subscribe(
                ProjectDataImportListener.TOPIC,
                object : ProjectDataImportListener {
                    override fun onImportFinished(projectPath: String?) = syncEnded("Gradle sync finished")

                    override fun onImportFailed(projectPath: String?, t: Throwable) = syncEnded("Gradle sync failed")
                },
            )
        }
    }

    fun hasDefinitionModule(read: () -> Boolean? = readSynced): Boolean {
        val seen = generation.get()
        kept.get()?.takeIf { it.generation == seen }?.let { return it.found }
        val found = read() ?: return false
        // A sync that ended while [read] ran made it an old answer: give it, but keep nothing.
        if (generation.get() == seen) kept.set(Kept(seen, found))
        return found
    }

    private fun syncEnded(reason: String) {
        invalidate()
        sdkCall("draw the open files again after the $reason") { HighlightingRefresher.getInstance(project).redrawNow(reason) }
        // The notification that was not there for want of a module (decision 17) comes without an edit as well.
        sdkCall("update the katachi notifications after the $reason") {
            if (!project.isDisposed) EditorNotifications.getInstance(project).updateAllNotifications()
        }
    }

    fun invalidate() {
        generation.incrementAndGet()
        kept.set(null)
    }

    override fun dispose() = Unit

    companion object {
        fun getInstance(project: Project): DefinitionModulePresence = project.service()

        private fun readSyncedData(project: Project): Boolean? = when (detectDefinitionModules(ProjectDataModuleSource(project).read())) {
            is DetectionResult.Found, is DetectionResult.TaskListMissing -> true
            DetectionResult.NotSynced -> null
            else -> false
        }
    }
}
