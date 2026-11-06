package me.tbsten.katachi.intellij.data.script

import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Asks the `katachi` that [locate] finds for the jars of the `*.katachi.kts` definition. */
internal class ScriptClasspathProbe(
    private val locate: () -> Path?,
    private val run: (List<String>) -> ProcessOutcome,
    private val exists: (Path) -> Boolean,
) {
    fun probe(): ScriptClasspath {
        val cli = locate() ?: return ScriptClasspath.CliNotFound
        val outcome = try {
            run(listOf(cli.toString(), "internal", "script-classpath"))
        } catch (failure: IOException) {
            return ScriptClasspath.Malformed("could not start $cli: ${failure.message}")
        }
        return ScriptClasspathInterpreter.interpret(outcome, exists)
    }
}

/**
 * Runs a command and waits at most [timeoutSeconds] for it. katachi is a Kotlin script run by `kotlin`, so the
 * first run after an install compiles it: the wait is long, and it is never spent on the UI thread.
 */
internal class TimedProcessRunner(private val timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS) : (List<String>) -> ProcessOutcome {
    override fun invoke(command: List<String>): ProcessOutcome {
        val process = ProcessBuilder(command).start()
        process.outputStream.close()
        // Both streams are drained while waiting: a full pipe would block the process until the timeout.
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val readers = listOf(
            thread(isDaemon = true, name = "katachi script-classpath stdout") { stdout.append(process.inputStream.bufferedReader().readText()) },
            thread(isDaemon = true, name = "katachi script-classpath stderr") { stderr.append(process.errorStream.bufferedReader().readText()) },
        )
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return ProcessOutcome(exitCode = null, stdout = "", stderr = "")
        }
        readers.forEach { it.join(READER_JOIN_MILLIS) }
        return ProcessOutcome(process.exitValue(), stdout.toString(), stderr.toString())
    }

    companion object {
        const val DEFAULT_TIMEOUT_SECONDS: Long = 60L
        private const val READER_JOIN_MILLIS: Long = 5_000L
    }
}
