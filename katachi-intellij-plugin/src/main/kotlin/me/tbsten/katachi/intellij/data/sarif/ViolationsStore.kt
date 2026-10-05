package me.tbsten.katachi.intellij.data.sarif

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.tbsten.katachi.intellij.data.ProjectFileSystem
import me.tbsten.katachi.intellij.data.gradle.GradleRunListener
import me.tbsten.katachi.intellij.data.gradle.GradleRunOutcome
import me.tbsten.katachi.intellij.data.gradle.GradleRunRequest
import me.tbsten.katachi.intellij.data.gradle.GradleTaskInvocation
import me.tbsten.katachi.intellij.data.gradle.GradleTaskRunner
import me.tbsten.katachi.intellij.data.load.classifyGradleFailure
import me.tbsten.katachi.intellij.data.load.collecting
import me.tbsten.katachi.intellij.data.watch.FileChanges
import me.tbsten.katachi.intellij.data.watch.REPORTS
import me.tbsten.katachi.intellij.model.GradleFailure
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.ModuleId
import me.tbsten.katachi.intellij.model.ViolationModel
import me.tbsten.katachi.intellij.model.ViolationsState
import me.tbsten.katachi.intellij.model.answer
import java.nio.file.Path
import java.time.Instant

/**
 * The violations of the definition modules' last check, read from their SARIF files (design 4.7), and the
 * check run from the IDE. IntelliJ-free: `KatachiViolationsService` feeds it the file changes and exposes it.
 *
 * - [read] reads every module's SARIF files into one [ViolationsState.Ready]; none at all is
 *   [ViolationsState.NotRun]. A file cut off by a write still running is read again [retryDelayMillis]
 *   later, up to [retries] times, then [ViolationsState.Unreadable] keeps the previous answer.
 * - While [runCheck] runs a module's check, its SARIF files going away (the test task deletes the last
 *   report first) keep that module's previous results instead of flashing "not run".
 * - A change of the project after the read marks the answer stale; the results stay.
 *
 * ```kotlin
 * val store = ViolationsStore(scope, runner, NioProjectFileSystem, modules = { definitionModules })
 * store.runCheck(module.id).await()
 * val here = store.forPath(file)
 * ```
 */
