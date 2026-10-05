package me.tbsten.katachi.intellij.ide.gutter

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.tbsten.katachi.intellij.ide.definition.DefinitionModelService
import me.tbsten.katachi.intellij.ide.sarif.ViolationsService
import me.tbsten.katachi.intellij.ide.sdkCall
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.presentation.gutter.GutterFreshness
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference

/**
 * Draws the gutter and the annotations of the open files again when the definition or the violations change (design
 * 2.6, U-13): the daemon recomputes on an edit, not when a service's state moves behind its back.
 *
 * A change goes [SETTLE_MILLIS] without another before the open files under a definition's root are restarted with
 * `restart(psiFile, reason)`, so a load that passes through several states draws once. Going to `Loading` draws
 * nothing: the previous answer stays until the new one arrives. When the answer goes away (`NotLoaded`, `Unavailable`
 * with nothing before it) the roots of the last draw are drawn again too, so the ◆ it left are removed at once.
 *
 * ```kotlin
 * HighlightingRefresher.getInstance(project).follow(definitions, violations) // the first time a provider finds them
 * ```
 */
@Service(Service.Level.PROJECT)
internal class HighlightingRefresher(private val project: Project, private val scope: CoroutineScope) {
    private val followed = AtomicReference<Followed?>(null)
    private val pending = AtomicReference<Job?>(null)

    /** The roots the last draw covered: a change that takes the answer away must draw these again, though no definition names them now. */
    private val lastRoots = AtomicReference<List<Path>>(emptyList())

    /** What a change restarts: the open files under these roots, or every open file when there are none. Tests replace it to see what is restarted and when. */
    @Volatile
    var restarter: (reason: String, roots: List<Path>) -> Unit = ::restartOpenFilesUnder

    private data class Followed(val definitions: DefinitionModelService, val violations: ViolationsService?, val job: Job)

    /** Starts following the two services' states. Calling it again for the same services does nothing; for others, follows those instead. */
    fun follow(definitions: DefinitionModelService, violations: ViolationsService?) {
        val current = followed.get()
        if (current != null && current.definitions === definitions && current.violations === violations) return
        // Read here, not in the coroutine: a change made before it starts collecting is still a change.
        val definitionBefore = definitions.state.value
        val violationsBefore = violations?.state?.value
        lastRoots.set(rootsOf(definitions))
        val job = scope.launch {
            launch {
                var before: DefinitionState = definitionBefore
                definitions.state.collect { after ->
                    if (GutterFreshness.shouldRedraw(before, after)) schedule("the definition is ${after.javaClass.simpleName}", definitions)
                    before = after
                }
            }
            if (violations != null) {
                launch {
                    var before = violationsBefore
                    violations.state.collect { after ->
                        if (after != before) schedule("the violations changed", definitions)
                        before = after
                    }
                }
            }
        }
        if (followed.compareAndSet(current, Followed(definitions, violations, job))) current?.job?.cancel() else job.cancel()
    }

    /** Draws every open file again now, for a change that is not a state of the two services (a setting). */
    fun redrawNow(reason: String) {
        restarter(reason, emptyList())
    }

    private fun rootsOf(definitions: DefinitionModelService): List<Path> =
        definitions.definitions.map { it.definition?.projectRoot ?: it.module.linkedRootPath }.distinct()

    private fun schedule(reason: String, definitions: DefinitionModelService) {
        val job = scope.launch {
            delay(SETTLE_MILLIS)
            val now = rootsOf(definitions)
            // Before and after: a definition that went away still has ◆ and menus on screen. With no root in either, any open file may have one.
            val roots = (lastRoots.getAndSet(now) + now).distinct()
            restarter(reason, roots)
        }
        pending.getAndSet(job)?.cancel()
    }

    private fun restartOpenFilesUnder(reason: String, roots: List<Path>) {
        scope.launch {
            val open = sdkCall("list the open files to draw the katachi gutter again") {
                if (project.isDisposed) emptyList() else FileEditorManager.getInstance(project).openFiles.toList()
            }.getOrDefault(emptyList())
            for (file in open) {
                val path = sdkCall("find the path of ${file.name}") { file.fileSystem.getNioPath(file) }.getOrNull() ?: continue
                if (roots.isNotEmpty() && roots.none { path.startsWith(it) }) continue
                val psiFile = sdkCall("find the PSI of ${file.path}") { readAction { PsiManager.getInstance(project).findFile(file) } }.getOrNull()
                    ?: continue
                sdkCall("draw the katachi gutter of ${file.path} again") {
                    if (!project.isDisposed) DaemonCodeAnalyzer.getInstance(project).restart(psiFile, "katachi: $reason")
                }
            }
        }
    }

    companion object {
        const val SETTLE_MILLIS: Long = 300L

        fun getInstance(project: Project): HighlightingRefresher = project.service()
    }
}
