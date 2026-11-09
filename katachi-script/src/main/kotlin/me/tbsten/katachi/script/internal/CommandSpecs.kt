package me.tbsten.katachi.script.internal

// Every subcommand's options, written once as values. The command line is parsed from them
// (parseArgs), and the typed builders scripts use (`katachi.scaffold.`package`(...)`) are generated
// from them by CommandBuildersSpec: edit a spec here, then rewrite the builders with
// `./gradlew :katachi-script:test --tests '*CommandBuildersSpec' -Dkatachi.golden.update=true`.
// The --help text stays hand-written prose; a test checks that it names every option here.

internal sealed interface OptionKind {
    /** Takes no value (`--force`). */
    data object Flag : OptionKind

    /** Takes any text (`--package com.example`). */
    data object Text : OptionKind

    /** Takes a path, relative to the current directory (`--workdir .local/install-katachi`). */
    data object Path : OptionKind

    /** Takes `yes` or `no` (`nestedGroups=yes`); a script passes a Boolean. */
    data object YesNo : OptionKind

    /** Takes one of the values of [type] (`--test-framework kotest`). */
    data class Choice(val type: ChoiceType) : OptionKind {
        val values: List<String> get() = type.values.map { it.value }
    }
}

/**
 * A set of values some options take. Options taking the same values (scaffold `--test-framework`,
 * preferences `testFramework`) share one, so a script uses one enum named [typeName] for both.
 */
internal class ChoiceType(
    val typeName: String,
    /** What the values pick, for the enum's KDoc: `The language ... records`. */
    val doc: String,
    val values: List<ChoiceValue>,
    /** Whether scripts get top-level values to write bare (`kotest`); off when a name would clash (`katachi`). */
    val bareNames: Boolean = true,
)

internal class ChoiceValue(val value: String, val doc: String)

/** Which [me.tbsten.katachi.script.Katachi] default an option starts from. */
internal enum class KatachiDefault(val property: String) {
    Package("defaultPackage"),
    Version("katachiVersion"),
    ModuleDir("moduleDir"),
    Lang("lang"),
}

internal class OptionSpec(
    /** As typed: `--test-framework`, or `nestedGroups` for a [keyValue] option. */
    val name: String,
    val kind: OptionKind,
    /** What the option does, after `Same as ... on the command line:` in the builder's KDoc. */
    val doc: String = "",
    /** The builder method's parameter name, and its `@param` text. */
    val param: String = "value",
    val paramDoc: String = "",
    /** The value the generated KDoc example passes; the example leaves the option out when null. Any text for a [OptionKind.Flag]. */
    val sample: String? = null,
    val required: Boolean = false,
    /** For a [OptionKind.Choice]: the value may be left out (`docs --api` and `docs --api katachi`). */
    val valueOptional: Boolean = false,
    /** May be given more than once, each adding a value (`add question --options a --options b`). */
    val repeatable: Boolean = false,
    /** Written `name=value` instead of `--name value` (`set nestedGroups=yes`). */
    val keyValue: Boolean = false,
    val katachiDefault: KatachiDefault? = null,
)

/** One positional argument: exactly one when [required], at most one otherwise; any number (at least one when [required]) when [variadic]. */
internal class PositionalSpec(
    val name: String,
    /** The `@param` text of the builder. */
    val doc: String = "",
    /** The value the generated KDoc example passes. */
    val sample: String = "",
    val required: Boolean = true,
    val variadic: Boolean = false,
)

/** Everything after `--` is handed on untouched (`test -- --info`); [method] is the builder method that adds to it. */
internal class PassthroughSpec(val method: String, val doc: String)

