package me.tbsten.katachi.intellij.data.gradle

import java.nio.file.Path

/** One task of a Gradle run, with its `--arg key=value` options in order. */
internal data class GradleTaskInvocation(
    val taskPath: String,
    val args: List<Pair<String, String>> = emptyList(),
)

/**
 * One Gradle build to run in [linkedRootPath]: every task of [tasks] on one command line, as
 * loading several definition modules does (E-05).
 */
internal data class GradleRunRequest(
    val linkedRootPath: Path,
    val tasks: List<GradleTaskInvocation>,
) {
    /**
     * What goes into `ExternalSystemTaskExecutionSettings.taskNames`: each task path followed by
     * `--arg` and `key=value` as separate elements, quoted only where it reaches the platform, which splits on spaces again (ExternalSystemGradleTaskRunner).
     */
    val taskNames: List<String>
        get() = tasks.flatMap { task ->
            listOf(task.taskPath) + task.args.flatMap { (key, value) -> listOf("--arg", "$key=$value") }
        }

    /** The same run as a shell command, for "Copy command": `./gradlew :arch:katachiTemplate --arg ...`. */
    val commandLine: String
        get() = (listOf("./gradlew") + taskNames.map(::shellQuoted)).joinToString(" ")
}

private val SHELL_SAFE = Regex("""[A-Za-z0-9_./:=@%+,-]+""")

/** [word] as one POSIX shell word: as it is when safe, else single-quoted. */
internal fun shellQuoted(word: String): String =
    if (word.isNotEmpty() && SHELL_SAFE.matches(word)) word else "'" + word.replace("'", "'\\''") + "'"
