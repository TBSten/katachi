package me.tbsten.katachi.script.command

import java.nio.file.Path
import me.tbsten.katachi.script.Katachi
import me.tbsten.katachi.script.internal.InProcessRunner
import me.tbsten.katachi.script.internal.SubprocessRunner

/**
 * How [KatachiCommand.execute] runs a command line.
 *
 * ## Example 1: run the `katachi` on the PATH instead of in this JVM
 *
 * ```kt
 * val katachi = Katachi(runner = CommandRunner.subprocess())
 * ```
 *
 * @see Katachi.runner
 */
public fun interface CommandRunner {
    /**
     * Runs `katachi` with [arguments] in [cwd] and returns its exit code.
     *
     * @param arguments Everything after `katachi`.
     * @param cwd Where to run. Null is the current directory.
     */
    public fun run(arguments: List<String>, cwd: Path?): Int

    /**
     * The runners katachi provides.
     *
     * ## Example 1: pick one
     *
     * ```kt
     * Katachi(runner = CommandRunner.InProcess)
     * Katachi(runner = CommandRunner.subprocess("/opt/homebrew/bin/katachi"))
     * ```
     */
    public companion object {
        /**
         * Runs the command in this JVM, with the same `katachi-script` the script depends on.
         *
         * No second Kotlin start-up and no version drift from a separately installed `katachi`.
         * Every subcommand is written in Kotlin, so nothing else has to be installed.
         */
        public val InProcess: CommandRunner = InProcessRunner

        /**
         * Starts [executable] as a child process with the command line.
         *
         * @param executable The command to start: `katachi` on the PATH by default.
         */
        public fun subprocess(executable: String = "katachi"): CommandRunner = SubprocessRunner(executable)
    }
}
