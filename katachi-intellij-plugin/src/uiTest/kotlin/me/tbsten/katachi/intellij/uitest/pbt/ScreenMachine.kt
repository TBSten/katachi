package me.tbsten.katachi.intellij.uitest.pbt

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import me.tbsten.katachi.intellij.data.gradle.GradleRunOutcome
import me.tbsten.katachi.intellij.model.ConflictChoice
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.presentation.FieldId
import me.tbsten.katachi.intellij.presentation.GenerationState
import me.tbsten.katachi.intellij.presentation.JapaneseKatachiStrings
import me.tbsten.katachi.intellij.presentation.KatachiIntent
import me.tbsten.katachi.intellij.presentation.KatachiScreenState
import me.tbsten.katachi.intellij.presentation.KatachiUiState
import me.tbsten.katachi.intellij.presentation.uiStateOf
import me.tbsten.katachi.intellij.testing.ContractFixtures
import me.tbsten.katachi.intellij.testing.FakeRun
import me.tbsten.katachi.intellij.testing.ManualDispatcher
import me.tbsten.katachi.intellij.testing.ROOT
import me.tbsten.katachi.intellij.testing.ScenarioHarness
import me.tbsten.katachi.intellij.uitest.DockSize
import me.tbsten.katachi.intellij.uitest.renderToolWindow
import me.tbsten.katachi.intellij.uitest.writePng
import java.io.File

/**
 * Drives the real ViewModel (over [ScenarioHarness]'s fakes) through [Op]s on one thread, in a
 * fixed order: every coroutine runs on a [ManualDispatcher] that the machine drains after each
 * step, and every "slow" build or dialog waits on a gate that only [Op.Release] opens. The same
 * sequence therefore always plays the same way, which is what lets kotest shrink it.
 *
 * After each step it checks the invariants and, when [render] is set, draws the new screen at both
 * [DockSize]s as the tool window would.
 *
 * ```kotlin
 * ScreenMachine(catalog, render = true).use { it.run(ops) }
 * ```
 */
internal class ScreenMachine(private val catalog: Catalog, private val render: Boolean) : AutoCloseable {
    private val dispatcher = ManualDispatcher()
    private val escaped = mutableListOf<Throwable>()
    private val scope = CoroutineScope(dispatcher + SupervisorJob() + CoroutineExceptionHandler { _, e -> escaped += e })
    private val harness = ScenarioHarness(scope, ioDispatcher = dispatcher)

    /** The definition on disk: what the next load writes and the next sync reports. */
    private var disk: World = catalog.initial
    private var nextLoad = RunOutcome.Normal
    private var nextRun = RunOutcome.Normal
    private var conflictChoice = ConflictChoice.Stop
    private var conflictWaits = false
    private val gates = mutableListOf<CompletableDeferred<Unit>>()

    private val trace = mutableListOf<String>()
    private val runViolations = mutableListOf<String>()
    private var lastDrawn: KatachiUiState? = null

    /** `katachiTemplate` runs of the generation on screen, by template; reset when one starts. */
    private val runsOfGeneration = mutableMapOf<String, MutableList<String>>()

    /** What the steps reached, for the coverage report: counts by kind of step and of state. */
    val reached: MutableMap<String, Int> = sortedMapOf()

    /** Renders drawn, for the timing report. */
    var renders: Int = 0
        private set

    /** The first thing wrong in a state between two resumptions of this step, which no step boundary shows. */
    private var midStep: String? = null

    init {
        dispatcher.afterTask = {
            if (midStep == null) midStep = stateViolationsOf(harness.state).firstOrNull()?.let { "while the step ran: $it" }
        }
        harness.syncModules(disk.modules.map { it.module })
        harness.loadOverride = ::loadRun
        harness.katachi.override = { args, _ -> templateRun(args) }
        harness.effects.conflictAnswer = {
            val choice = conflictChoice
            if (conflictWaits) gate().await()
            choice
        }
        perform("Opened", listOf(KatachiIntent.Opened), before = harness.state)
    }

    fun run(ops: List<Op>) = ops.forEach(::step)