internal class CommandSpec(
    val name: String,
    val options: List<OptionSpec> = emptyList(),
    val positionals: List<PositionalSpec> = emptyList(),
    /** `data get` / `data set`: the first argument picks one of these, which then reads the rest. */
    val subcommands: List<CommandSpec> = emptyList(),
    /** The subcommand used when the first argument is missing or an option (`answers --target report` is `answers show`). */
    val defaultSubcommand: String? = null,
    val passthrough: PassthroughSpec? = null,
    /** Every argument is positional, even one that starts with `-`. */
    val rawPositionals: Boolean = false,
    /** What the command does, for the builder's KDoc: `runs the test module's tests and ...`. */
    val doc: String = "",
    /**
     * A command the katachi skill's steps run, typed `katachi agent <name>` (`katachi.agent.<name>` in a
     * script). The others are for people too and stay at the top: `katachi doctor`, `katachi scaffold`.
     */
    val agent: Boolean = false,
) {
    fun option(name: String): OptionSpec? = options.firstOrNull { it.name == name }

    /** As typed after `katachi`: `["agent", "test"]`, `["doctor"]`. */
    val path: List<String> get() = if (agent) listOf(AGENT_GROUP, name) else listOf(name)
}

/** The subcommand the [CommandSpec.agent] commands sit under: `katachi agent test`. */
internal const val AGENT_GROUP: String = "agent"

private val LANG_TYPE = ChoiceType(
    "Lang",
    "The language of the command's own messages",
    listOf(ChoiceValue("en", "English"), ChoiceValue("ja", "Japanese")),
)
private val TEST_FRAMEWORK_TYPE = ChoiceType(
    "TestFramework",
    "The test framework `katachi scaffold --test-framework` lays the test module out for",
    listOf(
        ChoiceValue("junit5", "JUnit 5"),
        ChoiceValue("kotest", "Kotest, with the test written as a `FunSpec` like `kotest-fun`"),
        // Kotest in one spec style each (KATACHI-301): `kotest-<style>` writes a `<Style>Spec`.
        ChoiceValue("kotest-fun", "Kotest `FunSpec`: `test(\"...\") { }`"),
        ChoiceValue("kotest-string", "Kotest `StringSpec`: `\"...\" { }`"),
        ChoiceValue("kotest-should", "Kotest `ShouldSpec`: `should(\"...\") { }`"),
        ChoiceValue("kotest-describe", "Kotest `DescribeSpec`: `describe(\"...\") { it(\"...\") { } }`"),
        ChoiceValue("kotest-behavior", "Kotest `BehaviorSpec`: `Given(\"...\") { Then(\"...\") { } }`"),
        ChoiceValue("kotest-word", "Kotest `WordSpec`: `\"...\" should { \"...\" { } }`"),
        ChoiceValue("kotest-free", "Kotest `FreeSpec`: `\"...\" { }`"),
        ChoiceValue("kotest-feature", "Kotest `FeatureSpec`: `feature(\"...\") { scenario(\"...\") { } }`"),
        ChoiceValue("kotest-expect", "Kotest `ExpectSpec`: `context(\"...\") { expect(\"...\") { } }`"),
        ChoiceValue("kotest-annotation", "Kotest `AnnotationSpec`: a `@Test` function, as in JUnit"),
    ),
)

