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
 * stroke) of every Kotlin file, in projects without katachi too. A sync ends the answer ([ProjectDataImportListener]);
 * a read that fails is not kept.
 *
 * ```kotlin
 * if (!DefinitionModuleCache.getInstance(project).hasDefinitionModule()) return null
 * ```
 */
@Service(Service.Level.PROJECT)
internal class DefinitionModuleCache(private val project: Project) : Disposable {
    private val generation = AtomicInteger()
    private val kept = AtomicReference<Kept?>(null)

    private data class Kept(val generation: Int, val found: Boolean)

    init {
        sdkCall("listen to Gradle syncs for the katachi gutter") {
            project.messageBus.connect(this).subscribe(
                ProjectDataImportListener.TOPIC,
                object : ProjectDataImportListener {
                    override fun onImportFinished(projectPath: String?) = invalidate()

                    override fun onImportFailed(projectPath: String?, t: Throwable) = invalidate()
                },
            )
        }
    }

    /** The kept answer, or [read]'s (the synced data) when there is none. [read] may throw; nothing is kept then. */
    fun hasDefinitionModule(read: () -> Boolean = ::readSyncedData): Boolean {
        val seen = generation.get()
        kept.get()?.takeIf { it.generation == seen }?.let { return it.found }
        val found = read()
        // A sync that ended while [read] ran made it an old answer: give it, but keep nothing.
        if (generation.get() == seen) kept.set(Kept(seen, found))
        return found
    }

    /** Forgets the kept answer: the synced data changed. */
    fun invalidate() {
        generation.incrementAndGet()
        kept.set(null)
    }

    private fun readSyncedData(): Boolean = when (detectDefinitionModules(ProjectDataModuleSource(project).read())) {
        is DetectionResult.Found, is DetectionResult.TaskListMissing -> true
        else -> false
    }

    override fun dispose() = Unit

    companion object {
        fun getInstance(project: Project): DefinitionModuleCache = project.service()
    }
}
