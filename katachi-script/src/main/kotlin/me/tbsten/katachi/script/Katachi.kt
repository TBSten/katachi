package me.tbsten.katachi.script

import java.nio.file.Path
import me.tbsten.katachi.script.command.CommandRunner
import me.tbsten.katachi.script.command.KatachiCommand
import me.tbsten.katachi.script.internal.RawCommand
import me.tbsten.katachi.script.option.Lang

/**
 * The `katachi` command, for a `.main.kts` that builds its subcommands instead of typing them.
 *
 * Every subcommand of `katachi` has a builder: `katachi.doctor`, `katachi.scaffold`, `katachi.lint`,
 * `katachi.agent.test` and so on, named as the command line spells them. Each starts from the defaults given here, and every option method
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
 *     katachi.doctor.lang(ja),
 *     katachi.scaffold.`test-framework`(kotest),
 * )
 * ```
 *
 * @property defaultPackage Puts `--package <defaultPackage>` on every [scaffold]. Null or empty leaves it unset.
 * @property katachiVersion Puts `--katachi <katachiVersion>` on every [scaffold]. Null leaves the choice to the command.
 * @property moduleDir Puts `--module-dir <moduleDir>` on every [scaffold].
 * @property lang Puts `--lang <lang>` on every command that takes it: [doctor].
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
    public val cwd: Path? = null,
    public val runner: CommandRunner = CommandRunner.InProcess,
    public val echo: Boolean = true,
) {
    /**
     * Any subcommand, written out as it is typed: `command("agent", "test", "--save", "x")` is
     * `katachi agent test --save x` on the command line.
     *
     * For a subcommand or an option newer than this `katachi-script`; every subcommand it knows has a builder.
     *
     * ## Example 1: run the check and keep its log under a name
     *
     * ```kt
     * katachi.command("agent", "test", "--save", "before-refactor").execute()
     * ```
     *
     * @param arguments Everything after `katachi`.
     */
    public fun command(vararg arguments: String): KatachiCommand = RawCommand(this, arguments.toList())
}
