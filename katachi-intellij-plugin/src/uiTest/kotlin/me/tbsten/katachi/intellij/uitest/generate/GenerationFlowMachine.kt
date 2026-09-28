@file:OptIn(ExperimentalCoroutinesApi::class)

package me.tbsten.katachi.intellij.uitest.generate

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import me.tbsten.katachi.intellij.data.generate.EntryGenerationFailure
import me.tbsten.katachi.intellij.data.generate.EntryGenerationRefusal
import me.tbsten.katachi.intellij.data.generate.EntryIdeAction
import me.tbsten.katachi.intellij.data.generate.SingleFileGeneration
import me.tbsten.katachi.intellij.data.generate.SingleFileGenerationRequest
import me.tbsten.katachi.intellij.data.generate.SingleFileGenerationResult
import me.tbsten.katachi.intellij.presentation.entry.EntryGenerationLedger
import me.tbsten.katachi.intellij.presentation.entry.EntryOrigin
import me.tbsten.katachi.intellij.presentation.entry.LedgerEntry
import me.tbsten.katachi.intellij.testing.FakeFileSystem
import me.tbsten.katachi.intellij.testing.FakeGenerationIdeEffects
import me.tbsten.katachi.intellij.testing.ManualDispatcher
import me.tbsten.katachi.intellij.testing.rowOfTemplate
import me.tbsten.katachi.intellij.testing.rowsOf
import me.tbsten.katachi.intellij.testing.underRoot
import java.nio.file.Path

/** Where a file's generation stands, as the machine's own model of what the ledger must say. */
internal enum class FlowStatus { None, Generating, Failed, Succeeded }

/** What the disk, the editor and the ledger hold for a file: a refused generation changes none of it. */
internal data class FileSnapshot(val disk: String?, val document: String?, val unsaved: Boolean, val ledger: LedgerEntry?, val effectLog: Int)

internal class FlowRun(val file: Int, val result: Deferred<SingleFileGenerationResult>)

/**
 * `SingleFileGeneration` for two files, played with every wait held at a gate ([FlowOp]): the user's
 * clicks, edits, saves and deletes, Gradle and the template re-read finishing, the project closing.
 * Beside the real flow the machine keeps a model of what each step must do ([status], which run is
 * active, whether the file was touched since the provisional file), and after every step
 * [checkFlowInvariants] compares the two.
 *
 * ```kotlin
 * val machine = GenerationFlowMachine()
 * ops.forEach(machine::apply)
 * ```
 */
internal class GenerationFlowMachine {
    val log: MutableList<String> = mutableListOf()

    /** The kinds of ending the machine saw ("generated", "refused", ...), so a test can tell the sequences reached them all. */
    val outcomes: MutableSet<String> = mutableSetOf()
    val probe = FlowProbe()
    val dispatcher = ManualDispatcher()
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    val fileSystem = FakeFileSystem()
    val effects = FakeGenerationIdeEffects(fileSystem)
    val ledger = EntryGenerationLedger()
    var mode: CatalogMode = CatalogMode.Normal

    private val controller = rowsOf("sample-jvm-with-captures").rowOfTemplate("api.Controller")
    private val resources = listOf("user", "admin")
    val names: List<String> = listOf("User", "Admin")
    val targets: List<Path> = resources.zip(names) { resource, name ->
        underRoot("src/main/kotlin/com/example/controller/$resource/${name}Controller.kt")
    }
    val generated: List<String> = resources.zip(names) { resource, name -> "package com.example.controller.$resource\n\nclass ${name}Controller\n" }

    val catalog = GatedCatalog(probe, { mode }, listOf(controller), ::beforeReload)
    val runner = GatedRunner(probe, fileSystem, targets, generated, names, ::beforeGradle)
    val spy = SpyGenerationEffects(effects, probe) { catalog.ownWrites }
    private val generation = SingleFileGeneration(spy, runner, fileSystem, catalog, ledger)

    val runs: Array<FlowRun?> = arrayOfNulls(FILE_COUNT)
    val status: MutableList<FlowStatus> = MutableList(FILE_COUNT) { FlowStatus.None }

    /** Something changed the file after the run's provisional file was written (or since, for a finished run). */
    val touched: MutableList<Boolean> = MutableList(FILE_COUNT) { false }
    val acceptedRuns: IntArray = IntArray(FILE_COUNT)
    val commentable: List<Boolean> = listOf(true, false)

    fun controllerId() = controller.id

    fun textOf(file: Int): String? = effects.documents[targets[file]] ?: fileSystem.readText(targets[file])

