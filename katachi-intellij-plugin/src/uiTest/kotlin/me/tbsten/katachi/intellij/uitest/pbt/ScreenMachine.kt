package me.tbsten.katachi.intellij.uitest.pbt

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import me.tbsten.katachi.intellij.data.gradle.GradleRunOutcome
import me.tbsten.katachi.intellij.data.placement.DirectoryLister
import me.tbsten.katachi.intellij.model.ConflictChoice
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.presentation.BodyUi
import me.tbsten.katachi.intellij.presentation.FieldId
import me.tbsten.katachi.intellij.presentation.FieldUi
import me.tbsten.katachi.intellij.presentation.GenerationState
import me.tbsten.katachi.intellij.presentation.KatachiIntent
import me.tbsten.katachi.intellij.presentation.KatachiScreenState
import me.tbsten.katachi.intellij.presentation.KatachiUiState
import me.tbsten.katachi.intellij.presentation.ListItemUi
import me.tbsten.katachi.intellij.presentation.RowBodyUi
import me.tbsten.katachi.intellij.presentation.shownParametersOf
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
import java.nio.file.Path

/**
 * Drives the real ViewModel (over [ScenarioHarness]'s fakes) through [Op]s on one thread, in a
 * fixed order: every coroutine runs on a [ManualDispatcher] that the machine drains after each
 * step, and every "slow" build or dialog waits on a gate that only [Op.Release] opens. The same
 * sequence therefore always plays the same way, which is what lets kotest shrink it.
 *
 * After each step it checks the invariants and, when [render] is set, draws the new screen at both
 * [DockSize]s as the tool window would, worded in [language].
 *
 * ```kotlin
 * ScreenMachine(catalog, render = true).use { it.run(ops) }
 * ```
 */
