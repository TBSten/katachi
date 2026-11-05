package me.tbsten.katachi.intellij.ide.mcp

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import me.tbsten.katachi.intellij.ide.definition.DefinitionModelService
import me.tbsten.katachi.intellij.ide.definition.DefinitionPorts
import me.tbsten.katachi.intellij.ide.sarif.ViolationsService
import me.tbsten.katachi.intellij.ide.sdkCall
import me.tbsten.katachi.intellij.model.DefinitionSnapshot
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.FileRoles
import me.tbsten.katachi.intellij.model.ViolationModel
import me.tbsten.katachi.intellij.model.ViolationsState
import me.tbsten.katachi.intellij.model.answer
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * What the katachi MCP tools answer, without a type of the MCP Server: [KatachiMcpToolset] only passes the arguments in.
 * Call it off the EDT (the definition service must not be asked there). A call that cannot be answered throws
 * [KatachiMcpToolException].
 *
 * ```kotlin
 * val roles = KatachiMcpTools.of(project).rolesOfFile("src/main/kotlin/com/example/HealthService.kt")
 * ```
 */
internal class KatachiMcpTools(
    private val ports: DefinitionPorts,
    /** Where a relative path that no definition's project root holds is resolved; the project's base directory. */
    private val projectBase: Path?,
    private val loadTimeout: Duration = LOAD_TIMEOUT,
) {
    suspend fun rolesOfFile(path: String): RolesOfFileResult {
        val answer = loadedAnswer()
        val roles = rolesOf(answer.service, answer.definitions, path) ?: throw KatachiMcpToolException.PathNotCovered(path)
        return roles.toDto(answer.staleReason ?: roles.freshness.staleReason)
    }

    suspend fun listRoles(module: String?): ListRolesResult {
        val answer = loadedAnswer()
        return ListRolesResult(answer.select(module).map { it.toRolesDto() }, answer.staleReason != null, answer.staleReason)
    }

    suspend fun listTemplates(module: String?): ListTemplatesResult {
        val answer = loadedAnswer()
        return ListTemplatesResult(answer.select(module).map { it.toTemplatesDto() }, answer.staleReason != null, answer.staleReason)
    }

    fun violations(path: String?, module: String?): ViolationsResult {
        val service = ports.violations() ?: throw KatachiMcpToolException.NoDefinitionModule()
        val state = service.state.value
        val ready = state.answer
        val runs = ready?.runs.orEmpty()
        var selected: List<ViolationModel> = runs
        if (module != null) {
            val gradlePath = gradlePathOf(module)
            val known = (ports.service()?.definitions.orEmpty().map { it.module.gradlePath } + runs.map { it.module.gradlePath }).distinct()
            if (gradlePath !in known) throw KatachiMcpToolException.UnknownModule(gradlePath, known)
            selected = selected.filter { it.module.gradlePath == gradlePath }
        }
        if (path != null) {
            val byPath = violationsAt(service, path).toSet()
            selected = selected.filter { it in byPath }
        }
        return ViolationsResult(
            state = when (state) {
                ViolationsState.NotRun -> "notRun"
                is ViolationsState.Ready -> "ready"
                is ViolationsState.Unreadable -> "unreadable"
            },
            readAt = ready?.readAt?.toString(),
            stale = ready?.stale == true,
            unreadableReason = (state as? ViolationsState.Unreadable)?.let { "${it.path}: ${it.reason}" },
            violations = selected.map { it.toDto() },
        )
    }

    /** The definitions to answer from, loading them first (up to [loadTimeout]) when nothing was read yet. */
    private suspend fun loadedAnswer(): LoadedAnswer {
        val service = ports.service() ?: throw KatachiMcpToolException.NoDefinitionModule()
        if (service.state.value == DefinitionState.NotLoaded) service.ensureLoaded()
        val settled = withTimeoutOrNull(loadTimeout) { service.state.first { it is DefinitionState.Ready || it is DefinitionState.Unavailable } }
        val state = settled ?: service.state.value
        val ready = state.answer ?: throw when (state) {
            is DefinitionState.Unavailable -> KatachiMcpToolException.DefinitionUnavailable(state.reason)
            else -> KatachiMcpToolException.LoadTimedOut(loadTimeout)
        }
        val notReady = when (state) {
            is DefinitionState.Ready -> null
            is DefinitionState.Unavailable -> "the definition cannot be loaded now (${state.reason}); this is the answer from before"
            else -> "loading the definition did not finish within ${loadTimeout.inWholeSeconds} seconds; this is the answer from before"
        }
        return LoadedAnswer(service, ready.definitions, notReady ?: ready.freshness.staleReason)
    }

    private fun rolesOf(service: DefinitionModelService, definitions: List<DefinitionSnapshot>, path: String): FileRoles? =
        candidatesOf(path, definitions).firstNotNullOfOrNull { candidate ->
            val file = sdkCall("find $candidate in the VFS") { LocalFileSystem.getInstance().findFileByNioFile(candidate) }.getOrNull()
            if (file != null) service.forFile(file) else service.forPath(candidate)
        }

    private fun violationsAt(service: ViolationsService, path: String): List<ViolationModel> {
        val definitions = ports.service()?.definitions.orEmpty()
        return candidatesOf(path, definitions).map { service.forPath(it) }.firstOrNull { it.isNotEmpty() }.orEmpty()
    }

    /** [path] itself when absolute, else [path] under each project root a definition knows, then under [projectBase]. */
    private fun candidatesOf(path: String, definitions: List<DefinitionSnapshot>): List<Path> {
        val given = try {
            Path.of(path)
        } catch (_: InvalidPathException) {
            throw KatachiMcpToolException.PathNotCovered(path)
        }
        if (given.isAbsolute) return listOf(given.normalize())
        val roots = definitions.mapNotNull { it.definition?.projectRoot } + definitions.map { it.module.linkedRootPath } + listOfNotNull(projectBase)
        return roots.distinct().map { it.resolve(given).normalize() }
    }

    private class LoadedAnswer(val service: DefinitionModelService, val definitions: List<DefinitionSnapshot>, val staleReason: String?) {
        fun select(module: String?): List<DefinitionSnapshot> {
            if (module == null) return definitions
            val gradlePath = gradlePathOf(module)
            return definitions.filter { it.module.gradlePath == gradlePath }
                .ifEmpty { throw KatachiMcpToolException.UnknownModule(gradlePath, definitions.map { it.module.gradlePath }) }
        }
    }

    companion object {
        val LOAD_TIMEOUT: Duration = 60.seconds

        fun of(project: Project): KatachiMcpTools = KatachiMcpTools(DefinitionPorts.of(project), project.basePath?.let { Path.of(it) })

        /** `arch` and `:arch` both name the module `:arch`. */
        private fun gradlePathOf(module: String): String = if (module.startsWith(":")) module else ":$module"
    }
}
