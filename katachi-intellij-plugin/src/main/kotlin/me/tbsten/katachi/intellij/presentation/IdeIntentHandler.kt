package me.tbsten.katachi.intellij.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.tbsten.katachi.intellij.data.ProjectFileSystem
import me.tbsten.katachi.intellij.data.generate.templateArgsOf
import me.tbsten.katachi.intellij.data.gradle.GradleRunListener
import me.tbsten.katachi.intellij.data.gradle.GradleRunRequest
import me.tbsten.katachi.intellij.data.gradle.GradleTaskInvocation
import me.tbsten.katachi.intellij.data.gradle.GradleTaskRunner
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.model.TemplateId

/**
 * Carries out the intents that only hand something to the IDE (open, copy, show the log, sync) and
 * the ones that run Gradle outside loading and generating ("show cause").
 *
 * Split from [KatachiToolWindowViewModel] to keep that one about loading and generating.
 */
internal class IdeIntentHandler(
    private val scope: CoroutineScope,
    private val runner: GradleTaskRunner,
    private val fileSystem: ProjectFileSystem,
    private val effects: IdeEffects,
    private val state: () -> KatachiScreenState,
    private val update: ((KatachiScreenState) -> KatachiScreenState) -> Unit,
) {
    /** Returns `false` for an intent this handler does not own. */
    fun handle(intent: KatachiIntent): Boolean {
        when (intent) {
            KatachiIntent.ShowLog -> effects.showLog()
            KatachiIntent.SyncGradle -> effects.syncGradle()
            is KatachiIntent.OpenDocs -> effects.openDocs(intent.page)
            KatachiIntent.OpenBuildScript -> {
                val scripts = state().modules.map { it.directory.resolve(BUILD_SCRIPT) }.filter(fileSystem::exists)
                scope.launch { effects.openFiles(scripts.take(1)) }
            }
            is KatachiIntent.OpenFile -> scope.launch { effects.openFiles(listOf(intent.path)) }
            is KatachiIntent.CopyCommand -> rowOf(intent.templateId)?.let { effects.copyToClipboard(commandLineOf(it, state().form)) }
            is KatachiIntent.ShowExpectedContents -> rowOf(intent.templateId)?.let { row ->
                val files = expectedContentsOf(row, state().form).filter { it.content != null }
                scope.launch { effects.showExpectedContents(files) }
            }
            is KatachiIntent.ShowCause -> showCause(intent.templateId)
            else -> return false
        }
        return true
    }

    /** Runs `katachiTemplates --arg roleName=X` and shows its text under the row (E-07). */
    private fun showCause(id: TemplateId) {
        val row = rowOf(id) ?: return
        if (state().view.causes[id] is CauseState.Loading) return
        update { it.withCause(id, CauseState.Loading) }
        scope.launch {
            val lines = mutableListOf<String>()
            val request = GradleRunRequest(
                row.module.linkedRootPath,
                listOf(GradleTaskInvocation(row.module.taskPath(KatachiModule.DESCRIBE_TEMPLATES_TASK), listOf("roleName" to row.template.roleName))),
            )
            try {
                runner.run(request, object : GradleRunListener {
                    override fun onLine(line: String) {
                        lines += line
                    }
                })
            } finally {
                update { it.withCause(id, CauseState.Loaded(causeLinesOf(lines))) }
            }
        }
    }

    private fun rowOf(id: TemplateId): ModuleTemplate? = state().rows.firstOrNull { it.id == id }

    private companion object {
        const val BUILD_SCRIPT = "build.gradle.kts"
    }
}

/** The files [row] is expected to write with the current inputs, at absolute paths. Unresolved ones are left out. */
internal fun expectedContentsOf(row: ModuleTemplate, form: FormState): List<ExpectedContent> {
    val detail = row.template.detail ?: return emptyList()
    return expectedFilesOf(detail, form.inputsOf(row.id)).mapNotNull { file ->
        val known = file.location as? ExpectedLocation.Known ?: return@mapNotNull null
        val path = resolveExpectedPath(row.module.linkedRootPath, known.path) ?: return@mapNotNull null
        ExpectedContent(path, file.fileName, file.content)
    }
}

/** "Copy command": the `./gradlew` line that generates [row] with the current inputs. */
internal fun commandLineOf(row: ModuleTemplate, form: FormState): String {
    val detail = row.template.detail ?: return ""
    val args = templateArgsOf(row.template.roleName, detail, form.inputsOf(row.id), form.onExisting)
    val invocation = GradleTaskInvocation(row.module.taskPath(KatachiModule.TEMPLATE_TASK), args)
    return GradleRunRequest(row.module.linkedRootPath, listOf(invocation)).commandLine
}

// Gradle's own lines around the processor's text, which say nothing about the template.
private val GRADLE_NOISE = listOf("> Task ", "> Configure ", "BUILD ", "Deprecated Gradle", "You can use '--warning-mode", "For more on this", "Configuration cache", "Reusing configuration cache")

private val ACTIONABLE_TASKS = Regex("""\d+ actionable tasks?:.*""")

/** The output of "show cause" without Gradle's bookkeeping lines and the blank lines around it. Provisional. */
internal fun causeLinesOf(output: List<String>): List<String> {
    val kept = output.filterNot { line -> GRADLE_NOISE.any { line.startsWith(it) } || line.matches(ACTIONABLE_TASKS) }
    return kept.dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }
}

private fun KatachiScreenState.withCause(id: TemplateId, cause: CauseState): KatachiScreenState =
    copy(view = view.copy(causes = view.causes + (id to cause)))
