package me.tbsten.katachi.script.internal

import java.io.OutputStream
import java.io.PrintStream
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolute
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.script.CommandRunner
import me.tbsten.katachi.script.Katachi
import me.tbsten.katachi.script.KatachiCommand
import me.tbsten.katachi.script.KatachiMissingCommandOptionException

/**
 * A subcommand, its positional arguments and its options, kept in the order they were first set so
 * the printed line stays stable. Each option is the arguments it adds: `["--force"]`,
 * `["--lang", "ja"]` or `["nestedGroups=yes"]`.
 */
internal class CommandLine(
    val subcommand: List<String>,
    private val positionals: List<String> = emptyList(),
    private val options: List<Pair<String, List<String>>> = emptyList(),
    private val extra: List<String> = emptyList(),
    private val passthrough: List<String> = emptyList(),
) {
    private fun copy(
        positionals: List<String> = this.positionals,
        options: List<Pair<String, List<String>>> = this.options,
        extra: List<String> = this.extra,
        passthrough: List<String> = this.passthrough,
    ) = CommandLine(subcommand, positionals, options, extra, passthrough)

    private fun replacing(option: String, tokens: List<String>): CommandLine {
        val first = options.indexOfFirst { it.first == option }
        if (first < 0) return copy(options = options + (option to tokens))
        val replaced = options.filterIndexed { index, it -> index == first || it.first != option }
            .map { if (it.first == option) option to tokens else it }
        return copy(options = replaced)
    }

    /** `--option value`, or `--option` alone when [value] is null. Replaces an earlier value. */
    fun with(option: String, value: String?): CommandLine = replacing(option, listOfNotNull(option, value))

    /** `key=value`. Replaces an earlier value. */
    fun withKeyValue(key: String, value: String): CommandLine = replacing(key, listOf("$key=$value"))

    /** `--option value` once for each of [values], after the ones given before. */
    fun withAppended(option: String, values: List<String>): CommandLine = copy(options = options + values.map { option to listOf(option, it) })

    fun withFlag(option: String, enabled: Boolean): CommandLine =
        if (enabled) with(option, null) else copy(options = options.filterNot { it.first == option })

    fun withNonEmpty(option: String, value: String?): CommandLine =
        if (value.isNullOrEmpty()) this else with(option, value)

    fun withPositionals(values: List<String>): CommandLine = copy(positionals = values)

    fun plus(arguments: List<String>): CommandLine = copy(extra = extra + arguments)

    fun plusPassthrough(arguments: List<String>): CommandLine = copy(passthrough = passthrough + arguments)

    fun has(option: String): Boolean = options.any { it.first == option } || option in extra

    val arguments: List<String>
        get() = subcommand + positionals + options.flatMap { it.second } + extra +
            (if (passthrough.isEmpty()) emptyList() else listOf("--") + passthrough)
}

/** The line a person would type: `katachi` and [arguments], each quoted only when a POSIX shell would split or expand it. */
internal fun commandText(arguments: List<String>): String =
    (listOf("katachi") + arguments).joinToString(" ", transform = ::shellQuote)

private val SHELL_SAFE = Regex("[A-Za-z0-9@%+=:,./_-]+")

internal fun shellQuote(argument: String): String =
    if (argument.matches(SHELL_SAFE)) argument else "'" + argument.replace("'", "'\\''") + "'"

internal fun Katachi.run(arguments: List<String>): Int {
    if (echo) println("$ ${commandText(arguments)}")
    return runner.run(arguments, cwd)
}

internal fun requireOption(line: CommandLine, option: String, declaredAt: DeclarationSite) {
    if (!line.has(option)) {
        throw KatachiMissingCommandOptionException(
            command = commandText(line.arguments),
            option = option,
            declaredAt = declaredAt,
        )
    }
}

internal object InProcessRunner : CommandRunner {
    override fun run(arguments: List<String>, cwd: Path?): Int {
        // Same streams as `main`: the messages are Japanese and a POSIX locale would turn them into '?'.
        val out = utf8(System.out)
        val err = utf8(System.err)
        val context = ScriptContext(
            cwd = (cwd ?: Path("")).absolute().normalize(),
            env = System.getenv(),
            out = out::println,
            err = err::println,
        )
        return runScript(arguments, context)
    }

    private fun utf8(stream: OutputStream): PrintStream = PrintStream(stream, true, Charsets.UTF_8)
}

internal class SubprocessRunner(private val executable: String) : CommandRunner {
    override fun run(arguments: List<String>, cwd: Path?): Int {
        val builder = ProcessBuilder(listOf(executable) + arguments).inheritIO()
        if (cwd != null) builder.directory(cwd.toFile())
        return builder.start().waitFor()
    }

    override fun toString(): String = "CommandRunner.subprocess($executable)"
}

internal class RawCommand(private val katachi: Katachi, override val arguments: List<String>) : KatachiCommand {
    override fun execute(): Int = katachi.run(arguments)

    override fun toString(): String = commandText(arguments)
}