    fun apply(op: FlowOp) {
        log += op.toString()
        when (op) {
            is FlowOp.Start -> start(op.file)
            is FlowOp.ReleaseReload -> releaseReload(op.file)
            is FlowOp.FinishGradle -> finishGradle(op.file, op.succeeds)
            is FlowOp.Cancel -> cancel(op.file)
            is FlowOp.ExternalWrite -> touch(op.file) {
                fileSystem.write(targets[op.file], op.text)
                if (targets[op.file] !in effects.unsaved) effects.documents[targets[op.file]] = op.text
            }
            is FlowOp.TypeUnsaved -> if (textOf(op.file) != null) touch(op.file) { effects.typeUnsaved(targets[op.file], op.text) }
            is FlowOp.Save -> if (targets[op.file] in effects.unsaved) touch(op.file) {
                effects.documents[targets[op.file]]?.let { fileSystem.write(targets[op.file], it) }
                effects.unsaved.remove(targets[op.file])
            }
            is FlowOp.Delete -> touch(op.file) {
                fileSystem.delete(targets[op.file])
                effects.documents.remove(targets[op.file])
                effects.unsaved.remove(targets[op.file])
            }
            is FlowOp.SetCatalog -> mode = op.mode
            is FlowOp.ToggleIdeFailure -> effects.failing = if (op.effect in effects.failing) effects.failing - op.effect else effects.failing + op.effect
        }
        if (probe.violations.isNotEmpty()) fail(probe.violations.joinToString("; "))
        checkFlowInvariants(this)
    }

    private fun touch(file: Int, change: () -> Unit) {
        change()
        touched[file] = true
    }

    private fun request(file: Int) = SingleFileGenerationRequest(
        controller,
        EntryOrigin.EditorFile(targets[file]),
        listOf("resource" to resources[file], "name" to names[file]),
        targets[file],
    )

    private fun snapshot(file: Int) = FileSnapshot(
        fileSystem.readText(targets[file]),
        effects.documents[targets[file]],
        targets[file] in effects.unsaved,
        ledger.entryOf(targets[file]),
        effects.log.size,
    )

    private fun saw(outcome: String) {
        outcomes += outcome
    }

    private fun start(file: Int) {
        val before = snapshot(file)
        val text = textOf(file)
        val refused = runs[file] != null || (text != null && isContent(text))
        val ideAction = when {
            before.unsaved && "saveDocument" in effects.failing -> EntryIdeAction.SAVE
            "mkdirs" in effects.failing -> EntryIdeAction.CREATE_DIRECTORIES
            "provisional" in effects.failing -> EntryIdeAction.WRITE_PROVISIONAL
            else -> null
        }
        val failuresBefore = effects.failures.size
        val reloadsBefore = catalog.callsByFile.sum()
        val result = scope.async { generation.run(request(file)) }
        resume(file)
        when {
            refused -> {
                val done = resultOf(file, result)
                if (done != SingleFileGenerationResult.Refused(EntryGenerationRefusal.TargetHasContent(targets[file]))) fail("file $file has content or a run: expected a refusal, got $done")
                saw(if (before.unsaved) "refused unsaved" else "refused")
                if (snapshot(file) != before) fail("file $file: the refused generation changed $before -> ${snapshot(file)}")
                if (catalog.callsByFile.sum() != reloadsBefore || effects.failures.size != failuresBefore) fail("file $file: the refused generation went on")
            }
            ideAction != null -> {
                val done = resultOf(file, result)
                if (done != SingleFileGenerationResult.Failed(EntryGenerationFailure.Ide(ideAction))) fail("file $file: expected the IDE failure '$ideAction', got $done")
                if (catalog.callsByFile.sum() != reloadsBefore) fail("file $file: went on after the IDE call failed")
                expectBalloon(done, failuresBefore)
                saw("ide failure $ideAction")
            }
            else -> {
                if (result.isCompleted) fail("file $file: the generation ended without waiting for the re-read: ${resultOrError(result)}")
                runs[file] = FlowRun(file, result)
                acceptedRuns[file]++
                status[file] = FlowStatus.Generating
                touched[file] = false
            }
        }
    }

    private fun releaseReload(file: Int) {
        val gate = catalog.gates[file] ?: return
        val run = runs[file] ?: fail("file $file: a re-read is waiting without a run")
        val modeNow = mode
        val touchedNow = touched[file]
        val failuresBefore = effects.failures.size
        gate.complete(Unit)
        resume(file)
        val stillRunning = !run.result.isCompleted
        when {
            modeNow == CatalogMode.LoadFailed -> conclude(file, failuresBefore) { it is EntryGenerationFailure.CatalogReloadFailed }
            modeNow == CatalogMode.TemplateGone -> conclude(file, failuresBefore) { it == EntryGenerationFailure.TemplateGone }
            touchedNow -> conclude(file, failuresBefore) { it == EntryGenerationFailure.ChangedMeanwhile(targets[file]) }
            !stillRunning || file !in runner.gates -> fail("file $file: untouched and defined, so Gradle must run: ${resultOrError(run.result)}")
        }
    }

