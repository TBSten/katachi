package me.tbsten.katachi.intellij.ide.gutter

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.ui.EditorNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import me.tbsten.katachi.intellij.ide.definition.DefinitionModelService
import me.tbsten.katachi.intellij.ide.sarif.ViolationsService
import me.tbsten.katachi.intellij.ide.sdkCall
import me.tbsten.katachi.intellij.model.DefinitionErrors
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.presentation.gutter.GutterFreshness
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

internal data class Redraw(val seq: Long, val reason: String, val definitions: DefinitionModelService?, val definition: DefinitionState?)

// One change is one job: the settling time, the restart of every file and the record in `lastRedraw` belong to it, so a newer change cancels an older one and the record never goes backwards.
@Service(Service.Level.PROJECT)
internal class HighlightingRefresher(private val project: Project, private val scope: CoroutineScope) {
    private val followed = AtomicReference<Followed?>(null)
    private val pending = AtomicReference<Job?>(null)

    private val nowJob = AtomicReference<Job?>(null)
    private val recordLock = Any()
    private val seq = AtomicLong(0)
    private val _lastRedraw = MutableStateFlow<Redraw?>(null)

    val lastRedraw: StateFlow<Redraw?> get() = _lastRedraw

    private val history = AtomicReference<List<Redraw>>(emptyList())

    // A slow restart can finish after a newer draw, so `lastRedraw` may name an older state than a draw before it; waiters look here.
    internal val redraws: List<Redraw> get() = history.get()

    val isBusy: Boolean get() = pending.get()?.isActive == true || nowJob.get()?.isActive == true

    private val lastRoots = AtomicReference<List<Path>>(emptyList())

    @Volatile
    var restarter: suspend (reason: String, roots: List<Path>) -> Unit = ::restartOpenFilesUnder

    private data class Followed(
        val definitions: DefinitionModelService,
        val violations: ViolationsService?,
        val job: Job,
        val start: FollowStart,
    )

    internal class FollowStart(val definition: DefinitionState, val violations: Any?, val errors: DefinitionErrors = DefinitionErrors.None)

    internal fun followStartOf(definitions: DefinitionModelService): FollowStart? =
        followed.get()?.takeIf { it.definitions === definitions }?.start

    fun follow(definitions: DefinitionModelService, violations: ViolationsService?) {
        val current = followed.get()
        if (current != null && current.definitions === definitions && current.violations === violations) return
        // Read here, not in the coroutine: a change made before it starts collecting is still a change.
        val definitionBefore = definitions.state.value
        val violationsBefore = violations?.state?.value
        val errorsBefore = definitions.definitionErrors.value
        lastRoots.set(rootsOf(definitions))
        val job = scope.launch(start = CoroutineStart.LAZY) {
            launch {
                var before: DefinitionState = definitionBefore
                definitions.state.collect { after ->
                    if (GutterFreshness.shouldRedraw(before, after)) schedule("the definition is ${after.javaClass.simpleName}", definitions)
                    before = after
                }
            }
            launch {
                // The mistakes katachi found in the definition (ide-authoring design 2.4) ride on the same job as every other change (Q-2).
                var before: DefinitionErrors = errorsBefore
                definitions.definitionErrors.collect { after ->
                    if (after != before) schedule("the definition errors changed", definitions)
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
        if (followed.compareAndSet(current, Followed(definitions, violations, job, FollowStart(definitionBefore, violationsBefore, errorsBefore)))) {
            current?.job?.cancel()
            // A draw waiting for the service that was followed must not leave a record in the world of the new one.
            if (current != null && current.definitions !== definitions) pending.getAndSet(null)?.cancel()
            job.start()
        } else {
            job.cancel()
        }
    }

    fun redrawNow(reason: String) {
        val definitions = followed.get()?.definitions
        val job = scope.launch {
            val roots = emptyList<Path>()
            record(reason, definitions) { restarter(reason, roots) }
        }
        nowJob.getAndSet(job)?.cancel()
    }

    private suspend fun record(reason: String, definitions: DefinitionModelService?, onRecorded: () -> Unit = {}, restart: suspend () -> Unit) {
        // Read before the restart: the daemon runs after it, so it never sees an older state than this one.
        val definition = definitions?.state?.value
        restart()
        // A restart with nothing left to suspend on returns even when a newer change cancelled this draw: it must not leave a record.
        currentCoroutineContext().ensureActive()
        // The service may have been replaced while the restart ran: a record of a world that is gone would mislead whoever waits.
        if (definitions != null && followed.get()?.definitions !== definitions) return
        onRecorded()
        // A setting's draw and a definition's draw can record at the same moment: taking the number and appending it must be one step, or the history goes 6, 5.
        synchronized(recordLock) {
            val redraw = Redraw(seq.incrementAndGet(), reason, definitions, definition)
            history.updateAndGet { (it + redraw).takeLast(HISTORY_SIZE) }
            _lastRedraw.value = redraw
        }
    }

    private fun rootsOf(definitions: DefinitionModelService): List<Path> =
        definitions.definitions.map { it.definition?.projectRoot ?: it.module.linkedRootPath }.distinct()

    private fun schedule(reason: String, definitions: DefinitionModelService) {
        val job = scope.launch {
            delay(SETTLE_MILLIS)
            // The service this draw was for may have been replaced while it waited: its world is gone, so is the draw.
            if (followed.get()?.definitions !== definitions) return@launch
            val now = rootsOf(definitions)
            // Before and after: a definition that went away still has ◆ and menus on screen. With no root in either, any open file may have one.
            // The roots of the last draw that was recorded: one cancelled on the way leaves them for the next draw to cover.
            val roots = (lastRoots.get() + now).distinct()
            record(reason, definitions, onRecorded = { lastRoots.set(now) }) { restarter(reason, roots) }
        }
        pending.getAndSet(job)?.cancel()
    }

    private suspend fun restartOpenFilesUnder(reason: String, roots: List<Path>) {
        val open = sdkCall("list the open files to draw the katachi gutter again") {
            if (project.isDisposed) emptyList() else FileEditorManager.getInstance(project).openFiles.toList()
        }.getOrDefault(emptyList())
        for (file in open) {
            // The banner of a mistake of the definition is not drawn by the daemon: the platform recomputes it when asked.
            // Every draw asks, whatever its reason: two changes that settle together leave the reason of the later one only (and
            // that one may be the definition's state, not its mistakes), and a banner that is asked for needlessly costs one provider call.
            sdkCall("update the notifications of ${file.path}") { if (!project.isDisposed) EditorNotifications.getInstance(project).updateNotifications(file) }
            val path = sdkCall("find the path of ${file.name}") { file.fileSystem.getNioPath(file) }.getOrNull() ?: continue
            if (roots.isNotEmpty() && roots.none { path.startsWith(it) }) continue
            val psiFile = sdkCall("find the PSI of ${file.path}") { readAction { PsiManager.getInstance(project).findFile(file) } }.getOrNull()
                ?: continue
            // restart(PsiFile) reads the PSI, which the platform asserts is done inside a read action.
            sdkCall("draw the katachi gutter of ${file.path} again") {
                readAction { if (!project.isDisposed) DaemonCodeAnalyzer.getInstance(project).restart(psiFile, "katachi: $reason") }
            }
        }
    }

    companion object {
        const val SETTLE_MILLIS: Long = 300L

        private const val HISTORY_SIZE = 64

        fun getInstance(project: Project): HighlightingRefresher = project.service()
    }
}
