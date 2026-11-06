package me.tbsten.katachi.script.internal

// Every subcommand's options, written once as values. The command line is parsed from them
// (parseArgs), and a typed builder for scripts can be generated from them later (KATACHI-256).
// The --help text stays hand-written prose; a test checks that it names every option here.

internal sealed interface OptionKind {
    /** Takes no value (`--force`). */
    data object Flag : OptionKind

    /** Takes any text (`--package com.example`). */
    data object Text : OptionKind

    /** Takes a path, relative to the current directory (`--workdir .local/install-katachi`). */
    data object Path : OptionKind

    /**
     * Takes one of [values] (`--test-framework kotest`). [typeName] names the set of values, so that
     * options taking the same values (scaffold `--test-framework`, preferences `testFramework`) share one type.
     */
    data class Choice(val values: List<String>, val typeName: String) : OptionKind
}

internal class OptionSpec(
    /** As typed, with the dashes: `--test-framework`. */
    val name: String,
    val kind: OptionKind,
    val required: Boolean = false,
    /** For a [OptionKind.Choice]: the value may be left out (`docs --api` and `docs --api katachi`). */
    val valueOptional: Boolean = false,
)

/** One positional argument: exactly one when [required], at most one otherwise; any number (at least one when [required]) when [variadic]. */
internal class PositionalSpec(val name: String, val required: Boolean = true, val variadic: Boolean = false)

internal class CommandSpec(
    val name: String,
    val options: List<OptionSpec> = emptyList(),
    val positionals: List<PositionalSpec> = emptyList(),
    /** `data get` / `data set`: the first argument picks one of these, which then reads the rest. */
    val subcommands: List<CommandSpec> = emptyList(),
    /** The subcommand used when the first argument is missing or an option (`answers --target report` is `answers show`). */
    val defaultSubcommand: String? = null,
    /** Everything after `--` is handed on untouched (`test -- --info`). */
    val passthrough: Boolean = false,
    /** Every argument is positional, even one that starts with `-` (`warn 4 "-5 lines"`, `add role --name X`). */
    val rawPositionals: Boolean = false,
) {
    fun option(name: String): OptionSpec? = options.firstOrNull { it.name == name }
}

private val LANG = OptionSpec("--lang", OptionKind.Choice(listOf("en", "ja"), "Lang"))
private val MODULE_DIR = OptionSpec("--module-dir", OptionKind.Path)
private val KATACHI = OptionSpec("--katachi", OptionKind.Text)
private val FORCE = OptionSpec("--force", OptionKind.Flag)
private val REFRESH = OptionSpec("--refresh", OptionKind.Flag)
private val TARGET = OptionSpec("--target", OptionKind.Choice(listOf("report", "check-list"), "Target"))
private val DATA_ID = OptionSpec("--id", OptionKind.Text)
private val DATA_TARGET = PositionalSpec("target")

internal val COMMAND_SPECS: List<CommandSpec> = listOf(
    CommandSpec("doctor", listOf(LANG)),
    CommandSpec(
        "init",
        listOf(OptionSpec("--workdir", OptionKind.Path), MODULE_DIR, KATACHI, OptionSpec("--offline", OptionKind.Flag), LANG, FORCE),
    ),
    CommandSpec(
        "scaffold",
        listOf(
            OptionSpec("--package", OptionKind.Text, required = true),
            KATACHI,
            OptionSpec("--kotlin", OptionKind.Text),
            OptionSpec("--no-konsist", OptionKind.Flag),
            MODULE_DIR,
            OptionSpec("--test-framework", OptionKind.Choice(listOf("junit5", "kotest"), "TestFramework")),
            OptionSpec("--deps", OptionKind.Choice(listOf("catalog", "literal"), "DependencyStyle")),
            FORCE,
            OptionSpec("--dry-run", OptionKind.Flag),
        ),
    ),
    CommandSpec(
        "data",
        subcommands = listOf(
            CommandSpec("get", listOf(DATA_ID), listOf(DATA_TARGET, PositionalSpec("key", required = false))),
            CommandSpec("set", listOf(DATA_ID), listOf(DATA_TARGET, PositionalSpec("json"))),
            CommandSpec("merge", listOf(DATA_ID), listOf(DATA_TARGET, PositionalSpec("json"))),
        ),
    ),
    CommandSpec(
        "docs",
        listOf(
            OptionSpec("--small", OptionKind.Flag),
            OptionSpec("--topics", OptionKind.Flag),
            OptionSpec("--topic", OptionKind.Text),
            OptionSpec("--api", OptionKind.Choice(listOf("katachi", "katachi-konsist"), "ApiModule"), valueOptional = true),
            REFRESH,
        ),
    ),
    CommandSpec("guide", listOf(REFRESH, LANG), listOf(PositionalSpec("section", required = false))),
    CommandSpec("test", listOf(OptionSpec("--save", OptionKind.Text)), passthrough = true),
    CommandSpec("check", positionals = listOf(PositionalSpec("item", variadic = true)), rawPositionals = true),
    CommandSpec("uncheck", positionals = listOf(PositionalSpec("item", variadic = true)), rawPositionals = true),
    // warn <step id> <text> | warn <step id> --clear
    CommandSpec("warn", positionals = listOf(PositionalSpec("step"), PositionalSpec("text")), rawPositionals = true),
    // add <kind> --<field> <value>...: the fields differ by kind (see `add <kind> --help`).
    CommandSpec("add", positionals = listOf(PositionalSpec("kind"), PositionalSpec("fields", required = false, variadic = true)), rawPositionals = true),
    CommandSpec(
        "preferences",
        subcommands = listOf(
            CommandSpec("show"),
            CommandSpec("set", positionals = listOf(PositionalSpec("keyValue", variadic = true)), rawPositionals = true),
        ),
        defaultSubcommand = "show",
    ),
    CommandSpec(
        "answers",
        subcommands = listOf(
            CommandSpec("show", listOf(TARGET)),
            CommandSpec("import", listOf(TARGET), listOf(PositionalSpec("json"))),
            CommandSpec("set", listOf(TARGET, OptionSpec("--note", OptionKind.Text)), listOf(PositionalSpec("id"), PositionalSpec("answer"))),
        ),
        defaultSubcommand = "show",
    ),
    CommandSpec("lint"),
    CommandSpec(
        "compare-violations",
        positionals = listOf(PositionalSpec("before", required = false), PositionalSpec("after", required = false)),
        rawPositionals = true,
    ),
    CommandSpec("verify"),
    CommandSpec("summary"),
)

