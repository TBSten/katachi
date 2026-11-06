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

/** A subcommand and its options, kept in the order they were first set so the printed line stays stable. */
internal class CommandLine(
    val subcommand: List<String>,
    private val options: List<Pair<String, String?>> = emptyList(),
    private val extra: List<String> = emptyList(),
) {
    fun with(option: String, value: String?): CommandLine {
        val replaced = if (options.any { it.first == option }) {
            options.map { if (it.first == option) option to value else it }
        } else {
            options + (option to value)
        }
        return CommandLine(subcommand, replaced, extra)
    }

    fun withFlag(option: String, enabled: Boolean): CommandLine =
        if (enabled) with(option, null) else CommandLine(subcommand, options.filterNot { it.first == option }, extra)

    fun withNonEmpty(option: String, value: String?): CommandLine =
        if (value.isNullOrEmpty()) this else with(option, value)

    fun plus(arguments: List<String>): CommandLine = CommandLine(subcommand, options, extra + arguments)

    fun has(option: String): Boolean = options.any { it.first == option } || option in extra

    val arguments: List<String>
        get() = subcommand + options.flatMap { (option, value) -> listOfNotNull(option, value) } + extra
}

/** The line a person would type: `katachi` and [arguments], each quoted only when a POSIX shell would split or expand it. */
internal fun commandText(arguments: List<String>): String =
    (listOf("katachi") + arguments).joinToString(" ", transform = ::shellQuote)

private val SHELL_SAFE = Regex("[A-Za-z0-9@%+=:,./_-]+")

internal fun shellQuote(argument: String): String =
    if (argument.matches(SHELL_SAFE)) argument else "'" + argument.replace("'", "'\\''") + "'"

internal fun Katachi.run(arguments: List<String>): Int {
    if (echo) println("$ ${commandText(arguments)}")
    return runner.run(arguments, workingDirectory)
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
    override fun run(arguments: List<String>, workingDirectory: Path?): Int {
        // Same streams as `main`: the messages are Japanese and a POSIX locale would turn them into '?'.
        val out = utf8(System.out)
        val err = utf8(System.err)
        val context = ScriptContext(
            cwd = (workingDirectory ?: Path("")).absolute().normalize(),
            env = System.getenv(),
            out = out::println,
            err = err::println,
        )
        return runScript(arguments, context)
    }

    private fun utf8(stream: OutputStream): PrintStream = PrintStream(stream, true, Charsets.UTF_8)
}

internal class SubprocessRunner(private val executable: String) : CommandRunner {
    override fun run(arguments: List<String>, workingDirectory: Path?): Int {
        val builder = ProcessBuilder(listOf(executable) + arguments).inheritIO()
        if (workingDirectory != null) builder.directory(workingDirectory.toFile())
        return builder.start().waitFor()
    }

    override fun toString(): String = "CommandRunner.subprocess($executable)"
}

internal class RawCommand(private val katachi: Katachi, override val arguments: List<String>) : KatachiCommand {
    override fun execute(): Int = katachi.run(arguments)

    override fun toString(): String = commandText(arguments)
}
