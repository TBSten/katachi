package me.tbsten.katachi.intellij.testing

import kotlinx.coroutines.CompletableDeferred
import me.tbsten.katachi.intellij.data.ProjectFileSystem
import me.tbsten.katachi.intellij.data.generate.OpenAfterGeneration
import me.tbsten.katachi.intellij.data.gradle.GradleRunListener
import me.tbsten.katachi.intellij.data.gradle.GradleRunOutcome
import me.tbsten.katachi.intellij.data.gradle.GradleRunRequest
import me.tbsten.katachi.intellij.data.gradle.GradleTaskRunner
import me.tbsten.katachi.intellij.model.ConflictChoice
import me.tbsten.katachi.intellij.model.ConflictQuestion
import me.tbsten.katachi.intellij.model.GenerationReport
import me.tbsten.katachi.intellij.presentation.DocsPage
import me.tbsten.katachi.intellij.presentation.IdeEffects
import java.nio.file.Path
import java.time.Instant
import java.util.Collections

/** What one fake Gradle run prints and how it ends. */
internal data class FakeRun(
    val lines: List<String> = emptyList(),
    val outcome: GradleRunOutcome = GradleRunOutcome.Succeeded,
    val tasks: List<String> = emptyList(),
    /** Files the run "writes" into the fake file system, with their text. */
    val writes: Map<Path, String> = emptyMap(),
    /** When set, the run suspends on it after printing, so a test can cancel mid-run. */
    val gate: CompletableDeferred<Unit>? = null,
)

/**
 * A [GradleTaskRunner] that answers each request with the next [FakeRun] from [answer] and records
 * the requests. Suspends on [FakeRun.gate] so cancellation can be tested without timing.
 */
internal class FakeGradleTaskRunner(
    private val fileSystem: FakeFileSystem? = null,
    private val answer: (GradleRunRequest, Int) -> FakeRun,
) : GradleTaskRunner {
    val requests: MutableList<GradleRunRequest> = Collections.synchronizedList(mutableListOf())
    var cancelled: Int = 0
        private set

    /** Completes when a run reaches its gate: the test knows the build is "running". */
    var reachedGate: CompletableDeferred<Unit> = CompletableDeferred()

    override suspend fun run(request: GradleRunRequest, listener: GradleRunListener): GradleRunOutcome {
        val run = answer(request, requests.size)
        requests += request
        run.tasks.forEach(listener::onTask)
        run.lines.forEach(listener::onLine)
        run.writes.forEach { (path, text) -> fileSystem?.write(path, text) }
        val gate = run.gate
        if (gate != null) {
            reachedGate.complete(Unit)
            try {
                gate.await()
            } catch (e: kotlinx.coroutines.CancellationException) {
                cancelled++
                throw e
            }
        }
        return run.outcome
    }
}

/** An in-memory [ProjectFileSystem]. */
internal class FakeFileSystem : ProjectFileSystem {
    private val files = Collections.synchronizedMap(mutableMapOf<Path, Pair<String, Instant>>())

    fun write(path: Path, text: String, at: Instant = Instant.parse("2026-09-27T00:00:00Z")) {
        files[path] = text to at
    }

    fun delete(path: Path) {
        files.remove(path)
    }

    override fun readText(path: Path): String? = files[path]?.first

    override fun lastModified(path: Path): Instant? = files[path]?.second

    override fun exists(path: Path): Boolean = path in files
}

/**
 * Records every effect in order; conflicts are answered by [conflictAnswer]. [labelPut] and
 * [openable] play an IDE that fails to put the label or to open a file.
 */
internal class FakeIdeEffects(
    var conflictAnswer: suspend (ConflictQuestion) -> ConflictChoice = { ConflictChoice.Stop },
    var openSetting: OpenAfterGeneration = OpenAfterGeneration.First,
    var labelPut: Boolean = true,
    var openable: (Path) -> Boolean = { true },
) : IdeEffects {
    val log: MutableList<String> = Collections.synchronizedList(mutableListOf())
    val opened: MutableList<Path> = Collections.synchronizedList(mutableListOf())
    val refreshed: MutableList<Path> = Collections.synchronizedList(mutableListOf())

    override suspend fun saveAllDocuments() {
        log += "save"
    }

    override suspend fun putLocalHistoryLabel(titles: List<String>): String? {
        log += "label"
        return "katachi: before generating (${titles.joinToString(", ")})".takeIf { labelPut }
    }

    override suspend fun refreshFiles(paths: List<Path>) {
        log += "refresh"
        refreshed += paths
    }

    override suspend fun openFiles(paths: List<Path>): List<Path> {
        log += "open"
        val openedNow = paths.filter(openable)
        opened += openedNow
        return openedNow
    }

    override suspend fun askConflict(question: ConflictQuestion): ConflictChoice {
        log += "conflict"
        return conflictAnswer(question)
    }

    override fun openAfterGeneration(): OpenAfterGeneration = openSetting

    override fun notifyGenerationFinished(report: GenerationReport) {
        log += "notifyGenerated"
    }

    override fun notifyLoadFailed() {
        log += "notifyLoadFailed"
    }

    override fun showLog() {
        log += "showLog"
    }

    override fun syncGradle() {
        log += "sync"
    }

    override fun openDocs(page: DocsPage) {
        log += "docs:$page"
    }

    val clipboard: MutableList<String> = Collections.synchronizedList(mutableListOf())

    override fun copyToClipboard(text: String) {
        clipboard += text
    }
}
