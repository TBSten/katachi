package me.tbsten.katachi.intellij.uitest.dialog

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import me.tbsten.katachi.intellij.data.generate.SingleFileGenerationRequest
import me.tbsten.katachi.intellij.data.generate.TargetState
import me.tbsten.katachi.intellij.data.placement.PathPattern
import me.tbsten.katachi.intellij.data.placement.TemplatePlacementIndex
import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.model.ParameterModel
import me.tbsten.katachi.intellij.presentation.FieldSlot
import me.tbsten.katachi.intellij.presentation.dialog.CaptureSeedPort
import me.tbsten.katachi.intellij.presentation.dialog.GenerateDialogIntent
import me.tbsten.katachi.intellij.presentation.dialog.GenerateDialogListNotice
import me.tbsten.katachi.intellij.presentation.dialog.GenerateDialogState
import me.tbsten.katachi.intellij.presentation.dialog.GenerateDialogViewModel
import me.tbsten.katachi.intellij.presentation.entry.EntryAvailability
import me.tbsten.katachi.intellij.presentation.entry.EntryOrigin
import me.tbsten.katachi.intellij.presentation.entry.GenerateDialogRequest
import me.tbsten.katachi.intellij.presentation.entry.NewMenuNode
import me.tbsten.katachi.intellij.presentation.entry.newMenuTreeOf
import me.tbsten.katachi.intellij.testing.ManualDispatcher
import me.tbsten.katachi.intellij.testing.snapshotOf
import me.tbsten.katachi.intellij.uitest.pbt.DialogOp
import me.tbsten.katachi.intellij.uitest.pbt.DialogScenario
import me.tbsten.katachi.intellij.uitest.pbt.DiskKind
import me.tbsten.katachi.intellij.uitest.pbt.OriginPick
import java.nio.file.Path

private val moduleValuePool = listOf("home", "settings", "user", "fuga-piyo", "日本語")
private val otherValuePool = listOf("Home", "profile", "日本語", "Ab1", "user", "fuga-piyo", "x")

/**
 * The real [GenerateDialogViewModel] played through [DialogOp]s on one thread: its scope and the
 * existing-file check each run on a [ManualDispatcher] that the machine drains after every step, so a
 * sequence always plays the same way and kotest can shrink it. The seeds come from the real
 * [TemplatePlacementIndex] built from the same list, the New menu's tree from the real tree, and the
 * pre-check is a fake over [disk] that records every call.
 *
 * After each step the state is checked by [stateProblemsOf], the step by [transitionProblemsOf], and
 * [Generate] by [generationProblemsOf]; the opening is checked against the file or the leaf it came from.
 *
 * ```kotlin
 * DialogMachine(scenario).use { it.run() }
 * ```
 */
internal class DialogMachine(private val scenario: DialogScenario) : AutoCloseable {
    private val edt = ManualDispatcher()
    private val background = ManualDispatcher()
    private val escaped = mutableListOf<Throwable>()
    private val dialogScope = CoroutineScope(edt + SupervisorJob() + CoroutineExceptionHandler { _, e -> escaped += e })
    private val definitions = scenario.world.definitions

    /** Per definition, the indexes of its pool that the shared list holds now, in list order. */
    private val visible: MutableList<List<Int>> = definitions.map { it.pool.indices.toList() }.toMutableList()
    private var index: TemplatePlacementIndex = indexOfVisible()
    private val disk = mutableMapOf<Path, DiskKind>()
    private val checks = mutableListOf<Path?>()
    private val trace = mutableListOf<String>()
    private var cancelled = false
    private var checksAtCancel = 0
    private var lastShown: LastShown? = null

    // Where the dialog was opened from; set by openingRequest() in init, so declared above it.
    private var fileSegments: List<String> = emptyList()
    private var rootOfOrigin: Path? = null
    private var leafRemaining: String? = null
    private var directorySegments: List<String> = emptyList()

    /** What the steps reached, for the coverage report. */
    val reached: MutableMap<String, Int> = sortedMapOf()

    private val origin: EntryOrigin
    private val openingProblems = mutableListOf<String>()
    private val viewModel: GenerateDialogViewModel

    init {
        val request = openingRequest()
        origin = request.origin
        viewModel = GenerateDialogViewModel(
            scope = dialogScope,
            request = request,
            candidates = listedRows(),
            seeds = CaptureSeedPort { from, template -> index.seedsFor(from, template) },
            checkTarget = { target ->
                checks += target
                target?.let { answerOf(disk[it] ?: DiskKind.Missing) } ?: TargetState.Absent
            },
            rootOf = { it.linkedRootPath },
            checkContext = background,
            settle = {},
        )
        drain()
        checkOpening(request)
        verify("opened", null, null)
    }

    private fun indexOfVisible(): TemplatePlacementIndex = TemplatePlacementIndex.build(
        definitions.mapIndexed { d, def -> snapshotOf(def.module, visible[d].map { def.pool[it] }) },
    ) { it.linkedRootPath }

    private fun listedRows(): List<ModuleTemplate> = definitions.flatMapIndexed { d, def -> visible[d].map { ModuleTemplate(def.module, def.pool[it]) } }

