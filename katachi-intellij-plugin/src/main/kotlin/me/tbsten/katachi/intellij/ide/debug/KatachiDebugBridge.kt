package me.tbsten.katachi.intellij.ide.debug

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import me.tbsten.katachi.intellij.ide.KatachiProjectService
import me.tbsten.katachi.intellij.ide.dialog.GenerateDialogs
import me.tbsten.katachi.intellij.presentation.FieldId
import me.tbsten.katachi.intellij.presentation.GenerationState
import me.tbsten.katachi.intellij.presentation.KatachiIntent
import me.tbsten.katachi.intellij.presentation.KatachiToolWindowViewModel

/**
 * A String-only door into the tool window's ViewModel for the Driver smoke (`integrationTest`).
 *
 * The screen is one `ComposePanel`, whose rows and fields Driver's Swing tree cannot see, so the
 * smoke opens the tool window through the IDE and then drives the same intents a click would send
 * through this service (Driver `@Remote` calls can only pass plain values). Not used by the plugin,
 * and refuses to act unless the IDE runs with `-Dkatachi.debugBridge=true`, which only the smoke sets.
 *
 * Example, from a Driver test:
 * ```kotlin
 * val bridge = driver.service(KatachiDebugBridgeRef::class, project)
 * bridge.checkAndFill("domain.Service", "name=Probe")
 * bridge.generate()
 * ```
 */
@Service(Service.Level.PROJECT)
internal class KatachiDebugBridge(private val project: Project) {
    private val viewModel: KatachiToolWindowViewModel
        get() {
            check(System.getProperty(ENABLED_PROPERTY) == "true") { "The katachi debug bridge is off; start the IDE with -D$ENABLED_PROPERTY=true." }
            return KatachiProjectService.getInstance(project).viewModel
        }

    /**
     * The state as `key=value` lines: `phase`, one `module` per definition module (its Gradle path),
     * one `template` per row (its complete specifier), `blocker` and
     * `generation` (`running`, or `finished` followed by one `result` line per template).
     */
    fun describeState(): String {
        val state = viewModel.state.value
        return buildList {
            add("phase=${state.phase.toString().take(PHASE_TEXT_LIMIT)}")
            state.modules.forEach { add("module=${it.gradlePath}") }
            state.rows.forEach { add("template=${it.template.template}") }
            state.generateBlocker?.let { add("blocker=$it") }
            when (val generation = state.generation) {
                null -> Unit
                is GenerationState.Running -> add("generation=running")
                is GenerationState.Finished -> {
                    add("generation=finished")
                    generation.report.items.forEach { item -> item.templateIds.forEach { add("result=${it.template}:${item.result}") } }
                }
            }
        }.joinToString("\n")
    }

    /**
     * The generate dialog on screen as `key=value` lines, or `dialog=none`: `dialog=open`, `title`, `template`
     * (its complete specifier), `definition`, one `field` per input (`name=value`), `target`, `canGenerate`,
     * `okEnabled` and `refusal`. Reads state only, so it works while the modal dialog blocks the EDT.
     */
    fun describeDialog(): String {
        check(System.getProperty(ENABLED_PROPERTY) == "true") { "The katachi debug bridge is off; start the IDE with -D$ENABLED_PROPERTY=true." }
        val dialog = GenerateDialogs.showing ?: return "dialog=none"
        val state = dialog.viewModel.state.value
        return buildList {
            add("dialog=open")
            add("title=${dialog.title}")
            add("template=${state.selected?.template?.template}")
            state.selectedDefinition?.let { id -> add("definition=${state.definitions.firstOrNull { it.id == id }?.gradlePath}") }
            state.fields.forEach { add("field=${it.name}=${it.value.orEmpty()}") }
            add("target=${state.targetPath}")
            add("canGenerate=${state.canGenerate}")
            add("okEnabled=${dialog.isOKActionEnabled}")
            dialog.refusal?.let { add("refusal=$it") }
        }.joinToString("\n")
    }

    /**
     * Checks the first row whose complete specifier is [template] and types [args] into its
     * fields, one `name=value` per line. `false` when no row has that specifier.
     */
    fun checkAndFill(template: String, args: String): Boolean {
        val row = viewModel.state.value.rows.firstOrNull { it.template.template == template } ?: return false
        val inputs = args.lines().filter { it.isNotBlank() }.map { line -> line.substringBefore('=') to line.substringAfter('=') }
        onEdt {
            viewModel.dispatch(KatachiIntent.ToggleCheck(row.id))
            inputs.forEach { (name, value) -> viewModel.dispatch(KatachiIntent.Input(FieldId(row.id, name), value)) }
        }
        return true
    }

    /** Presses Generate. */
    fun generate() = onEdt { viewModel.dispatch(KatachiIntent.Generate) }

    /** Presses "uncheck all", which also leaves the result of the last generation. */
    fun uncheckAll() = onEdt { viewModel.dispatch(KatachiIntent.UncheckAll) }

    private fun onEdt(block: () -> Unit) = ApplicationManager.getApplication().invokeAndWait(block)
}

/** The system property that turns the bridge on. */
private const val ENABLED_PROPERTY = "katachi.debugBridge"

/** A load error carries the whole Gradle output; the smoke only needs its start. */
private const val PHASE_TEXT_LIMIT = 2_000
