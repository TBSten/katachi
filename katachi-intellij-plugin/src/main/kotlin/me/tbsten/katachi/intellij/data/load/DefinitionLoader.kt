package me.tbsten.katachi.intellij.data.load

import me.tbsten.katachi.intellij.data.ProjectFileSystem
import me.tbsten.katachi.intellij.data.gradle.GradleRunListener
import me.tbsten.katachi.intellij.data.gradle.GradleRunOutcome
import me.tbsten.katachi.intellij.data.gradle.GradleRunRequest
import me.tbsten.katachi.intellij.data.gradle.GradleTaskInvocation
import me.tbsten.katachi.intellij.data.gradle.GradleTaskRunner
import me.tbsten.katachi.intellij.data.json.KatachiDefinitionJsonException
import me.tbsten.katachi.intellij.data.json.KatachiIncompatibleDefinitionJsonException
import me.tbsten.katachi.intellij.data.json.KatachiIncompatibleTemplateJsonException
import me.tbsten.katachi.intellij.data.json.KatachiMalformedDefinitionJsonException
import me.tbsten.katachi.intellij.data.json.KatachiMalformedTemplateJsonException
import me.tbsten.katachi.intellij.data.json.KatachiUnsupportedDefinitionSchemaException
import me.tbsten.katachi.intellij.data.json.parseDefinitionErrorsJson
import me.tbsten.katachi.intellij.data.json.parseDefinitionJson
import me.tbsten.katachi.intellij.data.json.parseTemplateDescriptionJson
import me.tbsten.katachi.intellij.model.DescriptionSnapshot
import me.tbsten.katachi.intellij.model.GradleFailure
import me.tbsten.katachi.intellij.model.KatachiCapability
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.LoadFailure
import me.tbsten.katachi.intellij.model.ModuleId
import java.nio.file.Path
import java.time.Instant

internal sealed interface LoadResult {
    data class Loaded(val snapshots: List<DescriptionSnapshot>) : LoadResult

    data class Failed(val failure: LoadFailure, val output: List<String>) : LoadResult
}