internal class ViolationsStore(
    private val scope: CoroutineScope,
    private val runner: GradleTaskRunner,
    private val fileSystem: ProjectFileSystem,
    private val modules: () -> List<KatachiModule>,
    private val listSarif: (Path) -> List<Path> = ::sarifFilesOf,
    private val retryDelayMillis: Long = RETRY_DELAY_MILLIS,
    private val retries: Int = RETRIES,
    private val clock: () -> Instant = Instant::now,
    /**
     * A check ended (well, or with failing tests) but left no SARIF: `report { sarif = true }` is missing (decision U-4).
     * A check that failed to compile or never found its task does not say anything about the report: not here.
     */
    private val onSarifMissing: (KatachiModule) -> Unit = {},
    /**
     * Called before [module]'s SARIF files are listed, off the EDT. The IDE makes the VFS know the report
     * directories here, so that a report written by a terminal's Gradle reaches the file watcher (design 4.7).
     */
    private val beforeRead: (KatachiModule) -> Unit = {},
    /** Where an unexpected exception of a background read or check is reported (a WARN in the IDE log). */
    private val onError: (message: String, cause: Throwable) -> Unit = { _, _ -> },
) {
    private val mutableState = MutableStateFlow<ViolationsState>(ViolationsState.NotRun)
    val state: StateFlow<ViolationsState> = mutableState.asStateFlow()

    private val mutableRunning = MutableStateFlow<Set<ModuleId>>(emptySet())
    val runningChecks: StateFlow<Set<ModuleId>> = mutableRunning.asStateFlow()

    private val readLock = Mutex()
    private val checkLock = Any()
    private val checks = HashMap<ModuleId, Deferred<GradleRunOutcome>>()

    @Volatile private var index: Pair<ViolationsState.Ready, Map<Path, List<ViolationModel>>>? = null

    /**
     * Reads the SARIF files again, in the background. Whatever goes wrong (a report directory deleted while it is
     * listed, a parser surprise) ends as [ViolationsState.Unreadable] with the previous answer, never as an exception of [scope].
     */
    fun refresh(): Job = scope.launch {
        try {
            read()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onError("Reading the katachi SARIF reports failed unexpectedly", e)
            val previous = mutableState.value.answer
            val path = modules().firstOrNull()?.directory?.resolve(REPORTS) ?: Path.of(REPORTS)
            mutableState.value = ViolationsState.Unreadable(path, messageOf(e), previous)
        }
    }

    /** Reads every definition module's SARIF files now (no Gradle) and publishes the result. */
    suspend fun read() = readLock.withLock {
        val previous = mutableState.value.answer
        val running = mutableRunning.value
        val runs = mutableListOf<ViolationModel>()
        val paths = mutableListOf<Path>()
        var latest: Instant? = null
        var kept = false
        for (module in modules()) {
            beforeRead(module)
            val files = listSarif(module.directory)
            if (files.isEmpty() && module.id in running && previous != null) {
                runs += previous.runs.filter { it.module == module.id }
                paths += previous.paths.filter { it.startsWith(module.directory) }
                kept = true
                continue
            }
            for (file in files) {
                when (val read = readFile(file, module.id)) {
                    is FileRead.Read -> {
                        runs += read.violations
                        paths.add(file)
                        latest = listOfNotNull(latest, read.modifiedAt).maxOrNull()
                    }
                    FileRead.Gone -> Unit
                    is FileRead.Failed -> {
                        mutableState.value = ViolationsState.Unreadable(file, read.reason, previous)
                        return@withLock
                    }
                }
            }
        }
        mutableState.value = if (paths.isEmpty()) {
            ViolationsState.NotRun
        } else {
            val readAt = latest ?: previous?.readAt ?: clock()
            // The same files as before: what was stale stays stale.
            val stale = (kept || readAt == previous?.readAt) && previous?.stale == true
            ViolationsState.Ready(runs, readAt, stale, paths)
        }
    }

    /** What the file watcher saw (design 4.7): a new SARIF is read, any other change of the project makes the answer stale. */
    fun onFilesChanged(changes: FileChanges) {
        when {
            changes.sarifChanged.isNotEmpty() -> refresh()
            changes.projectChanged -> mutableState.update { current ->
                if (current is ViolationsState.Ready && !current.stale) current.copy(stale = true) else current
            }
        }
    }

    /** The violations whose `locations[0]` is [path]. */
    fun forPath(path: Path): List<ViolationModel> {
        val ready = mutableState.value.answer ?: return emptyList()
        val current = index?.takeIf { it.first === ready } ?: (ready to indexOf(ready.runs)).also { index = it }
        return current.second[path.normalize()].orEmpty()
    }

    fun forModule(module: ModuleId): List<ViolationModel> = mutableState.value.answer?.runs.orEmpty().filter { it.module == module }

    /**
     * Runs [module]'s `test` task ([CHECK_TASK]) through [runner], then reads the SARIF files. A second call
     * while it runs returns the same run. An unknown module fails without running Gradle.
     */
    fun runCheck(module: ModuleId): Deferred<GradleRunOutcome> = synchronized(checkLock) {
        checks[module]?.let { return it }
        val target = modules().firstOrNull { it.id == module } ?: return CompletableDeferred(GradleRunOutcome.Failed)
        mutableRunning.update { it + module }
        val run = scope.async(start = CoroutineStart.LAZY) { check(target) }
        checks[module] = run
        run.start()
        run
    }

    private suspend fun check(module: KatachiModule): GradleRunOutcome {
        val output = mutableListOf<String>()
        return try {
            val outcome = try {
                val request = GradleRunRequest(module.linkedRootPath, listOf(GradleTaskInvocation(module.taskPath(CHECK_TASK))))
                runner.run(request, collecting(output, object : GradleRunListener {}))
            } finally {
                synchronized(checkLock) { checks.remove(module.id) }
                mutableRunning.update { it - module.id }
            }
            read()
            if (leftNoReport(module, outcome, output)) onSarifMissing(module)
            outcome
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Nobody awaits a check started from the IDE: say it here, and end it as a failed one.
            onError("Running the katachi check of ${module.gradlePath} failed unexpectedly", e)
            GradleRunOutcome.Failed
        }
    }

    /**
     * Whether [module]'s check ended without a SARIF in a way that points at the missing `report { sarif = true }`: it
     * succeeded, or its tests failed (the common case: `assert()` fails on a violation). Not a compile error, a
     * missing task or a rejected command line.
     */
    private fun leftNoReport(module: KatachiModule, outcome: GradleRunOutcome, output: List<String>): Boolean {
        val reachedTests = when (outcome) {
            GradleRunOutcome.Succeeded -> true
            GradleRunOutcome.Failed -> classifyGradleFailure(output) is GradleFailure.Other
        }
        return reachedTests && listSarif(module.directory).isEmpty()
    }

    private suspend fun readFile(file: Path, module: ModuleId): FileRead {
        var attempt = 0
        while (true) {
            val text = fileSystem.readText(file) ?: return FileRead.Gone
            val modifiedAt = fileSystem.lastModified(file)
            try {
                return FileRead.Read(parseSarifJson(text, file, module), modifiedAt)
            } catch (e: KatachiMalformedSarifException) {
                // katachi writes the file in place: a read that races the write sees it cut off.
                if (attempt >= retries) return FileRead.Failed(e.message.orEmpty())
            } catch (e: KatachiSarifReadException) {
                return FileRead.Failed(e.message.orEmpty())
            }
            attempt++
            delay(retryDelayMillis)
        }
    }

    private fun indexOf(runs: List<ViolationModel>): Map<Path, List<ViolationModel>> =
        runs.mapNotNull { violation -> violation.location?.let { it.path.normalize() to violation } }.groupBy({ it.first }, { it.second })

    private sealed interface FileRead {
        data class Read(val violations: List<ViolationModel>, val modifiedAt: Instant?) : FileRead

        data object Gone : FileRead

        data class Failed(val reason: String) : FileRead
    }

    companion object {
        /** The task a check runs in the definition module: katachi's `assert()` runs in its tests. */
        const val CHECK_TASK: String = "test"
        const val RETRY_DELAY_MILLIS: Long = 300L
        const val RETRIES: Int = 3
    }
}

/** An unexpected exception as one line for the state's reason. */
private fun messageOf(e: Exception): String = listOfNotNull(e::class.simpleName, e.message).joinToString(": ")
