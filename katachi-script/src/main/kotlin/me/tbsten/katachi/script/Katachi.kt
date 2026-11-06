package me.tbsten.katachi.script

import java.nio.file.Path
import me.tbsten.katachi.script.internal.InProcessRunner
import me.tbsten.katachi.script.internal.RawCommand
import me.tbsten.katachi.script.internal.SubprocessRunner

/**
 * The `katachi` command, for a `.main.kts` that builds its subcommands instead of typing them.
 *
 * Every subcommand of `katachi` has a builder: `katachi.init`, `katachi.scaffold`,
 * `katachi.data.get(...)`, `katachi.add.role`, `katachi.preferences.set` and so on, named as the
 * command line spells them. Each starts from the defaults given here, and every option method
 * returns a new value, so one [Katachi] can hand out any number of commands. A command prints as
 * the line you would type, and [KatachiCommand.execute] runs it.
 *
 * ## Example 1: lay out the test module from a script
 *
 * ```kt
 * @file:DependsOn("me.tbsten.katachi:katachi-script:0.4.0")
 *
 * import me.tbsten.katachi.script.*
 *
 * val katachi = Katachi(defaultPackage = "com.example.app")
 *
 * executeCommands(
 *     katachi.init.lang(ja),
 *     katachi.scaffold.`test-framework`(kotest),
 * )
 * ```
 *
 * @property defaultPackage Puts `--package <defaultPackage>` on every [scaffold]. Null or empty leaves it unset.
 * @property katachiVersion Puts `--katachi <katachiVersion>` on every [init] and [scaffold]. Null leaves the choice to the command.
 * @property moduleDir Puts `--module-dir <moduleDir>` on every [init] and [scaffold].
 * @property lang Puts `--lang <lang>` on every command that takes it: [init], [doctor] and [guide].
 * @property workdir Puts `--workdir <workdir>` on every [init]: the working directory `init` makes. Null lets `init` pick one.
 * @property cwd Where the commands run. Null is the directory the script was started in.
 * @property runner How [KatachiCommand.execute] runs a command: [CommandRunner.InProcess] by default.
 * @property echo Whether [KatachiCommand.execute] prints `$ katachi ...` before it runs, as `make` does.
 * @constructor Takes the defaults. Every one of them can be left out.
 * @see KatachiCommand
 * @see commands
 * @featured
 */
public class Katachi(
    public val defaultPackage: String? = null,
    public val katachiVersion: String? = null,
    public val moduleDir: String? = null,
    public val lang: Lang? = null,
    public val workdir: String? = null,
    public val cwd: Path? = null,
    public val runner: CommandRunner = CommandRunner.InProcess,
    public val echo: Boolean = true,
) {
    /**
     * Any subcommand, written out as it is typed: `command("data", "get", "check-list")` is
     * `katachi data get check-list` on the command line.
     *
     * For a subcommand or an option newer than this `katachi-script`; every subcommand it knows has a builder.
     *
     * ## Example 1: read a value the check list recorded
     *
     * ```kt
     * katachi.command("data", "get", "check-list", "meta.moduleDir").execute()
     * ```
     *
     * @param arguments Everything after `katachi`.
     */
    public fun command(vararg arguments: String): KatachiCommand = RawCommand(this, arguments.toList())
}

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
