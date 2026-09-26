package me.tbsten.katachi.intellij.data.load

import me.tbsten.katachi.intellij.data.ProjectFileSystem
import me.tbsten.katachi.intellij.data.gradle.GradleRunListener
import me.tbsten.katachi.intellij.data.gradle.GradleRunOutcome
import me.tbsten.katachi.intellij.data.gradle.GradleRunRequest
import me.tbsten.katachi.intellij.data.gradle.GradleTaskInvocation
import me.tbsten.katachi.intellij.data.gradle.GradleTaskRunner
import me.tbsten.katachi.intellij.data.json.KatachiIncompatibleTemplateJsonException
import me.tbsten.katachi.intellij.data.json.KatachiMalformedTemplateJsonException
import me.tbsten.katachi.intellij.data.json.parseTemplateDescriptionJson
import me.tbsten.katachi.intellij.model.DescriptionSnapshot
import me.tbsten.katachi.intellij.model.GradleFailure
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.LoadFailure
import me.tbsten.katachi.intellij.model.ModuleId

/** How a load ended. Cancellation is thrown as `CancellationException`, not returned. */
internal sealed interface LoadResult {
    /** One snapshot per module that answered, in the order the modules were given. */
    data class Loaded(val snapshots: List<DescriptionSnapshot>) : LoadResult

    data class Failed(val failure: LoadFailure, val output: List<String>) : LoadResult
}

/**
 * Runs `katachiInternalTemplatesJson` and reads the JSON it writes. The JSON on disk is the cache as
 * well: [readCached] shows the last result before Gradle runs (E-43).
 */
internal class TemplateDescriptionLoader(
    private val runner: GradleTaskRunner,
    private val fileSystem: ProjectFileSystem,
) {
    /** The snapshots whose JSON is already on disk and readable; missing or broken ones are skipped. */
    fun readCached(modules: List<KatachiModule>, versions: Map<ModuleId, String>): List<DescriptionSnapshot> =
        modules.mapNotNull { module ->
            when (val read = readSnapshot(module, versions[module.id])) {
                is SnapshotRead.Read -> read.snapshot
                is SnapshotRead.Failed -> null
            }
        }

    /**
     * Runs the task of every module in one Gradle build per linked root (E-05, E-32), then reads
     * each JSON. The first failing root fails the whole load.
     */
    suspend fun load(modules: List<KatachiModule>, versions: Map<ModuleId, String>, listener: GradleRunListener): LoadResult {
        val output = mutableListOf<String>()
        for ((root, rootModules) in modules.groupBy { it.linkedRootPath }) {
            val request = GradleRunRequest(root, rootModules.map { GradleTaskInvocation(it.taskPath(KatachiModule.TEMPLATES_JSON_TASK)) })
            val outcome = runner.run(request, collecting(output, listener))
            if (outcome == GradleRunOutcome.Failed) return LoadResult.Failed(LoadFailure.Gradle(classifyGradleFailure(output)), output.toList())
        }
        return readAll(modules, versions, output)
    }

    /**
     * Loads modules found without a task list (E-30, provisional until spike S-5): each candidate
     * runs on its own, and those answering "task not found" are not definition modules after all.
     */
    suspend fun loadCandidates(candidates: List<KatachiModule>, versions: Map<ModuleId, String>, listener: GradleRunListener): LoadResult {
        val output = mutableListOf<String>()
        val answered = mutableListOf<KatachiModule>()
        for (module in candidates) {
            val own = mutableListOf<String>()
            val request = GradleRunRequest(module.linkedRootPath, listOf(GradleTaskInvocation(module.taskPath(KatachiModule.TEMPLATES_JSON_TASK))))
            val outcome = runner.run(request, collecting(own, listener))
            output += own
            if (outcome == GradleRunOutcome.Succeeded) {
                answered += module
                continue
            }
            val failure = classifyGradleFailure(own)
            if (failure !is GradleFailure.TaskNotFound) return LoadResult.Failed(LoadFailure.Gradle(failure), output.toList())
        }
        return readAll(answered, versions, output)
    }

    private fun readAll(modules: List<KatachiModule>, versions: Map<ModuleId, String>, output: List<String>): LoadResult {
        val snapshots = modules.map { module ->
            when (val read = readSnapshot(module, versions[module.id])) {
                is SnapshotRead.Read -> read.snapshot
                is SnapshotRead.Failed -> return LoadResult.Failed(read.failure, output.toList())
            }
        }
        return LoadResult.Loaded(snapshots)
    }

    private fun readSnapshot(module: KatachiModule, version: String?): SnapshotRead {
        val path = module.templateDescriptionJson
        val text = fileSystem.readText(path) ?: return SnapshotRead.Failed(LoadFailure.JsonMissing(path))
        val loadedAt = fileSystem.lastModified(path) ?: return SnapshotRead.Failed(LoadFailure.JsonMissing(path))
        return try {
            SnapshotRead.Read(DescriptionSnapshot(module, version, parseTemplateDescriptionJson(text, path), loadedAt))
        } catch (e: KatachiMalformedTemplateJsonException) {
            SnapshotRead.Failed(LoadFailure.MalformedJson(path, e.message.orEmpty()))
        } catch (e: KatachiIncompatibleTemplateJsonException) {
            SnapshotRead.Failed(LoadFailure.IncompatibleJson(path, e.location, e.message.orEmpty()))
        }
    }

    private sealed interface SnapshotRead {
        data class Read(val snapshot: DescriptionSnapshot) : SnapshotRead

        data class Failed(val failure: LoadFailure) : SnapshotRead
    }
}

/** A listener that keeps every line in [sink] and passes everything on to [downstream]. */
internal fun collecting(sink: MutableList<String>, downstream: GradleRunListener): GradleRunListener = object : GradleRunListener {
    override fun onLine(line: String) {
        sink += line
        downstream.onLine(line)
    }

    override fun onTask(taskPath: String) = downstream.onTask(taskPath)
}
