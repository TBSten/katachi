package me.tbsten.katachi.intellij.data.gradle

import java.nio.file.Path

internal data class GradleTaskInvocation(
    val taskPath: String,
    val args: List<Pair<String, String>> = emptyList(),
)

internal data class GradleRunRequest(
    val linkedRootPath: Path,
    val tasks: List<GradleTaskInvocation>,
) {
    val taskNames: List<String>
        get() = tasks.flatMap { task ->
            listOf(task.taskPath) + task.args.flatMap { (key, value) -> listOf("--arg", "$key=$value") }
        }

    val commandLine: String
        get() = (listOf("./gradlew") + taskNames.map(::shellQuoted)).joinToString(" ")
}

private val SHELL_SAFE = Regex("""[A-Za-z0-9_./:=@%+,-]+""")

internal fun shellQuoted(word: String): String =
    if (word.isNotEmpty() && SHELL_SAFE.matches(word)) word else "'" + word.replace("'", "'\\''") + "'"