internal fun commandSpec(name: String): CommandSpec = COMMAND_SPECS.first { it.name == name }

/** What [parseArgs] read: option values by name, the flags given, the positionals and what came after `--`. */
internal class ParsedArgs(
    /** The subcommand picked (`get` of `data get`); null for a command without subcommands. */
    val subcommand: String?,
    private val values: Map<String, String>,
    private val flags: Set<String>,
    val positionals: List<String>,
    val passthrough: List<String>,
) {
    fun value(name: String): String? = values[name]

    fun has(name: String): Boolean = name in flags || name in values

    fun withSubcommand(name: String): ParsedArgs = ParsedArgs(name, values, flags, positionals, passthrough)
}

/**
 * Reads [args] by [spec]. Stops on an unknown option, a missing value or a value outside a choice,
 * with the kit's wording (`<command>: 知らないオプションです`, `--x に値がありません`).
 * A required option is not checked here: each command says how to give it, with an example.
 */
internal fun parseArgs(spec: CommandSpec, args: List<String>): ParsedArgs {
    if (spec.subcommands.isNotEmpty()) {
        val first = args.firstOrNull()
        val picked = spec.subcommands.firstOrNull { it.name == first }
        val sub = picked
            ?: spec.defaultSubcommand?.takeIf { first == null || first.startsWith("-") }?.let { name -> spec.subcommands.first { it.name == name } }
            ?: run {
                val names = spec.subcommands.joinToString(" / ") { it.name }
                die("${spec.name} のサブコマンドは $names です（指定されたのは '${first.orEmpty()}'）", "The ${spec.name} subcommands are $names (got '${first.orEmpty()}')")
            }
        val inner = parseArgs(CommandSpec(spec.name, sub.options, sub.positionals, passthrough = sub.passthrough, rawPositionals = sub.rawPositionals), if (picked != null) args.drop(1) else args)
        return inner.withSubcommand(sub.name)
    }
    if (spec.rawPositionals) return ParsedArgs(null, emptyMap(), emptySet(), args, emptyList())
    val values = LinkedHashMap<String, String>()
    val flags = LinkedHashSet<String>()
    val positionals = mutableListOf<String>()
    var passthrough = emptyList<String>()
    var index = 0
    while (index < args.size) {
        val arg = args[index]
        if (arg == "--" && spec.passthrough) {
            passthrough = args.drop(index + 1)
            break
        }
        if (!arg.startsWith("-") || arg == "-") {
            positionals += arg
            index++
            continue
        }
        val option = spec.option(arg) ?: if (spec.passthrough) {
            die("${spec.name}: 知らないオプションです: $arg（Gradle に渡す引数は -- の後ろに）", "${spec.name}: unknown option: $arg (put arguments for Gradle after --)")
        } else {
            die("${spec.name}: 知らないオプションです: $arg", "${spec.name}: unknown option: $arg")
        }
        when (val kind = option.kind) {
            OptionKind.Flag -> {
                flags += arg
                index++
            }

            is OptionKind.Choice -> {
                val next = args.getOrNull(index + 1)
                if (option.valueOptional && (next == null || next !in kind.values)) {
                    flags += arg
                    index++
                    continue
                }
                val value = needValue(arg, next)
                if (value !in kind.values) {
                    val ja = if (kind.values.size == 2) "${kind.values[0]} か ${kind.values[1]} " else kind.values.joinToString(" / ") + " のどれか"
                    val en = if (kind.values.size == 2) "${kind.values[0]} or ${kind.values[1]}" else "one of " + kind.values.joinToString(" / ")
                    die("$arg は ${ja}です: $value", "$arg must be $en: $value")
                }
                values[arg] = value
                index += 2
            }

            OptionKind.Text, OptionKind.Path -> {
                values[arg] = needValue(arg, args.getOrNull(index + 1))
                index += 2
            }
        }
    }
    return ParsedArgs(null, values, flags, positionals, passthrough)
}