internal class ScreenMachine(
    private val catalog: Catalog,
    private val render: Boolean,
    private val language: ScreenLanguage = ScreenLanguage.Japanese,
) : AutoCloseable {
    private val dispatcher = ManualDispatcher()
    private val escaped = mutableListOf<Throwable>()
    private val scope = CoroutineScope(dispatcher + SupervisorJob() + CoroutineExceptionHandler { _, e -> escaped += e })
    private val candidateDisk = FakeDisk()

    /** While set, a candidate read waits on a gate of [readGates] before it reads ([Op.HoldCandidateReads] .. [Op.Release]). */
    private var holdingReads = false
    private val readGates = mutableListOf<CompletableDeferred<Unit>>()

    /**
     * What the read that runs now sees: a held read answers for the disk as it was when it was asked, not as it is when it
     * is let go (a real read takes time, and the disk moves meanwhile). `null`: the disk as it is.
     */
    private var readsSeen: Set<Path>? = null
    private val heldLister = DirectoryLister { directory ->
        (readsSeen ?: candidateDisk.dirs).filter { it.parent == directory }.map { it.fileName.toString() }
    }
    private val harness = ScenarioHarness(
        scope,
        ioDispatcher = dispatcher,
        candidateLister = heldLister,
        candidateSettle = {
            if (holdingReads) {
                val askedWhen = candidateDisk.dirs.toSet()
                val gate = CompletableDeferred<Unit>()
                readGates += gate
                reached.merge("candidate read held", 1, Int::plus)
                gate.await()
                // The read follows at once on the same dispatcher, before any other task can change this.
                readsSeen = askedWhen
            } else {
                readsSeen = null
            }
        },
    )
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
            is Op.Reveal -> listOf(KatachiIntent.RevealTemplate(revealTargetOf(op.pick, before, harness.arch)))
            is Op.NextLoad -> emptyList<KatachiIntent>().also { nextLoad = op.outcome }
            is Op.NextRun -> emptyList<KatachiIntent>().also { nextRun = op.outcome }
            is Op.ConflictAnswer -> emptyList<KatachiIntent>().also {
                conflictChoice = op.choice
                conflictWaits = op.waits
            }
            Op.HoldCandidateReads -> emptyList<KatachiIntent>().also { holdingReads = true }
            Op.Release -> emptyList<KatachiIntent>().also {
                gates.forEach { it.complete(Unit) }
                gates.clear()
                holdingReads = false
                if (readGates.size >= 2) reached.merge("several held candidate reads released", 1, Int::plus)
                // The newest question is answered first: whatever answers an older one afterwards is stale.
                readGates.asReversed().forEach { it.complete(Unit) }
                readGates.clear()
            }
            is Op.DiskChanged -> emptyList<KatachiIntent>().also {
                changeDisk(op, before)
                harness.vm.onFilesChanged()
            }
            else -> intentsOf(op, ui(before)).also {
                if (op is Op.PickCandidate && it.isNotEmpty()) reached.merge("candidate picked", 1, Int::plus)
                if (op is Op.TypeCapture && it.isNotEmpty()) reached.merge("capture typed", 1, Int::plus)
            }
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
        if (intents.any { it is KatachiIntent.RevealTemplate }) {
            if (before.searchQuery.isNotBlank() && after.searchQuery.isBlank()) reached.merge("[View template] cleared the search", 1, Int::plus)
            if ((before.view.collapsedModules - after.view.collapsedModules).isNotEmpty()) reached.merge("[View template] unfolded a module", 1, Int::plus)
        }
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
        val problems = found + uiViolationsOf(after, ui) + revealViolationsOf(after, intents, ui) +
            // Held reads leave the candidates behind what is typed and on disk on purpose; once released they must catch up.
            (if (holdingReads) emptyList() else candidateViolationsOf(after, ui, candidateDisk)) + focusViolationsOf(before, intents, after)
        reachCandidates(after, ui, intents)
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
        if (after.view.highlight != null && after.rows.any { it.id == after.view.highlight.templateId }) count("highlighted row in the list")
    }

    /** Typing into a field of an editable form reaches the state as typed. */
    private fun typedViolationOf(before: KatachiScreenState, intents: List<KatachiIntent>, after: KatachiScreenState): String? {
        if (before.generation != null) return null
        val input = intents.singleOrNull() as? KatachiIntent.Input ?: return null
        val now = after.form.inputOf(input.field)
        return if (now == input.value) null else "typed \"${input.value}\" into ${input.field.parameterName}, the state holds \"$now\""
    }

    /**
     * A directory appears or goes under the directory above one of the capture fields of the list (their levels above made
     * from the values typed so far), or nothing when no field has one yet.
     */
    private fun changeDisk(op: Op.DiskChanged, state: KatachiScreenState) {
        val parents = candidateParentsOf(state)
        val (parent, root) = parents.getOrNull(op.pick % parents.size.coerceAtLeast(1)) ?: return
        if (op.add) {
            candidateDisk.add(parent.resolve(DISK_NAMES[op.name % DISK_NAMES.size]), root)
            reached.merge("disk: directory added", 1, Int::plus)
        } else {
            val children = candidateDisk.childrenOf(parent).sorted()
            if (children.isEmpty()) return
            candidateDisk.remove(parent.resolve(children[op.name % children.size]))
            reached.merge("disk: directory removed", 1, Int::plus)
        }
    }

    private fun reachCandidates(state: KatachiScreenState, ui: KatachiUiState, intents: List<KatachiIntent>) {
        fun count(key: String) = reached.merge(key, 1, Int::plus)
        val fields = ((ui.body as? BodyUi.Listing)?.list?.items.orEmpty()).filterIsInstance<ListItemUi.Row>()
            .flatMap { (it.row.body as? RowBodyUi.Form)?.form?.fields.orEmpty() }.filterIsInstance<FieldUi.Text>()
        if (fields.any { it.suggestions != null }) count("candidates shown")
        if (fields.any { it.suggestions?.newDirectoryNote != null }) count("new-directory note shown")
        if (state.captureCandidates.isNotEmpty()) count("candidates in the state")
        if (intents.any { it is KatachiIntent.FocusSearch }) count("focus search")
        if (intents.any { it is KatachiIntent.ToggleModule }) count("module band toggled")
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
        for (specifier in specifiers) captureViolationOf(specifier, specifiers, args)?.let { runViolations += it }
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
     * Every capture of the running template reaches the run as `--arg`, and only a value that is one
     * directory level: whichever order the fields were filled in.
     *
     * The rows of one module run together and a run carries one value per name (design draft
     * section 6 "IDE の複数選択"), so the value is the field of the first row of the run, in list
     * order, that shows a field of that name: the template's own field, unless the user unlinked it
     * from a row above.
     */
    private fun captureViolationOf(template: String, specifiers: List<String>, args: Map<String, String>): String? {
        val runRows = harness.state.rows.filter {
            it.template.template in specifiers && it.module.taskPath(KatachiModule.TEMPLATE_TASK) == harness.katachi.currentTaskPath
        }
        val row = runRows.firstOrNull { it.template.template == template } ?: return null
        for (capture in row.template.detail?.captures.orEmpty()) {
            val sent = args[capture.name] ?: return "katachiTemplate ran for $template without its capture ${capture.name}: $args"
            val decides = runRows.firstOrNull { other ->
                val detail = other.template.detail ?: return@firstOrNull false
                shownParametersOf(detail, harness.state.form.inputsOf(other.id)).any { it.name == capture.name }
            } ?: row
            val typed = harness.state.form.inputOf(FieldId(decides.id, capture.name))
            if (sent != typed) {
                val whose = if (decides == row) "the field holds" else "the field of ${decides.template.template}, first in the run, holds"
                return "katachiTemplate ran for $template with ${capture.name}=\"$sent\", $whose \"$typed\""
            }
            if (sent.isBlank() || '/' in sent || '\\' in sent || sent.trim() == "." || sent.trim() == "..") {
                return "katachiTemplate ran for $template with ${capture.name}=\"$sent\", which is not one directory level"
            }
        }
        return null
    }

    private fun gate(): CompletableDeferred<Unit> = CompletableDeferred<Unit>().also { gates += it }

    private fun ui(state: KatachiScreenState): KatachiUiState = uiStateOf(state, language.strings, ScenarioHarness.NOW)

    private fun fail(problem: String, state: KatachiScreenState, ui: KatachiUiState?): Nothing {
        val picture = ui?.let { savePicture(it) }
        throw AssertionError(
            buildString {
                appendLine(problem)
                appendLine("in $language, after these steps:")
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
        readGates.forEach { it.complete(Unit) }
        scope.cancel()
    }
}
