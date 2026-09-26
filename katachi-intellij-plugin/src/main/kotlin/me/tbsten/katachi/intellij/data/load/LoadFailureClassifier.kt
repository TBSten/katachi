package me.tbsten.katachi.intellij.data.load

import me.tbsten.katachi.intellij.data.gradle.stripAnsi
import me.tbsten.katachi.intellij.model.GradleFailure

private const val ARCHITECTURE_NOT_SET = "katachi { architecture = ... } is not set"
private val CANNOT_LOCATE_TASKS = Regex("""Cannot locate tasks that match '([^']+)'""")
private val TASK_NOT_FOUND = Regex("""Task '([^']+)' not found in (?:root )?project '([^']*)'""")
private val PROJECT_NOT_FOUND = Regex("""Project '([^']+)' not found in (?:root )?project""")
private val COMPILE_TASK_FAILED = Regex("""Execution failed for task '[^']*:compile\w*Kotlin\w*'""")
private val KATACHI_MAIN_EXCEPTION = Regex("""^Exception in thread "main" (me\.tbsten\.katachi\.[\w.$]+)(?:: (.*))?$""")
private val STACK_FRAME = Regex("""^\s+at .+|^\s*\.\.\. \d+ more$""")

/** How many trailing lines "Details" shows for a failure it cannot name. */
private const val TAIL_LINES = 15

/**
 * Names the cause of a failed Gradle run from its output (spec 02 "エラー"): the definition does
 * not compile, the task is unknown (stale sync), `architecture` is not set, katachi rejected the
 * command line, or something else. Used for loading and for a generation that never reached katachi.
 */
internal fun classifyGradleFailure(output: List<String>): GradleFailure {
    val lines = output.map(::stripAnsi)

    lines.indexOfFirst { ARCHITECTURE_NOT_SET in it }.takeIf { it >= 0 }?.let { index ->
        return GradleFailure.ArchitectureNotSet(paragraphFrom(lines, index))
    }

    val locate = lines.firstNotNullOfOrNull { CANNOT_LOCATE_TASKS.find(it) }
    val taskNotFound = lines.firstNotNullOfOrNull { TASK_NOT_FOUND.find(it) }
    val projectNotFound = lines.firstNotNullOfOrNull { PROJECT_NOT_FOUND.find(it) }
    if (locate != null || taskNotFound != null || projectNotFound != null) {
        val taskPath = locate?.groupValues?.get(1) ?: taskNotFound?.let { taskPathOf(it.groupValues[2], it.groupValues[1]) }
        val details = lines.filter { line -> listOf(locate, taskNotFound, projectNotFound).any { it != null && it.value in line } }
        return GradleFailure.TaskNotFound(taskPath, details)
    }

    val compileErrors = lines.filter { it.startsWith("e: ") }
    if (compileErrors.isNotEmpty() || lines.any { COMPILE_TASK_FAILED.containsMatchIn(it) }) {
        return GradleFailure.CompilationFailed(compileErrors.ifEmpty { tailOf(lines) })
    }

    lines.withIndex().firstNotNullOfOrNull { (index, line) -> KATACHI_MAIN_EXCEPTION.find(line)?.let { index to it } }
        ?.let { (index, match) ->
            val message = listOfNotNull(match.groupValues[2].takeIf { it.isNotEmpty() })
            val rest = lines.drop(index + 1).takeWhile { !STACK_FRAME.matches(it) && !it.startsWith("Caused by:") }
            return GradleFailure.ProcessorRejected(match.groupValues[1], (message + rest).dropLastWhile { it.isBlank() })
        }

    return GradleFailure.Other(tailOf(lines))
}

private fun taskPathOf(projectPath: String, taskName: String): String =
    if (taskName.startsWith(":")) taskName else if (projectPath == ":" || projectPath.isEmpty()) ":$taskName" else "$projectPath:$taskName"

/** The line at [index] and the ones after it, up to a blank line. */
private fun paragraphFrom(lines: List<String>, index: Int): List<String> =
    lines.drop(index).takeWhile { it.isNotBlank() }

private fun tailOf(lines: List<String>): List<String> = lines.dropLastWhile { it.isBlank() }.takeLast(TAIL_LINES)