// `definition-errors.json` counts as this run's when its modification time differs from before the run; no clock is compared.
internal class DefinitionLoader(
    private val runner: GradleTaskRunner,
    private val fileSystem: ProjectFileSystem,
) {
    fun readCached(modules: List<KatachiModule>, versions: Map<ModuleId, String>): List<DescriptionSnapshot> =
        modules.mapNotNull { module ->
            when (val read = readSnapshot(module, versions[module.id])) {
                is SnapshotRead.Read -> read.snapshot
                is SnapshotRead.Failed -> null
            }
        }

    suspend fun load(modules: List<KatachiModule>, versions: Map<ModuleId, String>, listener: GradleRunListener): LoadResult {
        val output = mutableListOf<String>()
        for ((root, rootModules) in modules.groupBy { it.linkedRootPath }) {
            val before = errorsStampsOf(rootModules)
            val request = GradleRunRequest(root, rootModules.map { GradleTaskInvocation(it.taskPath(taskOf(it))) })
            val outcome = runner.run(request, collecting(output, listener))
            if (outcome == GradleRunOutcome.Failed) {
                return LoadResult.Failed(LoadFailure.Gradle(failureOfRun(rootModules, before, output)), output.toList())
            }
        }
        return readAll(modules, versions, output)
    }

    suspend fun loadCandidates(candidates: List<KatachiModule>, versions: Map<ModuleId, String>, listener: GradleRunListener): LoadResult {
        val output = mutableListOf<String>()
        val answered = mutableListOf<KatachiModule>()
        for (module in candidates) {
            // A candidate of the detection knows no task; one that does tries only those.
            val order = if (module.capabilities.isEmpty()) CANDIDATE_ORDER else CANDIDATE_ORDER.filter { it in module.capabilities }
            for (capability in order) {
                val own = mutableListOf<String>()
                val before = errorsStampsOf(listOf(module))
                val task = if (capability == KatachiCapability.Definition) KatachiModule.DEFINITION_JSON_TASK else KatachiModule.TEMPLATES_JSON_TASK
                val request = GradleRunRequest(module.linkedRootPath, listOf(GradleTaskInvocation(module.taskPath(task))))
                val outcome = runner.run(request, collecting(own, listener))
                output += own
                if (outcome == GradleRunOutcome.Succeeded) {
                    answered += module.copy(capabilities = setOf(capability))
                    break
                }
                val failure = failureOfRun(listOf(module), before, own)
                if (failure !is GradleFailure.TaskNotFound) return LoadResult.Failed(LoadFailure.Gradle(failure), output.toList())
            }
        }
        return readAll(answered, versions, output)
    }

    private fun failureOfRun(modules: List<KatachiModule>, before: Map<Path, Instant?>, output: List<String>): GradleFailure {
        val errors = modules.filter { it.readsDefinition }.flatMap { module ->
            val path = module.definitionErrorsJson
            val written = fileSystem.lastModified(path)
            if (written == null || written == before[path]) return@flatMap emptyList()
            val text = fileSystem.readText(path) ?: return@flatMap emptyList()
            parseDefinitionErrorsJson(text, path, module.id).orEmpty()
        }
        if (errors.isEmpty()) return classifyGradleFailure(output)
        return GradleFailure.DefinitionRejected(errors, errors.first().message.lines().dropLastWhile { it.isBlank() })
    }

    private fun errorsStampsOf(modules: List<KatachiModule>): Map<Path, Instant?> =
        modules.filter { it.readsDefinition }.associate { it.definitionErrorsJson to fileSystem.lastModified(it.definitionErrorsJson) }

    private fun taskOf(module: KatachiModule): String =
        if (module.readsDefinition) KatachiModule.DEFINITION_JSON_TASK else KatachiModule.TEMPLATES_JSON_TASK

    private fun readAll(modules: List<KatachiModule>, versions: Map<ModuleId, String>, output: List<String>): LoadResult {
        val snapshots = modules.map { module ->
            when (val read = readSnapshot(module, versions[module.id])) {
                is SnapshotRead.Read -> read.snapshot
                is SnapshotRead.Failed -> return LoadResult.Failed(read.failure, output.toList())
            }
        }
        return LoadResult.Loaded(snapshots)
    }

    private fun readSnapshot(module: KatachiModule, version: String?): SnapshotRead =
        if (module.readsDefinition) readDefinitionSnapshot(module, version) else readTemplatesSnapshot(module, version)

    private fun readDefinitionSnapshot(module: KatachiModule, version: String?): SnapshotRead {
        val path = module.definitionJson
        val text = fileSystem.readText(path) ?: return SnapshotRead.Failed(LoadFailure.JsonMissing(path))
        val loadedAt = fileSystem.lastModified(path) ?: return SnapshotRead.Failed(LoadFailure.JsonMissing(path))
        return try {
            val parsed = parseDefinitionJson(text, path)
            SnapshotRead.Read(DescriptionSnapshot(module, version ?: parsed.definition.katachiVersion, parsed.templates, loadedAt, parsed.definition))
        } catch (e: KatachiDefinitionJsonException) {
            SnapshotRead.Failed(failureOf(e))
        }
    }

    private fun readTemplatesSnapshot(module: KatachiModule, version: String?): SnapshotRead {
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

    private companion object {
        val CANDIDATE_ORDER = listOf(KatachiCapability.Definition, KatachiCapability.Templates)
    }
}

internal fun failureOf(failure: KatachiDefinitionJsonException): LoadFailure = when (failure) {
    is KatachiMalformedDefinitionJsonException -> LoadFailure.MalformedJson(failure.file, failure.message.orEmpty())
    is KatachiIncompatibleDefinitionJsonException -> LoadFailure.IncompatibleJson(failure.file, failure.location, failure.message.orEmpty())
    is KatachiUnsupportedDefinitionSchemaException -> LoadFailure.IncompatibleJson(failure.file, "schemaVersion", failure.message.orEmpty())
}

internal fun collecting(sink: MutableList<String>, downstream: GradleRunListener): GradleRunListener = object : GradleRunListener {
    override fun onLine(line: String) {
        sink += line
        downstream.onLine(line)
    }

    override fun onTask(taskPath: String) = downstream.onTask(taskPath)
}
