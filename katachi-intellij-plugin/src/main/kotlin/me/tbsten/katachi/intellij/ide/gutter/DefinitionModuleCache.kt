package me.tbsten.katachi.intellij.ide.gutter

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.project.Project
import me.tbsten.katachi.intellij.data.detect.detectDefinitionModules
import me.tbsten.katachi.intellij.ide.ProjectDataModuleSource
import me.tbsten.katachi.intellij.ide.sdkCall
import me.tbsten.katachi.intellij.model.DetectionResult
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Whether the synced Gradle data has a katachi definition module, kept until the next Gradle sync.
 *
 * Reading it walks every module's tasks and source sets, and the line marker provider asks at every highlight (each key
 * stroke) of every Kotlin file, in projects without katachi too. A sync ends the answer ([ProjectDataImportListener])
 * and draws the open files again, so a ◆ that was not there for want of a module appears without an edit;
 * a read that fails is not kept.
 *
 * ```kotlin
 * if (!DefinitionModuleCache.getInstance(project).hasDefinitionModule()) return null
 * ```
 */
@Service(Service.Level.PROJECT)
internal class DefinitionModuleCache(
    private val project: Project,
    /** Reads the synced data when nothing is kept. A test that gives it the answer of its fake synced data replaces the service with its own. */
    private val readSynced: () -> Boolean,
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

    /** The kept answer, or [read]'s (the synced data) when there is none. [read] may throw; nothing is kept then. */
    fun hasDefinitionModule(read: () -> Boolean = readSynced): Boolean {
        val seen = generation.get()
        kept.get()?.takeIf { it.generation == seen }?.let { return it.found }
        val found = read()
        // A sync that ended while [read] ran made it an old answer: give it, but keep nothing.
        if (generation.get() == seen) kept.set(Kept(seen, found))
        return found
    }

    /**
     * The synced data changed: forget the answer, and draw the open files again. The files drawn before the sync ended
     * had no ◆ because the answer was "no", and nothing else tells the daemon the answer is now "yes": the platform's
     * own restart on a root change is not something to rely on. A sync that ends without a change draws what was drawn.
     */
    private fun syncEnded(reason: String) {
        invalidate()
        sdkCall("draw the open files again after the $reason") { HighlightingRefresher.getInstance(project).redrawNow(reason) }
    }

    /** Forgets the kept answer: the synced data changed. */
    fun invalidate() {
        generation.incrementAndGet()
        kept.set(null)
    }

    override fun dispose() = Unit

    companion object {
        fun getInstance(project: Project): DefinitionModuleCache = project.service()

        private fun readSyncedData(project: Project): Boolean = when (detectDefinitionModules(ProjectDataModuleSource(project).read())) {
            is DetectionResult.Found, is DetectionResult.TaskListMissing -> true
            else -> false
        }
    }
}
