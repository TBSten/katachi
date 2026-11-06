package me.tbsten.katachi.script

import me.tbsten.katachi.script.internal.CommandLine
import me.tbsten.katachi.script.internal.commandText
import me.tbsten.katachi.script.internal.run

/**
 * `katachi init`: makes the working directory and lays out the check list and the report.
 *
 * The methods are named after the options of `katachi init --help`. Each returns a new
 * command; calling one twice keeps the last value.
 *
 * ## Example 1: set up offline, in Japanese
 *
 * ```kt
 * val init = Katachi().init.offline().lang(ja)
 * println(init) // katachi init --offline --lang ja
 * init.execute()
 * ```
 *
 * @see Katachi.init
 */
public class InitCommand internal constructor(
    private val katachi: Katachi,
    private val line: CommandLine,
) : KatachiCommand {
    private fun next(line: CommandLine) = InitCommand(katachi, line)

    /**
     * `--workdir`: the working directory. Without it, the command picks one.
     *
     * @param path Relative to where the command runs, or absolute.
     */
    public fun workdir(path: String): InitCommand = next(line.with("--workdir", path))

    /**
     * `--katachi`: the katachi version to record. Without it, the latest release.
     *
     * @param version Such as `0.4.0`.
     */
    public fun katachi(version: String): InitCommand = next(line.with("--katachi", version))

    /**
     * `--offline`: downloads nothing.
     *
     * @param enabled False takes the option off again.
     */
    public fun offline(enabled: Boolean = true): InitCommand = next(line.withFlag("--offline", enabled))

    /**
     * `--force`: lays out the check list and the report again even when they are filled in.
     *
     * @param enabled False takes the option off again.
     */
    public fun force(enabled: Boolean = true): InitCommand = next(line.withFlag("--force", enabled))

    /**
     * `--lang`: the language of the check list, the report and the command's messages.
     *
     * @param lang [ja] or [en].
     */
    public fun lang(lang: Lang): InitCommand = next(line.with("--lang", lang.value))

    /**
     * `--module-dir`: where the test module will go, relative to the Gradle root.
     *
     * @param path Such as `static-analysis/katachi`.
     */
    public fun `module-dir`(path: String): InitCommand = next(line.with("--module-dir", path))

    /**
     * Appends [arguments] as they are, for an option this builder has no method for yet.
     *
     * @param arguments Such as `"--new-option", "value"`.
     */
    public fun arg(vararg arguments: String): InitCommand = next(line.plus(arguments.toList()))

    override val arguments: List<String> get() = line.arguments

    override fun execute(): Int = katachi.run(arguments)

    /** The command line as typed in a shell: `katachi init --lang ja`. Values a shell would split are quoted. */
    override fun toString(): String = commandText(arguments)
}
