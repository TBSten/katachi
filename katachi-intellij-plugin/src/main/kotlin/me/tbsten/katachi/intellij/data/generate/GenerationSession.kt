package me.tbsten.katachi.intellij.data.generate

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import me.tbsten.katachi.intellij.data.ProjectFileSystem
import me.tbsten.katachi.intellij.data.gradle.GradleRunListener
import me.tbsten.katachi.intellij.data.gradle.GradleRunOutcome
import me.tbsten.katachi.intellij.data.gradle.GradleRunRequest
import me.tbsten.katachi.intellij.data.gradle.GradleTaskRunner
import me.tbsten.katachi.intellij.data.load.classifyGradleFailure
import me.tbsten.katachi.intellij.data.load.collecting
import me.tbsten.katachi.intellij.model.ConflictChoice
import me.tbsten.katachi.intellij.model.ConflictQuestion
import me.tbsten.katachi.intellij.model.GeneratedFile
import me.tbsten.katachi.intellij.model.GenerationFailure
import me.tbsten.katachi.intellij.model.GenerationItemReport
import me.tbsten.katachi.intellij.model.GenerationItemResult
import me.tbsten.katachi.intellij.model.GenerationReport
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.model.WrittenKind
import me.tbsten.katachi.intellij.presentation.OnExistingChoice
import java.nio.file.Path

/** One checked template, ready to run. */
internal data class GenerationItem(
    val templateId: TemplateId,
    val module: KatachiModule,
    /** Every `--arg` except `onExisting`, which the session sets per attempt. */
    val args: List<Pair<String, String>>,
    /** Absolute expected paths, checked for existence when the output cannot say what was written. */
    val expectedPaths: List<Path>,
)

/** What the session reports while it runs; the ViewModel turns it into screen state. */
internal interface GenerationListener : GradleRunListener {
    fun onItemStarted(index: Int) {}

    fun onItemFinished(index: Int, result: GenerationItemResult) {}
}

/**
 * Runs the checked templates one by one, in list order, each as its own `katachiTemplate` build
 * (spec 06). Stops at the first failure; a conflict under `fail` asks [askConflict] whether to
 * overwrite (run the same one again), skip it (without running again) or stop.
 *
 * [cancel] stops the running build and runs nothing after it: the running one becomes
 * [GenerationItemResult.Interrupted], the rest [GenerationItemResult.NotRun] (E-21).
 */
