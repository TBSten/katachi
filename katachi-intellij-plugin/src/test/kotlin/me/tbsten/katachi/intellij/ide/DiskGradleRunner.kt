package me.tbsten.katachi.intellij.ide

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import me.tbsten.katachi.intellij.data.gradle.GradleRunListener
import me.tbsten.katachi.intellij.data.gradle.GradleRunOutcome
import me.tbsten.katachi.intellij.data.gradle.GradleRunRequest
import me.tbsten.katachi.intellij.data.gradle.GradleTaskInvocation
import me.tbsten.katachi.intellij.data.gradle.GradleTaskRunner
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.testing.ContractFixtures
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections

/**
 * What one load of the definitions (`katachiInternalTemplatesJson`) answers with, in the order of the loads.
 *
 * [json] is written into every module of the request; `null` takes the module's fixture (see
 * [DiskGradleRunner.fixtureOf]). [fails] makes the build fail without writing anything.
 */
internal data class LoadAnswer(val json: String? = null, val fails: Boolean = false)

/** The `katachiTemplate` invocation a [PlannedGeneration] answers: which module, and the `--arg`s it was given. */
internal data class GenerationCall(val module: String, val moduleDir: Path, val root: Path, val args: List<Pair<String, String>>) {
    /** The `--arg template=...` value, the complete specifier of the template. */
    val template: String? get() = arg("template")

    /** The last value given for [key]. */
    fun arg(key: String): String? = args.lastOrNull { it.first == key }?.second
}

/**
 * One generation of the fake Gradle, decided from what it was asked for.
 *
 * [files] maps the call to the files to write (path to content); a relative path is under the
 * root of the project. It is not called when [fails]. A generation with a [gate] prints what it
 * would write, waits on the gate, then writes; [reachedGate] completes once it waits.
 */
internal class PlannedGeneration(
    val fails: Boolean = false,
    val gate: CompletableDeferred<Unit>? = null,
    val files: (GenerationCall) -> Map<Path, String> = { emptyMap() },
) {
    val reachedGate: CompletableDeferred<Unit> = CompletableDeferred()
}

/**
 * A Gradle that "loads" by writing the contract JSON into the module and "generates" by writing the
 * files a plan or the contract output names, then printing them with [root] in place of the token.
 *
 * Loads answer, in order: [loads] (a change of the definitions between loads), else the module's
 * contract fixture ([fixtureOf]). Generations answer, in order: [plans] (files decided from the
 * template and args, which may fail or wait on a gate), else the contract output `output/<name>.log`
 * of [generations], else `new`.
 */
internal class DiskGradleRunner(private val root: Path) : GradleTaskRunner {
    val requests: MutableList<GradleRunRequest> = Collections.synchronizedList(mutableListOf())

    /** The contract output (`output/<name>.log`) each `katachiTemplate` run answers with, in order. */
    val generations: MutableList<String> = Collections.synchronizedList(mutableListOf())

    /** What each load answers with, in order; once empty, the module's fixture. */
    val loads: MutableList<LoadAnswer> = Collections.synchronizedList(mutableListOf())

    /** The generations answered before [generations], in order. */
    val plans: MutableList<PlannedGeneration> = Collections.synchronizedList(mutableListOf())

    /**
     * The contract JSON (`json/<name>.json`) a module answers a load with, by the module's directory
     * name (`arch-a` for `:arch-a`); the module's own name when absent.
     */
    val fixtureOf: MutableMap<String, String> = Collections.synchronizedMap(mutableMapOf())

    /** When set, a generation of [generations] suspends here after printing, so a test can close the project mid-run. */
    @Volatile var gate: CompletableDeferred<Unit>? = null

    /** Completes once a generation of [generations] is waiting on [gate]. */
    val reachedGate: CompletableDeferred<Unit> = CompletableDeferred()

    @Volatile var cancelled: Boolean = false
        private set

    /** The requests that ran a generation (`katachiTemplate`). */
    val generationRequests: List<GradleRunRequest> get() = synchronized(requests) { requests.filterNot(::isLoad) }

    /** The requests that ran a load of the definitions. */
    val loadRequests: List<GradleRunRequest> get() = synchronized(requests) { requests.filter(::isLoad) }

