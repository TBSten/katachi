package me.tbsten.katachi.intellij.ide.mcp

import me.tbsten.katachi.intellij.model.DefinitionUnavailableReason
import kotlin.time.Duration

/**
 * A tool call that cannot be answered for a reason the caller can act on. [KatachiMcpToolset] turns its message into the
 * MCP error text, so every message is English ASCII in the form "katachi: <what happened>. <what to do>".
 */
internal sealed class KatachiMcpToolException(message: String) : Exception(message) {
    class NoDefinitionModule : KatachiMcpToolException(
        "katachi: this project has no katachi definition module. " +
            "Apply the katachi Gradle plugin (id(\"me.tbsten.katachi\")) to a module that declares architecture { }, sync the Gradle project, then call this tool again.",
    )

    class DefinitionUnavailable(val reason: DefinitionUnavailableReason) : KatachiMcpToolException(messageOf(reason))

    class LoadTimedOut(val timeout: Duration) : KatachiMcpToolException(
        "katachi: the katachi definition did not finish loading within ${timeout.inWholeSeconds} seconds. " +
            "Wait for Gradle to finish in the IDE, then call this tool again.",
    )

    class PathNotCovered(val path: String) : KatachiMcpToolException(
        "katachi: no katachi definition covers $path. " +
            "Pass a path inside the project a definition module checks, relative to the project root or absolute.",
    )

    class UnknownModule(val module: String, val known: List<String>) : KatachiMcpToolException(
        "katachi: $module is not a katachi definition module. " +
            "Pass one of ${known.joinToString(", ").ifEmpty { "(none loaded)" }}, or leave module out to read every one.",
    )

    class ModuleRequired(val known: List<String>) : KatachiMcpToolException(
        "katachi: this project has more than one katachi definition module, so the tool cannot pick one. " +
            "Pass module as one of ${known.joinToString(", ")}.",
    )

    class Busy : KatachiMcpToolException(
        "katachi: another generation is running in the IDE (the katachi tool window, an editor notification or another MCP call). " +
            "Wait for it to finish, then call this tool again.",
    )

    class GenerateTurnedOff : KatachiMcpToolException(
        "katachi: generating files over MCP is turned off. Turn on '$ALLOW_GENERATE_SETTING' in Settings | Tools | katachi.",
    )

    class UnknownTemplates(val unknown: List<String>, val known: List<String>) : KatachiMcpToolException(
        "katachi: ${unknown.joinToString(", ")} ${if (unknown.size == 1) "is not a template" else "are not templates"} of this module. " +
            "Pass ids as katachi_list_templates gives them: ${known.joinToString(", ").ifEmpty { "(none)" }}.",
    )

    class InvalidArguments(val problems: List<String>) : KatachiMcpToolException(
        "katachi: the arguments cannot generate the templates: ${problems.joinToString("; ")}. " +
            "Fix them and call this tool again; katachi_list_templates lists the parameters. Nothing was written.",
    )

    class ReloadFailed(val failure: LoadFailureDto) : KatachiMcpToolException(
        "katachi: reading the katachi definition before generating failed (${failure.kind}). " +
            "Call katachi_refresh_definition for the details and fix the definition; nothing was written.",
    )

    class TimedOut(val what: String, val timeout: Duration) : KatachiMcpToolException(
        "katachi: $what did not finish within $timeout. " +
            "Gradle may still be running in the IDE; wait for it, then call this tool again.",
    )

    companion object {
        /** The English text of the setting, `settings.mcpAllowGenerate`: the error is English whatever language the IDE shows. */
        const val ALLOW_GENERATE_SETTING: String = "Allow AI agents to generate files from templates over MCP"

        private fun messageOf(reason: DefinitionUnavailableReason): String = when (reason) {
            DefinitionUnavailableReason.NotSynced ->
                "katachi: the Gradle project is not synced yet. Sync the Gradle project in the IDE, then call this tool again."
            DefinitionUnavailableReason.NoDefinition ->
                "katachi: the katachi modules of this project declare no architecture definition. " +
                    "Declare architecture { } in the definition module, then call this tool again."
            DefinitionUnavailableReason.KatachiTooOld ->
                "katachi: the katachi Gradle plugin of this project is older than 0.3.0 and has no architecture definition. " +
                    "Update katachi to 0.3.0 or later."
            DefinitionUnavailableReason.LoadFailed ->
                "katachi: the katachi definition could not be loaded. " +
                    "Fix the error the katachi tool window shows (or run Reload Definition there), then call this tool again."
            DefinitionUnavailableReason.LoadingWithoutUserDisabled ->
                "katachi: the katachi definition is not loaded, and loading it automatically is turned off. " +
                    "Run Reload Definition in the katachi tool window, or turn on automatic loading in Settings | Tools | katachi."
        }
    }
}
