package me.tbsten.katachi.intellij.ide.mcp

import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.mcpFail
import com.intellij.mcpserver.project
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import me.tbsten.katachi.intellij.ide.mustPropagate

/**
 * The katachi tools of the JetBrains MCP Server (registered by katachi-mcp.xml). Each function only takes the arguments
 * and the project of the call and hands them to [KatachiMcpTools]; the names and descriptions are what an AI agent reads.
 *
 * ```kotlin
 * // What the MCP Server does for a `tools/call` of katachi_list_roles:
 * withContext(McpCallAdditionalDataElement(callInfo)) { KatachiMcpToolset().listRoles(module = ":arch") }
 * ```
 */
class KatachiMcpToolset : McpToolset {
    @McpTool(name = "katachi_roles_of_file")
    @McpDescription(
        "Tells which katachi roles hold a file of the project, as the architecture definition says: the roles, the layout entry and template of each, and, for a file no role holds, why (outcome) and the places it could move to (nearby).",
    )
    suspend fun rolesOfFile(
        @McpDescription("The file, relative to the project root or absolute. It need not exist.") path: String,
    ): RolesOfFileResult = call { it.rolesOfFile(path) }

    @McpTool(name = "katachi_list_roles")
    @McpDescription("Lists the roles the katachi architecture definition declares, with their groups and layout entries.")
    suspend fun listRoles(
        @McpDescription("The Gradle path of a katachi definition module, such as :arch. Leave out for every module.") module: String? = null,
    ): ListRolesResult = call { it.listRoles(module) }

    @McpTool(name = "katachi_list_templates")
    @McpDescription(
        "Lists the katachi templates (and templateSets) the definition declares: their parameters, the files they write and a katachiTemplate command that runs them.",
    )
    suspend fun listTemplates(
        @McpDescription("The Gradle path of a katachi definition module, such as :arch. Leave out for every module.") module: String? = null,
    ): ListTemplatesResult = call { it.listTemplates(module) }

    @McpTool(name = "katachi_violations")
    @McpDescription(
        "Lists the violations of the last katachi architecture check, as its SARIF report says. It does not run the check; state is notRun when no report exists yet.",
    )
    suspend fun violations(
        @McpDescription("Only the violations of this file, relative to the project root or absolute.") path: String? = null,
        @McpDescription("Only the violations of this katachi definition module, such as :arch.") module: String? = null,
    ): ViolationsResult = call { it.violations(path, module) }

    @McpTool(name = "katachi_refresh_definition")
    @McpDescription(
        "Runs Gradle to read the katachi architecture definition again, waits for it, and tells what it left: ready, or unavailable with the reason, the mistakes of the definition and why the reload failed. Call it after editing the definition module.",
    )
    suspend fun refreshDefinition(
        @McpDescription("The Gradle path of the katachi definition module to read, such as :arch. Leave out for every module.") module: String? = null,
    ): RefreshDefinitionResult = call { it.refreshDefinition(module) }

    @McpTool(name = "katachi_run_check")
    @McpDescription(
        "Saves the open files and runs the katachi architecture check (the definition module's test task) through Gradle, waits for it, and gives the outcome with the first violations. katachi_violations lists every one.",
    )
    suspend fun runCheck(
        @McpDescription("The Gradle path of the katachi definition module to check, such as :arch. Needed when the project has more than one.") module: String? = null,
    ): RunCheckResult = call { it.runCheck(module) }

    private suspend fun <T> call(block: suspend (KatachiMcpTools) -> T): T {
        val project = currentCoroutineContext().project
        return try {
            withContext(Dispatchers.Default) { block(KatachiMcpTools.of(project)) }
        } catch (failure: KatachiMcpToolException) {
            mcpFail(failure.message.orEmpty())
        } catch (failure: Throwable) {
            if (failure.mustPropagate) throw failure
            LOG.warn("A katachi MCP tool failed", failure)
            mcpFail(unexpectedFailureText(failure))
        }
    }

    private companion object {
        val LOG = logger<KatachiMcpToolset>()
    }
}

internal fun unexpectedFailureText(failure: Throwable): String =
    "katachi: the tool failed unexpectedly (${failure.javaClass.simpleName}). " +
        "This is a bug of the katachi IDE plugin; report it at https://github.com/TBSten/katachi/issues with idea.log."