internal class GenerationSession(
    private val runner: GradleTaskRunner,
    private val fileSystem: ProjectFileSystem,
    private val askConflict: suspend (ConflictQuestion) -> ConflictChoice,
) {
    @Volatile private var cancelRequested = false

    @Volatile private var running: Deferred<*>? = null

    /** Whether [cancel] was called, possibly before [run] started. */
    val isCancelRequested: Boolean get() = cancelRequested

    fun cancel() {
        cancelRequested = true
        running?.cancel()
    }

    suspend fun run(items: List<GenerationItem>, onExisting: OnExistingChoice, listener: GenerationListener): GenerationReport {
        val results = MutableList<GenerationItemResult>(items.size) { GenerationItemResult.NotRun }
        for ((index, item) in items.withIndex()) {
            if (cancelRequested) break
            listener.onItemStarted(index)
            val result = runItem(item, index, items.size, onExisting, listener)
            results[index] = result
            listener.onItemFinished(index, result)
            if (result !is GenerationItemResult.Generated && result !is GenerationItemResult.Skipped) break
        }
        return GenerationReport(items.mapIndexed { index, item -> GenerationItemReport(item.templateId, results[index]) })
    }

    private suspend fun runItem(
        item: GenerationItem,
        index: Int,
        total: Int,
        onExisting: OnExistingChoice,
        listener: GenerationListener,
    ): GenerationItemResult {
        val first = attempt(item, onExisting, listener) ?: return interrupted(item)
        if (first !is Attempt.Conflict) return first.result
        return when (askConflict(ConflictQuestion(item.templateId, index, total, first.existing))) {
            ConflictChoice.Overwrite -> (attempt(item, OnExistingChoice.Overwrite, listener) ?: return interrupted(item)).result
            // katachi would write nothing under skip, so running again would only cost a build.
            ConflictChoice.SkipAndContinue -> GenerationItemResult.Skipped(first.existing)
            ConflictChoice.Stop -> GenerationItemResult.StoppedAtConflict(first.existing)
        }
    }

    /** One build of [item]; `null` when [cancel] stopped it. */
    private suspend fun attempt(item: GenerationItem, onExisting: OnExistingChoice, listener: GenerationListener): Attempt? {
        if (cancelRequested) return null
        val output = mutableListOf<String>()
        val request = GradleRunRequest(
            item.module.linkedRootPath,
            listOf(templateInvocationOf(item.module, item.args.withOnExisting(onExisting))),
        )
        val outcome = try {
            coroutineScope {
                val deferred = async(start = CoroutineStart.LAZY) { runner.run(request, collecting(output, listener)) }
                running = deferred
                if (cancelRequested) deferred.cancel()
                deferred.await()
            }
        } catch (e: CancellationException) {
            // Our own cancel() only cancels the build; a cancelled caller (project closing) goes on up.
            currentCoroutineContext().ensureActive()
            if (!cancelRequested) throw e
            return null
        } finally {
            running = null
        }
        return interpret(item, outcome, output)
    }

    private fun interpret(item: GenerationItem, outcome: GradleRunOutcome, output: List<String>): Attempt {
        val parsed = parseTemplateOutput(output)
        val status = parsed.status
        return when {
            status is TemplateRunStatus.Failed -> {
                val conflicting = status.conflicting
                if (conflicting != null) {
                    Attempt.Conflict(conflicting)
                } else {
                    Attempt.Done(GenerationItemResult.Failed(GenerationFailure.Katachi(status.body), output))
                }
            }
            status == TemplateRunStatus.Ok || outcome == GradleRunOutcome.Succeeded -> Attempt.Done(generatedOf(item, parsed, status))
            else -> Attempt.Done(GenerationItemResult.Failed(GenerationFailure.NotReached(classifyGradleFailure(output)), output))
        }
    }

    private fun generatedOf(item: GenerationItem, parsed: TemplateRunOutput, status: TemplateRunStatus): GenerationItemResult {
        if (parsed.written.isEmpty() && parsed.skippedExisting.isNotEmpty()) return GenerationItemResult.Skipped(parsed.skippedExisting)
        val overwritten = parsed.overwritten.toSet()
        val files = parsed.written.map { GeneratedFile(it, if (it in overwritten) WrittenKind.Overwritten else WrittenKind.New) }
        val incomplete = status != TemplateRunStatus.Ok
        val unknown = !incomplete && files.isEmpty() && parsed.projectRoot == null
        return GenerationItemResult.Generated(
            files = files,
            writesUnknown = unknown,
            outputIncomplete = incomplete,
            existingExpected = if (unknown || incomplete) existingOf(item) else emptyList(),
        )
    }

    private fun interrupted(item: GenerationItem) = GenerationItemResult.Interrupted(existingOf(item))

    private fun existingOf(item: GenerationItem): List<Path> = item.expectedPaths.filter(fileSystem::exists)

    private sealed interface Attempt {
        val result: GenerationItemResult

        data class Done(override val result: GenerationItemResult) : Attempt

        data class Conflict(val existing: List<Path>) : Attempt {
            override val result: GenerationItemResult get() = GenerationItemResult.StoppedAtConflict(existing)
        }
    }
}

private fun List<Pair<String, String>>.withOnExisting(choice: OnExistingChoice): List<Pair<String, String>> {
    val rest = filter { it.first != "onExisting" }
    val roleName = rest.filter { it.first == "roleName" }
    return roleName + ("onExisting" to choice.argValue) + rest.filter { it.first != "roleName" }
}