/** What `scaffold --test-framework` and `preferences.testFramework` take: junit5, kotest and kotest-<style>. */
internal val TEST_FRAMEWORKS: List<String> = TEST_FRAMEWORK_TYPE.values.map { it.value }
private val DEPENDENCY_STYLE_TYPE = ChoiceType(
    "DependencyStyle",
    "How `katachi scaffold --deps` writes the dependencies of the test module",
    listOf(
        ChoiceValue("catalog", "Adds the libraries to `gradle/libs.versions.toml` and refers to them as `libs.katachi`"),
        ChoiceValue("literal", "Writes the coordinates into `build.gradle.kts`"),
    ),
)
private val LANG = OptionSpec(
    "--lang",
    OptionKind.Choice(LANG_TYPE),
    doc = "the language of the command's messages",
    param = "lang",
    sample = "ja",
    katachiDefault = KatachiDefault.Lang,
)
private val MODULE_DIR = OptionSpec(
    "--module-dir",
    OptionKind.Path,
    doc = "where the test module goes, relative to the Gradle root",
    param = "path",
    paramDoc = "Such as `static-analysis/katachi`.",
    katachiDefault = KatachiDefault.ModuleDir,
)
private val KATACHI = OptionSpec("--katachi", OptionKind.Text, param = "version", paramDoc = "Such as `0.4.0`.", katachiDefault = KatachiDefault.Version)
internal val COMMAND_SPECS: List<CommandSpec> = listOf(
    CommandSpec(
        "doctor",
        listOf(LANG),
        doc = "checks the JDK Gradle uses, the project's Kotlin version and whether git is there",
    ),
    CommandSpec(
        "scaffold",
        listOf(
            OptionSpec(
                "--package",
                OptionKind.Text,
                doc = "the package of the app",
                param = "name",
                paramDoc = "Such as `com.example.app`.",
                sample = "me.tbsten.katachi.sample",
                required = true,
                katachiDefault = KatachiDefault.Package,
            ),
            OptionSpec(
                "--katachi",
                OptionKind.Text,
                doc = "the katachi version to depend on. Without it, the version of this katachi command",
                param = "version",
                paramDoc = "Such as `0.4.0`.",
                katachiDefault = KatachiDefault.Version,
            ),
            OptionSpec(
                "--kotlin",
                OptionKind.Text,
                doc = "the Kotlin version. Without it, the one found in the project",
                param = "version",
                paramDoc = "Such as `2.2.21`.",
            ),
            OptionSpec("--no-konsist", OptionKind.Flag, doc = "lays the module out without katachi-konsist, so `ktFileContent { }` is not available"),
            MODULE_DIR,
            OptionSpec(
                "--test-framework",
                OptionKind.Choice(TEST_FRAMEWORK_TYPE),
                doc = "the framework the generated tests use",
                param = "framework",
                sample = "kotest",
            ),
            OptionSpec("--deps", OptionKind.Choice(DEPENDENCY_STYLE_TYPE), doc = "how the dependencies are written", param = "style"),
            OptionSpec("--force", OptionKind.Flag, doc = "overwrites an existing test module"),
            OptionSpec("--dry-run", OptionKind.Flag, doc = "prints what would be written, and writes nothing"),
        ),
        doc = "creates the test module and adds it to the build",
    ),
    CommandSpec(
        "test",
        agent = true,
        options = listOf(
            OptionSpec(
                "--save",
                OptionKind.Text,
                doc = "also copies the log to `tmp/test-<name>.log` of the working directory",
                param = "name",
                paramDoc = "Such as `before-refactor`.",
                sample = "before-refactor",
            ),
        ),
        passthrough = PassthroughSpec("gradle", "the arguments after `--`, handed to `./gradlew` as they are"),
        doc = "runs the tests of the test module and prints only a summary",
    ),
    CommandSpec("lint", doc = "lists what in the definition looks off: a Role with files of several kinds, flat groups and the like"),
    CommandSpec(
        "compare-violations",
        agent = true,
        positionals = listOf(
            PositionalSpec("before", doc = "The log before. Without it, `tmp/test-before-refactor.log` of the working directory.", sample = "before.log", required = false),
            PositionalSpec("after", doc = "The log after. Without it, `tmp/test-after-refactor.log` of the working directory.", sample = "after.log", required = false),
        ),
        rawPositionals = true,
        doc = "compares the violations of two test logs, as before and after a refactoring of the definition",
    ),
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
                die(Messages.CommandSpecs.subcommandsGot(spec.name, names, first.orEmpty()))
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
        if (arg == "--" && spec.passthrough != null) {
            passthrough = args.drop(index + 1)
            break
        }
        if (!arg.startsWith("-") || arg == "-") {
            positionals += arg
            index++
            continue
        }
        val option = spec.option(arg) ?: if (spec.passthrough != null) {
            die(Messages.CommandSpecs.unknownOptionPutArguments(spec.name, arg))
        } else {
            die(Messages.CommandSpecs.unknownOption(spec.name, arg))
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
                    die(Messages.CommandSpecs.valueMustBe(arg, Messages.CommandSpecs.oneOf(kind.values), value))
                }
                values[arg] = value
                index += 2
            }

            OptionKind.Text, OptionKind.Path, OptionKind.YesNo -> {
                values[arg] = needValue(arg, args.getOrNull(index + 1))
                index += 2
            }
        }
    }
    return ParsedArgs(null, values, flags, positionals, passthrough)
}
