package me.tbsten.katachi.intellij.data.script

import java.nio.file.Path

/** What the ▶ of a `*.katachi.kts` (KATACHI-100) asks of the katachi CLI: the same as `katachi <file>.katachi.kts` in a terminal. */
internal object ScriptRun {
    /**
     * Whether a script gets a ▶: the file is a `*.katachi.kts` and the installed CLI has given its definition. Without
     * a CLI, with one from before `*.katachi.kts` or with an answer that cannot be read, there is nothing to run it with,
     * and the banner above the script says why. While the CLI has not answered yet ([answer] is null) it is not shown either.
     */
    fun runnable(fileName: String, answer: ScriptClasspath?): Boolean = isKatachiScript(fileName) && answer is ScriptClasspath.Found

    fun command(cli: Path, scriptPath: String): List<String> = listOf(cli.toString(), scriptPath)

    /** The directory to run in: the project's, where the script's relative paths are meant from; the script's own when there is no project directory. */
    fun workingDirectory(projectBasePath: String?, scriptPath: String): String? =
        projectBasePath ?: scriptPath.substringBeforeLast('/', missingDelimiterValue = "").ifEmpty { null }
}
