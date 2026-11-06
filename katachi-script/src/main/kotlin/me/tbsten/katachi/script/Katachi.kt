package me.tbsten.katachi.script

import java.nio.file.Path
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.script.internal.CommandLine
import me.tbsten.katachi.script.internal.InProcessRunner
import me.tbsten.katachi.script.internal.RawCommand
import me.tbsten.katachi.script.internal.SubprocessRunner

/**
 * The `katachi` command, for a `.main.kts` that builds its subcommands instead of typing them.
 *
 * Each subcommand ([init], [scaffold]) starts from the defaults given here, and every option
 * method returns a new value, so one [Katachi] can hand out any number of commands. A command
 * prints as the line you would type, and [KatachiCommand.execute] runs it.
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
 * @property defaultPackage The `--package` every [scaffold] starts with. Null or empty leaves it unset.
 * @property katachiVersion The `--katachi` every [init] and [scaffold] starts with. Null leaves the choice to the command.
 * @property moduleDir The `--module-dir` every [init] and [scaffold] starts with.
 * @property lang The `--lang` every [init] starts with.
 * @property workingDirectory Where the commands run. Null is the directory the script was started in.
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
    public val workingDirectory: Path? = null,
    public val runner: CommandRunner = CommandRunner.InProcess,
    public val echo: Boolean = true,
) {
    /** `katachi init` with this [Katachi]'s defaults: makes the working directory and lays out the check list. */
    public val init: InitCommand
        get() = InitCommand(
            katachi = this,
            line = CommandLine(listOf("init"))
                .withNonEmpty("--katachi", katachiVersion)
                .withNonEmpty("--module-dir", moduleDir)
                .withNonEmpty("--lang", lang?.value),
        )

    /** `katachi scaffold` with this [Katachi]'s defaults: creates the test module and adds it to the build. */
    public val scaffold: ScaffoldCommand
        get() = ScaffoldCommand(
            katachi = this,
            line = CommandLine(listOf("scaffold"))
                .withNonEmpty("--package", defaultPackage)
                .withNonEmpty("--katachi", katachiVersion)
                .withNonEmpty("--module-dir", moduleDir),
            declaredAt = DeclarationSite.capture(),
        )

    /**
     * Any subcommand, written out as it is typed.
     *
     * For the subcommands that have no builder of their own yet.
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
     * Runs `katachi` with [arguments] in [workingDirectory] and returns its exit code.
     *
     * @param arguments Everything after `katachi`.
     * @param workingDirectory Where to run. Null is the current directory.
     */
    public fun run(arguments: List<String>, workingDirectory: Path?): Int

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
         * Subcommands not yet written in Kotlin still go to `katachi-install.sh`, which is found
         * through `KATACHI_HOME` or in the working directory `init` made.
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
