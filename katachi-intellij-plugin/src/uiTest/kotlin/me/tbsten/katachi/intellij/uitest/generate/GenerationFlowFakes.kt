package me.tbsten.katachi.intellij.uitest.generate

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import me.tbsten.katachi.intellij.data.generate.GenerationCatalogPort
import me.tbsten.katachi.intellij.data.generate.GenerationCatalogReload
import me.tbsten.katachi.intellij.data.gradle.GradleRunListener
import me.tbsten.katachi.intellij.data.gradle.GradleRunOutcome
import me.tbsten.katachi.intellij.data.gradle.GradleRunRequest
import me.tbsten.katachi.intellij.data.gradle.GradleTaskRunner
import me.tbsten.katachi.intellij.data.placement.TemplatePlacementIndex
import me.tbsten.katachi.intellij.model.DescriptionSnapshot
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.LoadFailure
import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.presentation.IdeEffects
import me.tbsten.katachi.intellij.testing.FakeFileSystem
import me.tbsten.katachi.intellij.testing.FakeGenerationIdeEffects
import me.tbsten.katachi.intellij.testing.ROOT
import java.nio.file.Path
import java.time.Instant

// The world SingleFileGeneration runs in for GenerationFlowMachine: gates the machine opens one at a
// time, so the interleavings of a real IDE are played exactly. Nothing here touches IntelliJ.

/** What the re-read of the templates answers next. */
internal enum class CatalogMode { Normal, TemplateGone, LoadFailed }

/**
 * Which file's run the machine is resuming ([current]), and what the fakes saw go wrong. The fakes
 * record instead of throwing: an exception inside the flow would be taken for a failure of the flow.
 */
internal class FlowProbe {
    var current: Int = -1
    val violations: MutableList<String> = mutableListOf()

    fun violation(message: String) {
        violations += message
    }
}

/** The text of a file counts as content (never overwritten) by a second, independent reading of the rule. */
internal fun isContent(text: CharSequence): Boolean = text.lines().any { line ->
    val trimmed = line.trim()
    trimmed.isNotEmpty() && !trimmed.startsWith("//") && !trimmed.startsWith("package ")
}

/**
 * The template re-read, held at a gate per file so an edit can land between the provisional file and
 * Gradle. [beforeReload] checks the world at the moment a run asks.
 */
internal class GatedCatalog(
    private val probe: FlowProbe,
    private val mode: () -> CatalogMode,
    private val templates: List<ModuleTemplate>,
    private val beforeReload: (Int) -> Unit,
) : GenerationCatalogPort {
    val gates: MutableMap<Int, CompletableDeferred<Unit>> = mutableMapOf()
    val callsByFile: IntArray = IntArray(FILE_COUNT)
    val ownWrites: MutableSet<Path> = mutableSetOf()

    override suspend fun reloadForGeneration(module: KatachiModule): GenerationCatalogReload {
        val file = probe.current
        callsByFile[file]++
        beforeReload(file)
        if (file in gates) probe.violation("two template re-reads of file $file overlap")
        val gate = CompletableDeferred<Unit>()
        gates[file] = gate
        try {
            gate.await()
        } finally {
            gates.remove(file)
        }
        return when (mode()) {
            CatalogMode.Normal -> reloaded(module, templates)
            CatalogMode.TemplateGone -> reloaded(module, emptyList())
            CatalogMode.LoadFailed -> GenerationCatalogReload.Failed(LoadFailure.Cancelled)
        }
    }

    override fun registerOwnWrite(path: Path) {
        ownWrites.add(path)
    }

    private fun reloaded(module: KatachiModule, templates: List<ModuleTemplate>) = GenerationCatalogReload.Reloaded(
        listOf(DescriptionSnapshot(module, "0.3.0", templates.map { it.template }, Instant.EPOCH)),
        TemplatePlacementIndex.EMPTY,
    )
}

/**
 * `katachiTemplate` held at a gate per file, then finished by the machine. It writes the generated
 * text when it succeeds. [onStart] checks the world at the moment Gradle would begin to write.
 */
internal class GatedRunner(
    private val probe: FlowProbe,
    private val fileSystem: FakeFileSystem,
    private val targets: List<Path>,
    private val generated: List<String>,
    private val names: List<String>,
    private val onStart: (Int) -> Unit,
) : GradleTaskRunner {
    val gates: MutableMap<Int, CompletableDeferred<Boolean>> = mutableMapOf()
    val startsByFile: IntArray = IntArray(FILE_COUNT)
    var cancelled: Int = 0
        private set

    override suspend fun run(request: GradleRunRequest, listener: GradleRunListener): GradleRunOutcome {
        val file = probe.current
        startsByFile[file]++
        val invocation = request.tasks.singleOrNull()
        val expectedHead = listOf("template" to "api.Controller", "onExisting" to "overwrite")
        if (invocation == null || invocation.args.take(2) != expectedHead || invocation.args.toMap()["name"] != names[file]) {
            probe.violation("file $file: Gradle got ${request.tasks} instead of one --arg template=<one> --arg onExisting=overwrite")
        }
        if (file in gates) probe.violation("two Gradle requests for file $file overlap")
        onStart(file)
        val gate = CompletableDeferred<Boolean>()
        gates[file] = gate
        val succeeds = try {
            gate.await()
        } catch (e: CancellationException) {
            cancelled++
            throw e
        } finally {
            gates.remove(file)
        }
        listener.onLine("> Task :arch-a:katachiTemplate" + if (succeeds) "" else " FAILED")
        if (!succeeds) {
            listener.onLine("")
            listener.onLine("BUILD FAILED in 1s")
            return GradleRunOutcome.Failed
        }
        fileSystem.write(targets[file], generated[file])
        listener.onLine("  [template] Generating 1 files under ${ROOT.toUri()}")
        listener.onLine("  [template] Wrote ${targets[file].toUri()}")
        listener.onLine("BUILD SUCCESSFUL in 1s")
        return GradleRunOutcome.Succeeded
    }
}

/**
 * The IDE effects of the flow with a check on each provisional file: it goes over no content, and
 * the definition watcher was told first (the own-write registration). Everything else is [inner].
 */
internal class SpyGenerationEffects(
    private val inner: FakeGenerationIdeEffects,
    private val probe: FlowProbe,
    private val ownWrites: () -> Set<Path>,
) : IdeEffects by inner {
    /** The provisional text last written to each path. */
    val provisionals: MutableMap<Path, String> = mutableMapOf()

    override suspend fun writeProvisionalFile(path: Path, text: String): Boolean {
        val before = inner.currentText(path)
        if (before != null && isContent(before)) probe.violation("the provisional file went over content: $path had ${before.toString().take(40)}")
        if (path !in ownWrites()) probe.violation("the provisional file $path was written before it was registered as an own write")
        val written = inner.writeProvisionalFile(path, text)
        if (written) provisionals[path] = text
        return written
    }
}

internal const val FILE_COUNT: Int = 2