    fun step(op: Op) {
        val before = harness.state
        val intents = when (op) {
            is Op.Redefine -> {
                disk = catalog.worlds[op.world % catalog.worlds.size]
                emptyList()
            }
            Op.Sync -> {
                harness.syncModules(disk.modules.map { it.module })
                listOf(KatachiIntent.SyncCompleted)
            }
            Op.DefinitionChanged -> listOf(KatachiIntent.DefinitionChanged)
            is Op.NextLoad -> emptyList<KatachiIntent>().also { nextLoad = op.outcome }
            is Op.NextRun -> emptyList<KatachiIntent>().also { nextRun = op.outcome }
            is Op.ConflictAnswer -> emptyList<KatachiIntent>().also {
                conflictChoice = op.choice
                conflictWaits = op.waits
            }
            Op.Release -> emptyList<KatachiIntent>().also {
                gates.forEach { it.complete(Unit) }
                gates.clear()
            }
            else -> intentsOf(op, ui(before))
        }
        perform(op.toString(), intents, before)
    }

    private fun perform(label: String, intents: List<KatachiIntent>, before: KatachiScreenState) {
        trace += if (intents.isEmpty()) label else "$label -> ${intents.joinToString()}"
        if (before.generation !is GenerationState.Running && KatachiIntent.Generate in intents) runsOfGeneration.clear()
        intents.forEach(harness.vm::dispatch)
        dispatcher.runAll()
        val after = harness.state
        countReached(intents, after)
        val found = buildList {
            escaped.forEach { add("an exception escaped a coroutine: $it") }
            addAll(runViolations)
            midStep?.let { add(it) }
            addAll(stateViolationsOf(after))
            addAll(keptViolationsOf(before, intents, after))
            typedViolationOf(before, intents, after)?.let { add(it) }
        }
        val ui = try {
            ui(after)
        } catch (e: Exception) {
            fail("uiStateOf threw $e", after, null)
        }
        val problems = found + uiViolationsOf(after, ui)
        if (problems.isNotEmpty()) fail(problems.joinToString("\n  "), after, ui)
        if (render && ui != lastDrawn) {
            for (size in DockSize.entries) {
                try {
                    renderToolWindow(ui, size).close()
                    renders++
                } catch (e: Throwable) {
                    fail("drawing at ${size.name} (${size.width}x${size.height}) threw $e", after, null)
                }
            }
            lastDrawn = ui
        }
    }

    private fun countReached(intents: List<KatachiIntent>, after: KatachiScreenState) {
        fun count(key: String) = reached.merge(key, 1, Int::plus)
        intents.forEach { count("intent:${it::class.simpleName}") }
        count("phase:${after.phase::class.simpleName}")
        after.generation?.let { count("generation:${it::class.simpleName}") }
        (after.generation as? GenerationState.Running)?.let { running ->
            running.conflict?.let { count("conflict dialog") }
            if (running.waitingForLoad) count("generation waiting for a load")
        }
        (after.generation as? GenerationState.Finished)?.report?.items?.forEach { count("result:${it.result::class.simpleName}") }
        if (after.loading != null) count("loading")
        if (after.loadErrorBanner != null) count("load error banner")
        if (after.modules.size >= 2) count("two or more modules")
        if (after.searchQuery.isNotBlank()) count("searching")
    }

    /** Typing into a field of an editable form reaches the state as typed. */
    private fun typedViolationOf(before: KatachiScreenState, intents: List<KatachiIntent>, after: KatachiScreenState): String? {
        if (before.generation != null) return null
        val input = intents.singleOrNull() as? KatachiIntent.Input ?: return null
        val now = after.form.inputOf(input.field)
        return if (now == input.value) null else "typed \"${input.value}\" into ${input.field.parameterName}, the state holds \"$now\""
    }

    private fun loadRun(): FakeRun {
        val writes = disk.modules.associate { it.module.templateDescriptionJson to it.json }
        val outcome = nextLoad.also { nextLoad = RunOutcome.Normal }
        return when (outcome) {
            RunOutcome.Normal -> FakeRun(writes = writes)
            RunOutcome.Fails -> FakeRun(ContractFixtures.outputLines("compile-failure", ROOT), GradleRunOutcome.Failed)
            RunOutcome.Waits -> FakeRun(writes = writes, gate = gate())
        }
    }

