package me.tbsten.katachi.intellij.testing

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import me.tbsten.katachi.intellij.data.detect.SyncedModule
import me.tbsten.katachi.intellij.data.detect.SyncedProject
import me.tbsten.katachi.intellij.data.detect.SyncedRoot
import me.tbsten.katachi.intellij.data.gradle.GradleRunOutcome
import me.tbsten.katachi.intellij.data.gradle.GradleRunRequest
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.presentation.BodyUi
import me.tbsten.katachi.intellij.presentation.FieldId
import me.tbsten.katachi.intellij.presentation.FieldUi
import me.tbsten.katachi.intellij.presentation.FooterUi
import me.tbsten.katachi.intellij.presentation.FormUi
import me.tbsten.katachi.intellij.presentation.GenerationState
import me.tbsten.katachi.intellij.presentation.JapaneseKatachiStrings
import me.tbsten.katachi.intellij.presentation.KatachiIntent
import me.tbsten.katachi.intellij.presentation.KatachiScreenState
import me.tbsten.katachi.intellij.presentation.KatachiToolWindowViewModel
import me.tbsten.katachi.intellij.presentation.ListItemUi
import me.tbsten.katachi.intellij.presentation.ListUi
import me.tbsten.katachi.intellij.presentation.RowBodyUi
import me.tbsten.katachi.intellij.presentation.RowResultUi
import me.tbsten.katachi.intellij.presentation.ScreenPhase
import me.tbsten.katachi.intellij.presentation.TemplateRowUi
import me.tbsten.katachi.intellij.presentation.uiStateOf
import java.time.Instant

/**
 * A [KatachiToolWindowViewModel] wired to fakes for driving it through a sequence of operations:
 * loads answer with [loadJson] (after [loadGate]), generations are played by [katachi] against
 * [fs], so files written by one generation are there for the next. [ui] is what the Composables
 * would be handed at each step.
 *
 * ```kotlin
 * val s = ScenarioHarness(this)
 * s.open()
 * s.check(s.repository); s.input(s.repository, "name", "User")
 * val result = s.generate()
 * ```
 */
internal class ScenarioHarness(scope: CoroutineScope, ioDispatcher: CoroutineDispatcher = Dispatchers.IO) {
    val fs = FakeFileSystem()
    val effects = FakeIdeEffects()
    val arch = module(":arch-a")
    val repository = TemplateId(arch.id, "data/Repository")
    val useCase = TemplateId(arch.id, "domain/UseCase")
    val noArgs = TemplateId(arch.id, "misc/NoArgs")
    val label = TemplateId(arch.id, "misc/Label")
    val dataDir = ROOT.resolve("data/src/main/kotlin/com/example/data")
    val domainDir = ROOT.resolve("domain/src/main/kotlin/com/example/domain")

    var synced: SyncedProject = SyncedProject.Synced(
        listOf(SyncedRoot(ROOT, "project", listOf(SyncedModule(":arch-a", arch.directory, setOf("katachiInternalTemplatesJson"), "0.3.0")))),
    )
    var loadJson: String = ContractFixtures.json("arch-a")
    /** The JSON each other definition module's load writes (a module added by a later sync). */
    val otherJson: MutableMap<KatachiModule, String> = mutableMapOf()
    var loadGate: CompletableDeferred<Unit>? = null
    /** Replaces how a load answers when set: what it writes, how it ends, whether it waits. */
    var loadOverride: (() -> FakeRun)? = null
    /** What "show cause" (`katachiTemplates`) prints. */
    var causeLines: List<String> = listOf("[FAILED] misc/Broken", "  Unresolved placeholder")
    var loads = 0
        private set

    lateinit var vm: KatachiToolWindowViewModel
        private set

    val katachi = FakeKatachi(fs, ROOT) { vm.state.value.rows }
    val runner = FakeGradleTaskRunner(fs) { request, _ -> answer(request) }

    init {
        vm = KatachiToolWindowViewModel(scope, { synced }, runner, fs, effects, ioDispatcher = ioDispatcher)
    }