    override suspend fun run(request: GradleRunRequest, listener: GradleRunListener): GradleRunOutcome {
        requests += request
        return if (isLoad(request)) load(request, listener) else generate(request, listener)
    }

    private fun isLoad(request: GradleRunRequest): Boolean = request.tasks.first().taskPath.endsWith(":${KatachiModule.TEMPLATES_JSON_TASK}")

    private fun load(request: GradleRunRequest, listener: GradleRunListener): GradleRunOutcome {
        val answer = synchronized(loads) { if (loads.isEmpty()) LoadAnswer() else loads.removeAt(0) }
        if (answer.fails) {
            failureLines(request.tasks.first().taskPath).forEach(listener::onLine)
            return GradleRunOutcome.Failed
        }
        for (invocation in request.tasks) {
            val module = moduleOf(invocation)
            val json = root.resolve(module).resolve(KatachiModule.TEMPLATE_DESCRIPTION_JSON)
            Files.createDirectories(json.parent)
            Files.writeString(json, answer.json ?: ContractFixtures.json(fixtureOf[module] ?: module))
        }
        return GradleRunOutcome.Succeeded
    }

    private suspend fun generate(request: GradleRunRequest, listener: GradleRunListener): GradleRunOutcome {
        val plan = synchronized(plans) { if (plans.isEmpty()) null else plans.removeAt(0) }
        return if (plan != null) runPlan(plan, request, listener) else runContractOutput(request, listener)
    }

    private suspend fun runPlan(plan: PlannedGeneration, request: GradleRunRequest, listener: GradleRunListener): GradleRunOutcome {
        if (plan.fails) {
            failureLines(request.tasks.first().taskPath).forEach(listener::onLine)
            return GradleRunOutcome.Failed
        }
        val files = request.tasks.flatMap { invocation ->
            val module = moduleOf(invocation)
            plan.files(GenerationCall(module, root.resolve(module), root, invocation.args)).entries.map { (path, text) -> root.resolve(path) to text }
        }
        listener.onLine("> Task ${request.tasks.first().taskPath}")
        listener.onLine("  [template] Generating ${files.size} files under ${root.toUri().toString().removeSuffix("/")}")
        files.forEach { (path, _) -> listener.onLine("  [template] Wrote ${path.toUri()}") }
        listener.onLine("BUILD SUCCESSFUL in 1s")
        plan.gate?.let { gate ->
            plan.reachedGate.complete(Unit)
            awaitCancellable(gate)
        }
        files.forEach { (path, text) -> write(path, text) }
        return GradleRunOutcome.Succeeded
    }

    private suspend fun runContractOutput(request: GradleRunRequest, listener: GradleRunListener): GradleRunOutcome {
        val name = synchronized(generations) { if (generations.isEmpty()) "new" else generations.removeAt(0) }
        val lines = ContractFixtures.outputLines(name, root)
        lines.forEach(listener::onLine)
        gate?.let { gate ->
            reachedGate.complete(Unit)
            awaitCancellable(gate)
        }
        writeReportedFiles(lines)
        return if (ContractFixtures.exitCode(name) == 0) GradleRunOutcome.Succeeded else GradleRunOutcome.Failed
    }

    private suspend fun awaitCancellable(gate: CompletableDeferred<Unit>) {
        try {
            gate.await()
        } catch (e: CancellationException) {
            cancelled = true
            throw e
        }
    }

    private fun moduleOf(invocation: GradleTaskInvocation): String = invocation.taskPath.substringBeforeLast(':').trim(':')

    private fun failureLines(taskPath: String): List<String> = listOf("> Task $taskPath FAILED", "", "BUILD FAILED in 1s")

    private fun writeReportedFiles(lines: List<String>) {
        for (line in lines) {
            val uri = line.substringAfter("[template] Wrote ", missingDelimiterValue = "").takeIf { it.startsWith("file:") } ?: continue
            write(Path.of(java.net.URI(uri)), "// written by the fake Gradle\n")
        }
    }

    private fun write(path: Path, text: String) {
        Files.createDirectories(path.parent)
        Files.writeString(path, text)
    }
}
