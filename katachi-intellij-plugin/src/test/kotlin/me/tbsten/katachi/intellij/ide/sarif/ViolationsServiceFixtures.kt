package me.tbsten.katachi.intellij.ide.sarif

import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.tbsten.katachi.intellij.data.gradle.GradleRunOutcome
import me.tbsten.katachi.intellij.model.ModuleId
import me.tbsten.katachi.intellij.model.ViolationModel
import me.tbsten.katachi.intellij.model.ViolationsState
import me.tbsten.katachi.intellij.model.answer
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A [ViolationsService] with no SARIF and no Gradle behind it, for the headless tests of the two
 * features. The test scripts the state and the answers, and decides when a check "finishes".
 *
 * `forFile` answers [answer] when scripted for the path, else the results of the state whose
 * `locations[0]` is that file; either way nothing while the state has no answer (`NotRun`, or
 * `Unreadable` with no previous). [runCheck] starts nothing: it records the call, adds the module to
 * [runningChecks], and returns a deferred that [finishCheck] completes, so a test holds "checking..." for
 * as long as it wants. A second call for a running module returns the same deferred, as the real one does.
 *
 * ## A state sequence
 * As for the definition's fake: [emit] sets the state, [script] / [step] set it one at a time, [play]
 * sets each and calls back after it.
 *
 * ```kotlin
 * val violations = FakeViolationsService()
 * violations.play(ViolationsStateScripts.standardCycle(module, path)) { state -> assertMarkers(state) }
 * violations.runCheck(module)            // runningChecks = {module}
 * violations.finishCheck(module)         // and the deferred completes
 * ```
 *
 * This class sits in a `*Fixtures.kt` file, which is what the test layout calls what tests share, and not in `testing/`:
 * `uiTest` compiles `testing/` without the IntelliJ classes this needs (`VirtualFile`).
 */
internal class FakeViolationsService(initial: ViolationsState = ViolationsState.NotRun) : ViolationsService {
    private val mutableState = MutableStateFlow(initial)
    override val state: StateFlow<ViolationsState> = mutableState.asStateFlow()

    private val mutableRunning = MutableStateFlow<Set<ModuleId>>(emptySet())
    override val runningChecks: StateFlow<Set<ModuleId>> = mutableRunning.asStateFlow()

    private val answers = ConcurrentHashMap<String, List<ViolationModel>>()
    private val checks = ConcurrentHashMap<ModuleId, CompletableDeferred<GradleRunOutcome>>()
    private val queued = ArrayDeque<ViolationsState>()
    private val mutableHistory = CopyOnWriteArrayList<ViolationsState>().apply { add(initial) }
    private val mutableRunChecks = CopyOnWriteArrayList<ModuleId>()

    /** Every state the service has been in, the initial one first. */
    val history: List<ViolationsState> get() = mutableHistory.toList()

    /** The modules [runCheck] was called with, in order (a repeated call for a running module is listed too). */
    val runCheckCalls: List<ModuleId> get() = mutableRunChecks.toList()

    /** How many times [refresh] was called. */
    @Volatile
    var refreshCalls: Int = 0
        private set

    // --- the state ---

    fun emit(state: ViolationsState) {
        if (mutableState.value == state) return
        mutableHistory += state
        mutableState.value = state
    }

    fun emit(vararg states: ViolationsState) = states.forEach { emit(it) }

    fun script(vararg states: ViolationsState) {
        queued.addAll(states)
    }

    /** Sets the next queued state. `false` when none is left. */
    fun step(): Boolean {
        val next = queued.removeFirstOrNull() ?: return false
        emit(next)
        return true
    }

    fun play(states: List<ViolationsState>, afterEach: (ViolationsState) -> Unit = {}) {
        for (next in states) {
            emit(next)
            afterEach(next)
        }
    }

    // --- scripted answers and checks ---

    /** What `forFile` / `forPath` answer for [path]. */
    fun answer(path: Path, violations: List<ViolationModel>) {
        answers[keyOf(path)] = violations
    }

    /**
     * Ends the check [runCheck] started for [module] with [outcome]: it is no longer running and its deferred
     * completes. Does nothing when none is running.
     */
    fun finishCheck(module: ModuleId, outcome: GradleRunOutcome = GradleRunOutcome.Succeeded) {
        val deferred = checks.remove(module) ?: return
        mutableRunning.value = mutableRunning.value - module
        deferred.complete(outcome)
    }

    // --- ViolationsService ---

    override fun forFile(file: VirtualFile): List<ViolationModel> = forKey(file.path)

    override fun forPath(path: Path): List<ViolationModel> = forKey(keyOf(path.normalize()))

    private fun forKey(key: String): List<ViolationModel> {
        val ready = state.value.answer ?: return emptyList()
        return answers[key] ?: ready.runs.filter { it.location?.path?.let(::keyOf) == key }
    }

    override fun forModule(module: ModuleId): List<ViolationModel> =
        state.value.answer?.runs.orEmpty().filter { it.module == module }

    override fun refresh() {
        refreshCalls++
    }

    override fun runCheck(module: ModuleId): Deferred<GradleRunOutcome> {
        mutableRunChecks += module
        val started = checks.computeIfAbsent(module) { CompletableDeferred() }
        mutableRunning.value = mutableRunning.value + module
        return started
    }

    private fun keyOf(path: Path): String = path.toString().replace('\\', '/')
}