    /**
     * A `katachiTemplate` run: only while a generation runs, only for its rows, and a template runs
     * a second time only to overwrite after the conflict dialog.
     */
    private fun templateRun(args: Map<String, String>): FakeRun? {
        // Several checked rows of the same module run together, `--arg template=` comma joined
        // (design draft section 6, "IDE の複数選択").
        val template = args.getValue("template")
        val specifiers = template.split(",")
        val running = harness.state.generation as? GenerationState.Running
        if (running == null) {
            runViolations += "katachiTemplate ran for $template without a generation on screen"
        } else {
            val runningSpecifiers = running.rows.map { it.template }.toSet()
            if (specifiers.any { it !in runningSpecifiers }) {
                runViolations += "katachiTemplate ran for $template, which is not among the generating rows ${running.rows.map { it.template }}"
            }
        }
        for (specifier in specifiers) captureViolationOf(specifier, args)?.let { runViolations += it }
        val runs = runsOfGeneration.getOrPut("${harness.katachi.currentTaskPath} $template") { mutableListOf() }
        runs += args.getValue("onExisting")
        if (runs.size > 2 || (runs.size == 2 && runs[1] != "overwrite")) {
            runViolations += "katachiTemplate ran for $template $runs in one generation"
        }
        val outcome = nextRun.also { nextRun = RunOutcome.Normal }
        return when (outcome) {
            RunOutcome.Normal -> null
            RunOutcome.Fails -> FakeRun(ContractFixtures.outputLines("unknown-arg", ROOT), GradleRunOutcome.Failed)
            RunOutcome.Waits -> FakeRun(gate = gate())
        }
    }

    /**
     * Every capture of the running template reaches the run as `--arg`, with the value its field
     * holds, and only a value that is one directory level: whichever order the fields were filled in.
     */
    private fun captureViolationOf(template: String, args: Map<String, String>): String? {
        val row = harness.state.rows.firstOrNull {
            it.template.template == template && it.module.taskPath(KatachiModule.TEMPLATE_TASK) == harness.katachi.currentTaskPath
        } ?: return null
        for (capture in row.template.detail?.captures.orEmpty()) {
            val sent = args[capture.name] ?: return "katachiTemplate ran for $template without its capture ${capture.name}: $args"
            val typed = harness.state.form.inputOf(FieldId(row.id, capture.name))
            if (sent != typed) return "katachiTemplate ran for $template with ${capture.name}=\"$sent\", the field holds \"$typed\""
            if (sent.isBlank() || '/' in sent || '\\' in sent || sent.trim() == "." || sent.trim() == "..") {
                return "katachiTemplate ran for $template with ${capture.name}=\"$sent\", which is not one directory level"
            }
        }
        return null
    }

    private fun gate(): CompletableDeferred<Unit> = CompletableDeferred<Unit>().also { gates += it }

    private fun ui(state: KatachiScreenState): KatachiUiState = uiStateOf(state, JapaneseKatachiStrings, ScenarioHarness.NOW)

    private fun fail(problem: String, state: KatachiScreenState, ui: KatachiUiState?): Nothing {
        val picture = ui?.let { savePicture(it) }
        throw AssertionError(
            buildString {
                appendLine(problem)
                appendLine("after these steps:")
                trace.forEachIndexed { index, step -> appendLine("  ${index + 1}. $step") }
                appendLine("state: phase=${state.phase} loading=${state.loading} generation=${state.generation}")
                appendLine("form: selected=${state.form.selected.map { it.template }} inputs=${state.form.inputs.mapKeys { it.key.template }}")
                picture?.let { appendLine("screen: $it") }
            },
        )
    }

    private fun savePicture(ui: KatachiUiState): String? = try {
        val dir = File(System.getProperty("katachi.pbt.outDir") ?: "build/uiTest-failures")
        val file = File(dir, "failure-${System.nanoTime()}.png")
        renderToolWindow(ui, DockSize.Narrow).writePng(file)
        file.path
    } catch (_: Throwable) {
        null
    }

    override fun close() {
        gates.forEach { it.complete(Unit) }
        scope.cancel()
    }
}