    private fun finishGradle(file: Int, succeeds: Boolean) {
        val gate = runner.gates[file] ?: return
        val run = runs[file] ?: fail("file $file: Gradle is waiting without a run")
        val failuresBefore = effects.failures.size
        gate.complete(succeeds)
        resume(file)
        if (succeeds) {
            val done = resultOf(file, run.result)
            if (done != SingleFileGenerationResult.Generated(listOf(targets[file]))) fail("file $file: Gradle succeeded but the result is $done")
            finish(file, done, failuresBefore)
        } else {
            conclude(file, failuresBefore) { it is EntryGenerationFailure.Katachi }
        }
    }

    private fun cancel(file: Int) {
        val run = runs[file] ?: return
        val cancelledBefore = runner.cancelled
        val inGradle = file in runner.gates
        val failuresBefore = effects.failures.size
        run.result.cancel()
        resume(file)
        saw(if (inGradle) "cancelled in Gradle" else "cancelled in the re-read")
        if (!run.result.isCancelled) fail("file $file: cancelling the run did not stop it")
        if (inGradle && runner.cancelled != cancelledBefore + 1) fail("file $file: cancelling did not stop Gradle")
        if (file in runner.gates || file in catalog.gates) fail("file $file: a cancelled run left a gate open")
        if (effects.failures.size != failuresBefore) fail("file $file: cancelling raised a balloon")
        runs[file] = null
        status[file] = FlowStatus.Failed
    }

    /** The run ended with a [SingleFileGenerationResult.Failed] whose failure satisfies [expected]. */
    private fun conclude(file: Int, failuresBefore: Int, expected: (EntryGenerationFailure) -> Boolean) {
        val run = runs[file] ?: fail("file $file: no run to end")
        val done = resultOf(file, run.result)
        val failure = (done as? SingleFileGenerationResult.Failed)?.failure
        if (failure == null || !expected(failure)) fail("file $file: got the unexpected $done (catalog $mode)")
        finish(file, done, failuresBefore)
    }

    private fun finish(file: Int, done: SingleFileGenerationResult, failuresBefore: Int) {
        runs[file] = null
        saw(if (done is SingleFileGenerationResult.Failed) "failed ${done.failure::class.simpleName}" else "generated")
        status[file] = if (done is SingleFileGenerationResult.Generated) FlowStatus.Succeeded else FlowStatus.Failed
        if (done is SingleFileGenerationResult.Failed) expectBalloon(done, failuresBefore)
        else if (effects.failures.size != failuresBefore) fail("file $file: a balloon for a generation that did not fail")
    }

    private fun expectBalloon(done: SingleFileGenerationResult, failuresBefore: Int) {
        val failure = (done as SingleFileGenerationResult.Failed).failure
        if (effects.failures.drop(failuresBefore) != listOf(failure)) fail("the balloon must be raised once with $failure, got ${effects.failures.drop(failuresBefore)}")
    }

    private fun resume(file: Int) {
        probe.current = file
        dispatcher.runAll()
        probe.current = -1
    }

    private fun resultOf(file: Int, result: Deferred<SingleFileGenerationResult>): SingleFileGenerationResult {
        if (!result.isCompleted) fail("file $file: the generation is still waiting")
        return resultOrError(result)
    }

    private fun resultOrError(result: Deferred<SingleFileGenerationResult>): SingleFileGenerationResult {
        if (!result.isCompleted) fail("the generation is still waiting")
        result.getCompletionExceptionOrNull()?.let { throw IllegalStateException("the generation threw $it", it) }
        return result.getCompleted()
    }

    private fun beforeReload(file: Int) {
        val provisional = spy.provisionals[targets[file]]
        if (provisional == null || textOf(file) != provisional) probe.violation("file $file: the template re-read began before the provisional file was written")
    }

    /** Gradle is about to write: the file may hold no content, on disk or in the editor, and nothing unsaved. */
    private fun beforeGradle(file: Int) {
        val disk = fileSystem.readText(targets[file])
        val shown = textOf(file)
        val bad = disk == null || shown == null || isContent(disk) || isContent(shown) || targets[file] in effects.unsaved
        if (bad) probe.violation("file $file: Gradle began over disk=$disk shown=$shown unsaved=${targets[file] in effects.unsaved}")
    }

    private fun fail(message: String): Nothing = throw IllegalStateException(message)
}