    private fun view(state: GenerateDialogState = viewModel.state.value) = DialogView(state, listedRows(), index, origin, disk.toMap(), cancelled)

    private fun drain() {
        while (edt.pending + background.pending > 0) {
            edt.runAll()
            background.runAll()
        }
    }

    // ---- opening ----

    /** The file of a notification or the leaf of a New menu that the pick names, computed from the pool and the real index. */
    private fun openingRequest(): GenerateDialogRequest {
        val pick = scenario.origin
        val candidates = definitions.flatMapIndexed { d, def ->
            def.pool.filter { it.isAvailable }.mapNotNull { model ->
                val pattern = model.detail?.files?.firstOrNull()?.pattern ?: return@mapNotNull null
                if (PathPattern.parse(pattern) == null) null else Triple(d, model, pattern)
            }
        }
        val (d, _, pattern) = candidates[pick.template % candidates.size]
        val root = definitions[d].module.linkedRootPath
        val segments = filledSegmentsOf(pattern, valuesFor(pattern, pick.values, moduleValuePool, otherValuePool), pick.form)
        fileSegments = segments
        rootOfOrigin = root
        return if (pick.editor) editorRequest(root, segments) else menuRequest(root, segments, pick)
    }

    private fun editorRequest(root: Path, segments: List<String>): GenerateDialogRequest {
        val file = segments.fold(root) { path, segment -> path.resolve(segment) }
        val first = index.matchesForFile(file).firstOrNull()
        if (first == null) openingProblems += "the file $file of a template matches no template"
        val origin = EntryOrigin.EditorFile(file)
        return GenerateDialogRequest(origin, first?.id ?: listedRows().first().id, first?.decided.orEmpty())
    }

    private fun menuRequest(root: Path, segments: List<String>, pick: OriginPick): GenerateDialogRequest {
        val depth = pick.depth % segments.size
        directorySegments = segments.take(depth)
        val directory = directorySegments.fold(root) { path, segment -> path.resolve(segment) }
        val leaves = leavesOf(newMenuTreeOf(EntryAvailability.Ready, index.matchesForDirectory(directory)))
        if (leaves.isEmpty()) openingProblems += "the directory $directory offers no template in the New menu"
        val leaf = leaves.getOrNull(pick.leaf % leaves.size.coerceAtLeast(1))
        leafRemaining = leaf?.match?.remainingPath
        return GenerateDialogRequest(EntryOrigin.NewMenuDirectory(directory), leaf?.match?.id ?: listedRows().first().id, leaf?.match?.decided.orEmpty())
    }

    private fun leavesOf(nodes: List<NewMenuNode>): List<NewMenuNode.Template> = nodes.flatMap {
        when (it) {
            is NewMenuNode.Definition -> leavesOf(it.children)
            is NewMenuNode.Group -> leavesOf(it.children)
            is NewMenuNode.Role -> leavesOf(it.children)
            is NewMenuNode.Template -> listOf(it)
            NewMenuNode.Loading -> emptyList()
        }
    }

    /** A notification opens on its own file; the New menu on a leaf's remaining path (issue 1, decision 21). */
    private fun checkOpening(request: GenerateDialogRequest) {
        val s = viewModel.state.value
        val problems = openingProblems.toMutableList()
        if (s.selectedTemplate != request.initialTemplate) problems += "opened on ${s.selectedTemplate.template}, not ${request.initialTemplate.template}"
        val unfilled = s.fields.filter { it.seeded && it.value != request.seeds[it.name] }
        if (unfilled.isNotEmpty()) problems += "seeded fields differ from the origin's: ${unfilled.map { it.name }}"
        if (s.fields.count { it.seeded } != request.seeds.size) problems += "${s.fields.count { it.seeded }} seeded fields for ${request.seeds}"
        if (scenario.origin.editor) {
            // Every capture of the file is an initial value, so the sample path is the file itself (decision 21).
            val file = (origin as EntryOrigin.EditorFile).path
            val relative = rootOfOrigin?.relativize(file)?.map { it.toString() }?.joinToString("/")
            if (s.targetPath != relative) problems += "the notification's sample path '${s.targetPath}' != the file '$relative'"
        } else {
            val prefix = directorySegments.joinToString("/")
            val expected = listOf(prefix, leafRemaining.orEmpty()).filter { it.isNotEmpty() }.joinToString("/")
            if (asMenuSpelling(s.targetPath) != expected) problems += "the New menu leaf's sample path '${asMenuSpelling(s.targetPath)}' != the directory plus the remaining path '$expected'"
        }
        if (problems.isNotEmpty()) fail("opening", problems)
    }

    // ---- steps ----

    fun run() = scenario.ops.forEach(::step)

