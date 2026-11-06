package me.tbsten.katachi.intellij.data.generate

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import me.tbsten.katachi.intellij.data.ProjectFileSystem
import me.tbsten.katachi.intellij.data.gradle.GradleTaskRunner
import me.tbsten.katachi.intellij.model.ConflictChoice
import me.tbsten.katachi.intellij.model.GenerationReport
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.LoadFailure
import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.model.allParametersOf
import me.tbsten.katachi.intellij.model.templatesOf
import me.tbsten.katachi.intellij.presentation.FormState
import me.tbsten.katachi.intellij.presentation.GenerateBlocker
import me.tbsten.katachi.intellij.presentation.IdeEffects
import me.tbsten.katachi.intellij.presentation.OnExistingChoice
import me.tbsten.katachi.intellij.presentation.generationItemsOf
import me.tbsten.katachi.intellij.presentation.inputBlockersOf
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** [templates] are complete specifiers; [arguments] fill the parameters of every one of them alike. */
internal data class HeadlessGenerationRequest(
    val module: KatachiModule,
    val templates: List<String>,
    val arguments: Map<String, String>,
    val onExisting: OnExistingChoice,
)

internal sealed interface HeadlessGenerationResult {
    /** Another generation (the tool window's, an entry's or another headless one) is running. Nothing was read or written. */
    data object Busy : HeadlessGenerationResult

    data class ReloadFailed(val failure: LoadFailure) : HeadlessGenerationResult

    data class UnknownTemplates(val unknown: List<String>, val known: List<String>) : HeadlessGenerationResult

    /** What is wrong with the request, every problem at once. Gradle did not run. */
    data class Invalid(val problems: List<ArgumentProblem>) : HeadlessGenerationResult

    /** [timedOut]: the run was cancelled after the time limit; the items it did not finish are interrupted or not run. */
    data class Ran(val report: GenerationReport, val timedOut: Boolean) : HeadlessGenerationResult
}

internal sealed interface ArgumentProblem {
    /** A field of [template] as the tool window would refuse it. */
    data class Field(val template: String, val blocker: GenerateBlocker) : ArgumentProblem

    /** [name] is a parameter of none of the templates. */
    data class UnknownArgument(val name: String) : ArgumentProblem
}

/**
 * Generation without a screen: the templates and arguments come from the caller instead of the tool window's form. A
 * conflict with an existing file always stops (the caller cannot be asked), no balloon is shown and no file is opened.
 *
 * ```kotlin
 * val result = headless.run(HeadlessGenerationRequest(module, listOf("domain.UseCase.useCase"), mapOf("name" to "Login"), OnExistingChoice.Fail))
 * ```
 */
internal class HeadlessGeneration(
    private val runner: GradleTaskRunner,
    private val fileSystem: ProjectFileSystem,
    private val catalog: GenerationCatalogPort,
    private val effects: IdeEffects,
    /** Whether a generation of the IDE's own (tool window, entry) is running. */
    private val otherGenerationRunning: () -> Boolean,
    private val timeout: Duration = TIMEOUT,
) {
    private val mutex = Mutex()

    val isRunning: Boolean get() = mutex.isLocked

    suspend fun run(request: HeadlessGenerationRequest): HeadlessGenerationResult {
        if (otherGenerationRunning() || !mutex.tryLock()) return HeadlessGenerationResult.Busy
        try {
            return runLocked(request)
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun runLocked(request: HeadlessGenerationRequest): HeadlessGenerationResult {
        val snapshots = when (val reload = catalog.reloadForGeneration(request.module)) {
            is GenerationCatalogReload.Failed -> return HeadlessGenerationResult.ReloadFailed(reload.failure)
            is GenerationCatalogReload.Reloaded -> reload.snapshots
        }
        val rows = templatesOf(snapshots).filter { it.module.id == request.module.id }
        val unknown = request.templates.filter { wanted -> rows.none { it.template.template == wanted } }
        if (unknown.isNotEmpty()) return HeadlessGenerationResult.UnknownTemplates(unknown, rows.map { it.template.template })

        val selected = rows.filter { it.template.template in request.templates }
        val form = FormState(selected = selected.map { it.id }, inputs = selected.associate { it.id to request.arguments }, onExisting = request.onExisting)
        val problems = problemsOf(selected, form, request.arguments.keys)
        if (problems.isNotEmpty()) return HeadlessGenerationResult.Invalid(problems)

        val items = generationItemsOf(selected, form)
        items.flatMap { it.expectedPaths }.forEach(catalog::registerOwnWrite)
        // Gradle compiles the definition from the disk.
        effects.saveAllDocuments()
        effects.putLocalHistoryLabelNamed("$LOCAL_HISTORY_PREFIX${selected.joinToString(", ") { it.template.title }}")
        val session = GenerationSession(runner, fileSystem) { ConflictChoice.Stop }
        var timedOut = false
        val report = coroutineScope {
            val running = async { session.run(items, request.onExisting, object : GenerationListener {}) }
            withTimeoutOrNull(timeout) { running.await() } ?: run {
                // Cancelling the session, not this coroutine: the report still says what was written before the limit.
                timedOut = true
                session.cancel()
                running.await()
            }
        }
        val written = report.writtenFiles.map { it.path }
        written.forEach(catalog::registerOwnWrite)
        effects.refreshFiles(written)
        return HeadlessGenerationResult.Ran(report, timedOut)
    }

    private fun problemsOf(selected: List<ModuleTemplate>, form: FormState, given: Set<String>): List<ArgumentProblem> {
        val templateOf = selected.associate { it.id to it.template.template }
        val fields = inputBlockersOf(selected, form).map { blocker ->
            val id = when (blocker) {
                is GenerateBlocker.Unavailable -> blocker.templateId
                is GenerateBlocker.InvalidField -> blocker.templateId
                is GenerateBlocker.UnresolvedPath -> blocker.templateId
                else -> null
            }
            ArgumentProblem.Field(id?.let(templateOf::get).orEmpty(), blocker)
        }
        val known = selected.flatMap { row -> row.template.detail?.let(::allParametersOf).orEmpty().map { it.name } }.toSet()
        return fields + given.filter { it !in known }.sorted().map(ArgumentProblem::UnknownArgument)
    }

    companion object {
        val TIMEOUT: Duration = 10.minutes

        const val LOCAL_HISTORY_PREFIX: String = "katachi (MCP): "
    }
}