    private fun answer(request: GradleRunRequest): FakeRun =
        if (request.taskNames.first().endsWith("katachiInternalTemplatesJson")) {
            loads++
            loadOverride?.invoke() ?: FakeRun(writes = mapOf(arch.templateDescriptionJson to loadJson) + otherJson.mapKeys { it.key.templateDescriptionJson }, gate = loadGate)
        } else if (request.taskNames.first().endsWith(":katachiTemplates")) {
            FakeRun(causeLines, GradleRunOutcome.Failed)
        } else {
            katachi.answer(request)
        }

    val state: KatachiScreenState get() = vm.state.value

    fun dispatch(vararg intents: KatachiIntent) = intents.forEach(vm::dispatch)

    suspend fun await(predicate: (KatachiScreenState) -> Boolean): KatachiScreenState =
        withTimeout(5_000) { vm.state.first(predicate) }

    /** Lets launched work run up to its next suspension, for "nothing else happened" checks. */
    suspend fun settle() = repeat(20) { yield() }

    suspend fun open(): KatachiScreenState {
        dispatch(KatachiIntent.Opened)
        return awaitIdle()
    }

    suspend fun awaitIdle(): KatachiScreenState = await { it.phase == ScreenPhase.Ready && it.loading == null }

    /**
     * Presses ⟳ with [json] as the new definition and waits until that load ended. Polls: a load
     * that changes nothing ends on a state equal to the one before, which a StateFlow never emits.
     */
    suspend fun reload(json: String = loadJson) {
        loadJson = json
        val before = loads
        dispatch(KatachiIntent.Reload)
        withTimeout(5_000) {
            while (loads == before || state.loading != null) delay(1)
        }
    }

    fun check(id: TemplateId) = dispatch(KatachiIntent.ToggleCheck(id))

    fun input(id: TemplateId, parameter: String, value: String) = dispatch(KatachiIntent.Input(FieldId(id, parameter), value))

    fun inputOf(id: TemplateId, parameter: String): String? = state.form.inputOf(FieldId(id, parameter))

    /** Presses Generate and waits for the result screen. */
    suspend fun generate(): GenerationState.Finished {
        dispatch(KatachiIntent.Generate)
        return finished()
    }

    suspend fun finished(): GenerationState.Finished = await { it.generation is GenerationState.Finished }.generation.cast()

    fun ui(): ListUi = uiStateOf(state, JapaneseKatachiStrings, NOW).body.cast<BodyUi.Listing>().list

    fun row(id: TemplateId): TemplateRowUi = ui().items.firstNotNullOf { (it as? ListItemUi.Row)?.row?.takeIf { row -> row.id == id } }

    fun rowIds(): List<TemplateId> = ui().items.mapNotNull { (it as? ListItemUi.Row)?.row?.id }

    fun formFooter(): FooterUi.Form = ui().footer.cast()

    fun resultFooter(): FooterUi.Result = ui().footer.cast()

    fun form(id: TemplateId): FormUi = row(id).body.cast<RowBodyUi.Form>().form

    fun result(id: TemplateId): RowResultUi = row(id).body.cast<RowBodyUi.Result>().result

    /** Makes [modules] the definition modules of the synced data, in this order. */
    fun syncModules(modules: List<KatachiModule>) {
        val root = (synced as SyncedProject.Synced).roots.single()
        val synced = modules.map { SyncedModule(it.gradlePath, it.directory, setOf("katachiInternalTemplatesJson"), "0.3.0") }
        this.synced = SyncedProject.Synced(listOf(root.copy(modules = synced)))
    }

    /** Makes [module] a definition module of the synced data, its load writing [json]. */
    fun addModule(module: KatachiModule, json: String) {
        val root = (synced as SyncedProject.Synced).roots.single()
        val added = SyncedModule(module.gradlePath, module.directory, setOf("katachiInternalTemplatesJson"), "0.3.0")
        synced = SyncedProject.Synced(listOf(root.copy(modules = root.modules + added)))
        otherJson[module] = json
    }

    fun textField(id: TemplateId, parameter: String): FieldUi.Text =
        form(id).fields.firstNotNullOf { (it as? FieldUi.Text)?.takeIf { field -> field.id.parameterName == parameter } }

    /** The `--arg`s of the latest `katachiTemplate` run. */
    fun lastArgs(): Map<String, String> = katachi.runs.last()

    companion object {
        val NOW: Instant = Instant.parse("2026-09-27T10:00:00Z")
    }
}