    fun step(op: DialogOp) {
        val before = view()
        val b = before.state
        var typed: Pair<String, String>? = null
        var request: Pair<SingleFileGenerationRequest?, SingleFileGenerationRequest?>? = null
        trace += op.toString()
        count("op ${op::class.simpleName}")
        when (op) {
            is DialogOp.SelectTemplate -> b.candidates.takeIf { it.isNotEmpty() }?.let { viewModel.dispatch(GenerateDialogIntent.SelectTemplate(it[op.pick % it.size].id)) }
            is DialogOp.SelectDefinition -> b.definitions.takeIf { it.isNotEmpty() }?.let { viewModel.dispatch(GenerateDialogIntent.SelectDefinition(it[op.pick % it.size].id)) }
            is DialogOp.Type -> typed = input(b, op.field, op.text)
            is DialogOp.Clear -> typed = input(b, op.field, "")
            is DialogOp.Shrink -> changeList { d -> visible[d] = definitions[d].pool.indices.filter { Math.floorMod(op.salt * 31 + it * 17 + d * 5 + op.salt / 7, 3) != 0 } }
            DialogOp.Restore -> changeList { d -> visible[d] = definitions[d].pool.indices.toList() }
            is DialogOp.Rotate -> changeList { d -> visible[d] = visible[d].let { list -> if (list.isEmpty()) list else list.drop(op.by % list.size) + list.take(op.by % list.size) } }
            is DialogOp.ExternalWrite -> b.target?.let { target ->
                if (op.kind == DiskKind.Missing) disk.remove(target) else disk[target] = op.kind
                viewModel.dispatch(GenerateDialogIntent.FilesChangedOutside)
            }
            is DialogOp.Cancel -> cancel(op.mode, b)
            DialogOp.Generate -> request = viewModel.generationRequest() to viewModel.generationRequest()
        }
        drain()
        val after = view()
        val problems = mutableListOf<String>()
        if (escaped.isNotEmpty()) problems += "an exception escaped the dialog's scope: ${escaped.first()}"
        if (cancelled && checks.size != checksAtCancel) problems += "the target was checked after the dialog was cancelled"
        if (checks.any { it == null }) problems += "the pre-check was asked about no file"
        problems += transitionProblemsOf(op, before, after, lastShown, typed)
        if (request != null) {
            problems += generationProblemsOf(after, request.first, request.second)
            count(if (request.first == null) "generate refused" else "generate requested")
        }
        verify(op.toString(), problems, after)
    }

    private fun input(state: GenerateDialogState, field: Int, text: String): Pair<String, String>? {
        val target = state.fields.takeIf { it.isNotEmpty() }?.get(field % state.fields.size) ?: return null
        viewModel.dispatch(GenerateDialogIntent.Input(target.name, text))
        return target.name to text
    }

    private fun changeList(update: (Int) -> Unit) {
        definitions.indices.forEach(update)
        index = indexOfVisible()
        viewModel.dispatch(GenerateDialogIntent.ListChanged(listedRows()))
    }

    /** [mode] 0: at rest; 1: an input is queued; 2: the input reached the ViewModel and its check is queued in the background. */
    private fun cancel(mode: Int, state: GenerateDialogState) {
        checksAtCancel = checks.size
        if (mode >= 1) state.fields.firstOrNull()?.let { viewModel.dispatch(GenerateDialogIntent.Input(it.name, "Cancelled${mode}")) }
        if (mode == 2) edt.runAll()
        dialogScope.cancel()
        cancelled = true
    }

    private fun verify(step: String, extra: List<String>?, after: DialogView?) {
        val current = after ?: view()
        val problems = (extra ?: emptyList()) + stateProblemsOf(current)
        current.selected?.let { lastShown = LastShown(it, current.values) }
        if (problems.isNotEmpty()) fail(step, problems)
        recordReach(current)
    }

    private fun fail(step: String, problems: List<String>): Nothing {
        val s = viewModel.state.value
        throw AssertionError(
            buildString {
                appendLine("after $step:")
                problems.forEach { appendLine("  - $it") }
                appendLine("state: selected=${s.selectedTemplate.template} fields=${s.fields.map { "${it.name}=${it.value}" }} path='${s.targetPath}' notice=${s.targetNotice} canGenerate=${s.canGenerate} listNotice=${s.listNotice}")
                appendLine("origin: ${scenario.origin} -> $origin")
                appendLine("steps: ${trace.joinToString(" ; ")}")
            },
        )
    }

    private fun count(key: String) {
        reached.merge(key, 1, Int::plus)
    }

    private fun recordReach(v: DialogView) {
        val s = v.state
        if (s.definitions.size >= 2) count("two definitions")
        if (s.candidates.size == 1) count("one candidate")
        if (s.listNotice is GenerateDialogListNotice.NoCandidates) count("no candidates")
        if (s.listNotice is GenerateDialogListNotice.TemplateReplaced) count("template replaced")
        s.targetNotice?.let { count("notice $it") }
        if (s.fields.any { it.seeded }) count("seeded")
        if (s.fields.size >= 5) count("many fields")
        if (s.fields.none { it.slot is FieldSlot.Shown && it.slot.parameter !is ParameterModel.CaptureParam }) count("captures only")
        if (s.fields.any { it.slot is FieldSlot.Collapsed }) count("collapsed")
        if (s.canGenerate) count("can generate")
        if (cancelled) count("cancelled")
    }

    override fun close() {
        dialogScope.cancel()
    }
}
