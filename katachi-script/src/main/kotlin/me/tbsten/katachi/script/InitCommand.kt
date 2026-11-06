// Generated from COMMAND_SPECS (internal/CommandSpecs.kt) by CommandBuildersSpec. Do not edit by hand:
// change the spec and run `./gradlew :katachi-script:test --tests '*CommandBuildersSpec' -Dkatachi.golden.update=true`.

package me.tbsten.katachi.script

import me.tbsten.katachi.script.internal.CommandLine
import me.tbsten.katachi.script.internal.commandText
import me.tbsten.katachi.script.internal.run

/**
 * Same as `katachi init` on the command line, starting from this [Katachi]'s defaults: makes the working directory and lays out the check list and the report.
 */
public val Katachi.init: InitCommand
    get() = InitCommand(this, CommandLine(listOf("init")).withNonEmpty("--workdir", this.workdir).withNonEmpty("--katachi", this.katachiVersion).withNonEmpty("--module-dir", this.moduleDir).withNonEmpty("--lang", this.lang?.value))

/**
 * The `katachi init` command: makes the working directory and lays out the check list and the report.
 *
 * Building one is writing `katachi init ...` in a shell: the methods are named after the
 * options of `katachi init --help` and add the same flag. Each returns a new
 * command; calling one twice keeps the last value.
 *
 * ## Example 1: build the command, print it and run it
 *
 * ```kt
 * val command = Katachi().init.offline().lang(ja)
 * println(command) // katachi init --offline --lang ja
 * command.execute()
 * ```
 *
 * The `execute()` above runs the same thing as this line in a shell:
 *
 * ```sh
 * katachi init --offline --lang ja
 * ```
 *
 * @see Katachi
 */
public class InitCommand internal constructor(
    private val katachi: Katachi,
    private val line: CommandLine,
) : KatachiCommand {
    private fun next(line: CommandLine) = InitCommand(katachi, line)

    /**
     * Same as `katachi init --workdir <path>` on the command line: the working directory. Without it, the command picks one.
     *
     * @param path Relative to where the command runs, or absolute.
     */
    public fun workdir(path: String): InitCommand = next(line.with("--workdir", path))

    /**
     * Same as `katachi init --katachi <version>` on the command line: the katachi version to record. Without it, the version of this katachi command.
     *
     * @param version Such as `0.4.0`.
     */
    public fun katachi(version: String): InitCommand = next(line.with("--katachi", version))

    /**
     * Same as `katachi init --module-dir <path>` on the command line: where the test module goes, relative to the Gradle root.
     *
     * @param path Such as `static-analysis/katachi`.
     */
    public fun `module-dir`(path: String): InitCommand = next(line.with("--module-dir", path))

    /**
     * Same as `katachi init --offline` on the command line: downloads nothing.
     *
     * @param enabled False takes the option off again.
     */
    public fun offline(enabled: Boolean = true): InitCommand = next(line.withFlag("--offline", enabled))

    /**
     * Same as `katachi init --lang <en|ja>` on the command line: the language of the check list, the report and the command's messages.
     *
     * @param lang [en] or [ja].
     */
    public fun lang(lang: Lang): InitCommand = next(line.with("--lang", lang.value))

    /**
     * Same as `katachi init --force` on the command line: lays out the check list and the report again even when they are filled in.
     *
     * @param enabled False takes the option off again.
     */
    public fun force(enabled: Boolean = true): InitCommand = next(line.withFlag("--force", enabled))

    /**
     * Appends [arguments] as they are, for an option this builder has no method for yet:
     * `arg("--new-option", "v")` is `katachi init ... --new-option v` on the command line.
     *
     * @param arguments Such as `"--new-option", "value"`.
     */
    public fun arg(vararg arguments: String): InitCommand = next(line.plus(arguments.toList()))

    override val arguments: List<String> get() = line.arguments

    override fun execute(): Int = katachi.run(arguments)

    /** The command line as typed in a shell: `katachi init --offline --lang ja`. Values a shell would split are quoted. */
    override fun toString(): String = commandText(arguments)
}
