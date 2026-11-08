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
    Workdir("workdir"),
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
    /** Written `name=value` instead of `--name value` (`preferences set nestedGroups=yes`). */
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
    /** Every argument is positional, even one that starts with `-` (`warn 4 "-5 lines"`, `add role --name X`). */
    val rawPositionals: Boolean = false,
    /** What the command does, for the builder's KDoc: `makes the working directory and ...`. */
    val doc: String = "",
    /**
     * A step of the install guide an AI agent follows, typed `katachi agent <name>` (`katachi.agent.<name>` in a
     * script). The others are for people too and stay at the top: `katachi doctor`, `katachi scaffold`.
     */
    val agent: Boolean = false,
) {
    fun option(name: String): OptionSpec? = options.firstOrNull { it.name == name }

    /** As typed after `katachi`: `["agent", "init"]`, `["doctor"]`. */
    val path: List<String> get() = if (agent) listOf(AGENT_GROUP, name) else listOf(name)
}

/** The subcommand the [CommandSpec.agent] commands sit under: `katachi agent init`. */
internal const val AGENT_GROUP: String = "agent"

private val LANG_TYPE = ChoiceType(
    "Lang",
    "The language `katachi agent init --lang` records for the check list, the report and the command's own messages",
    listOf(ChoiceValue("en", "English"), ChoiceValue("ja", "Japanese")),
)
private val TEST_FRAMEWORK_TYPE = ChoiceType(
    "TestFramework",
    "The test framework `katachi scaffold --test-framework` lays the test module out for",
    listOf(ChoiceValue("junit5", "JUnit 5"), ChoiceValue("kotest", "Kotest")),
)
private val KOTEST_STYLE_TYPE = ChoiceType(
    "KotestStyle",
    "The spec style `katachi scaffold --kotest-style` writes the Kotest test in",
    listOf(
        ChoiceValue("fun-spec", "`FunSpec`, the default: `test(\"...\") { }`"),
        ChoiceValue("string-spec", "`StringSpec`: `\"...\" { }`"),
        ChoiceValue("should-spec", "`ShouldSpec`: `should(\"...\") { }`"),
        ChoiceValue("describe-spec", "`DescribeSpec`: `describe(\"...\") { it(\"...\") { } }`"),
        ChoiceValue("behavior-spec", "`BehaviorSpec`: `Given(\"...\") { Then(\"...\") { } }`"),
        ChoiceValue("word-spec", "`WordSpec`: `\"...\" should { \"...\" { } }`"),
        ChoiceValue("free-spec", "`FreeSpec`: `\"...\" { }`"),
        ChoiceValue("feature-spec", "`FeatureSpec`: `feature(\"...\") { scenario(\"...\") { } }`"),
        ChoiceValue("expect-spec", "`ExpectSpec`: `context(\"...\") { expect(\"...\") { } }`"),
        ChoiceValue("annotation-spec", "`AnnotationSpec`: a `@Test` function, as in JUnit"),
    ),
    // Bare names such as `free-spec` are not Kotlin identifiers; scripts write `KotestStyle.FreeSpec`.
    bareNames = false,
)
private val DEPENDENCY_STYLE_TYPE = ChoiceType(
    "DependencyStyle",
    "How `katachi scaffold --deps` writes the dependencies of the test module",
    listOf(
        ChoiceValue("catalog", "Adds the libraries to `gradle/libs.versions.toml` and refers to them as `libs.katachi`"),
        ChoiceValue("literal", "Writes the coordinates into `build.gradle.kts`"),
    ),
)
private val TARGET_TYPE = ChoiceType(
    "Target",
    "Which file `katachi agent answers --target` reads or writes",
    listOf(ChoiceValue("report", "The report"), ChoiceValue("check-list", "The check list")),
)
private val API_MODULE_TYPE = ChoiceType(
    "ApiModule",
    "The module whose API reference `katachi agent docs --api` fetches",
    listOf(ChoiceValue("katachi", "katachi itself"), ChoiceValue("katachi-konsist", "katachi-konsist")),
    // A bare `katachi` would clash with the `val katachi = Katachi(...)` every script has.
    bareNames = false,
)
private val CONSTRAINT_NAMES_TYPE = ChoiceType(
    "ConstraintNames",
    "Whether `katachi agent preferences set constraintNames=...` puts the question number in constraint names",
    listOf(ChoiceValue("plain", "Names only"), ChoiceValue("numbered", "Names with the question number")),
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
private val REFRESH = OptionSpec("--refresh", OptionKind.Flag, doc = "fetches again even when the working directory has a copy")
private val TARGET = OptionSpec("--target", OptionKind.Choice(TARGET_TYPE), doc = "the file to read or write", param = "target", sample = "report")
private val DATA_ID = OptionSpec(
    "--id",
    OptionKind.Text,
    doc = "the id of the JSON block to read or write. Without it, the one the file is known to have",
    param = "id",
    paramDoc = "Such as `checklist`.",
)
private val DATA_TARGET = PositionalSpec("target", doc = "`check-list`, `report` or the path of an HTML file.", sample = "check-list")
private val DATA_JSON = PositionalSpec("json", doc = "The path of a JSON file.", sample = "meta.json")

private val ADD_SPECS: List<CommandSpec> = ADD_KINDS.map { (kind, add) ->
    CommandSpec(
        kind,
        options = add.scalars.map { field ->
            OptionSpec("--$field", OptionKind.Text, doc = add.docs[field].orEmpty(), paramDoc = "Becomes `$field` of the row.", sample = add.samples[field])
        } + add.lists.map { field ->
            OptionSpec("--$field", OptionKind.Text, doc = add.docs[field].orEmpty(), param = "values", paramDoc = "Each becomes one entry of `$field` of the row.", sample = add.samples[field], repeatable = true)
        } + add.flags.keys.map { flag ->
            OptionSpec("--$flag", OptionKind.Flag, doc = add.docs[flag].orEmpty(), sample = add.samples[flag])
        },
        rawPositionals = true,
        doc = "appends one row to `${add.array}` of ${add.target.en}",
    )
}

internal val COMMAND_SPECS: List<CommandSpec> = listOf(
    CommandSpec(
        "doctor",
        listOf(LANG),
        doc = "checks the JDK Gradle uses, the project's Kotlin version and whether git is there",
    ),
    CommandSpec(
        "init",
        agent = true,
        options = listOf(
            OptionSpec(
                "--workdir",
                OptionKind.Path,
                doc = "the working directory. Without it, the command picks one",
                param = "path",
                paramDoc = "Relative to where the command runs, or absolute.",
                katachiDefault = KatachiDefault.Workdir,
            ),
            OptionSpec(
                "--katachi",
                OptionKind.Text,
                doc = "the katachi version to record. Without it, the version of this katachi command",
                param = "version",
                paramDoc = "Such as `0.4.0`.",
                katachiDefault = KatachiDefault.Version,
            ),
            MODULE_DIR,
            OptionSpec("--offline", OptionKind.Flag, doc = "downloads nothing", sample = ""),
            OptionSpec(
                "--lang",
                OptionKind.Choice(LANG_TYPE),
                doc = "the language of the check list, the report and the command's messages",
                param = "lang",
                sample = "ja",
                katachiDefault = KatachiDefault.Lang,
            ),
            OptionSpec("--force", OptionKind.Flag, doc = "lays out the check list and the report again even when they are filled in"),
        ),
        doc = "makes the working directory and lays out the check list and the report",
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
                doc = "the katachi version to depend on. Without it, the version `init` recorded",
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
            OptionSpec(
                "--kotest-style",
                OptionKind.Choice(KOTEST_STYLE_TYPE),
                doc = "the spec style of the generated Kotest test. Without `--test-framework`, it picks kotest",
                param = "style",
            ),
            OptionSpec("--deps", OptionKind.Choice(DEPENDENCY_STYLE_TYPE), doc = "how the dependencies are written", param = "style"),
            OptionSpec("--force", OptionKind.Flag, doc = "overwrites an existing test module"),
            OptionSpec("--dry-run", OptionKind.Flag, doc = "prints what would be written, and writes nothing"),
        ),
        doc = "creates the test module and adds it to the build",
    ),
    CommandSpec(
        "data",
        agent = true,
        subcommands = listOf(
            CommandSpec(
                "get",
                listOf(DATA_ID),
                listOf(DATA_TARGET, PositionalSpec("key", doc = "Dot-separated, such as `meta.moduleDir`. Without it, the whole JSON.", sample = "meta.moduleDir", required = false)),
                doc = "prints the JSON embedded in the check list or the report, or the part of it at a key",
            ),
            CommandSpec("set", listOf(DATA_ID), listOf(DATA_TARGET, DATA_JSON), doc = "replaces the embedded JSON with the content of a JSON file"),
            CommandSpec("merge", listOf(DATA_ID), listOf(DATA_TARGET, DATA_JSON), doc = "deep-merges a JSON file into the embedded JSON. Arrays are replaced, not appended to"),
        ),
        doc = "reads and writes the JSON embedded in the check list and the report",
    ),
    CommandSpec(
        "docs",
        agent = true,
        options = listOf(
            OptionSpec("--small", OptionKind.Flag, doc = "fetches the shorter guide (`llms-small.txt`) instead of the whole one"),
            OptionSpec("--topics", OptionKind.Flag, doc = "lists the topics `--topic` takes"),
            OptionSpec(
                "--topic",
                OptionKind.Text,
                doc = "fetches one page of the guide",
                param = "name",
                paramDoc = "Such as `layout` or `kt-file-content`.",
                sample = "layout",
            ),
            OptionSpec(
                "--api",
                OptionKind.Choice(API_MODULE_TYPE),
                doc = "fetches the API reference: the index without a module, the whole reference of one module with it",
                param = "module",
                valueOptional = true,
            ),
            REFRESH,
        ),
        doc = "fetches the katachi guide into the working directory and prints the path",
    ),
    CommandSpec(
        "guide",
        agent = true,
        options = listOf(
            OptionSpec("--refresh", OptionKind.Flag, doc = "writes the working directory's copy of the guide again"),
            LANG,
        ),
        positionals = listOf(PositionalSpec("section", doc = "The number of a heading, such as `3-2`.", sample = "3-2", required = false)),
        doc = "prints the install guide one section at a time, or its table of contents without a section",
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
    CommandSpec(
        "check",
        agent = true,
        positionals = listOf(PositionalSpec("items", doc = "The ids of the items, such as `3-2`.", sample = "3-2", variadic = true)),
        rawPositionals = true,
        doc = "marks items of the check list done",
    ),
    CommandSpec(
        "uncheck",
        agent = true,
        positionals = listOf(PositionalSpec("items", doc = "The ids of the items, such as `3-2`.", sample = "3-2", variadic = true)),
        rawPositionals = true,
        doc = "marks items of the check list not done",
    ),
    // warn <step id> <text> | warn <step id> --clear
    CommandSpec(
        "warn",
        agent = true,
        options = listOf(OptionSpec("--clear", OptionKind.Flag, doc = "removes the warning instead of adding one")),
        positionals = listOf(
            PositionalSpec("step", doc = "The id of the step, such as `4`.", sample = "4"),
            PositionalSpec("text", doc = "The warning. Leave it out with `.clear()`.", sample = "gradlew is missing", required = false),
        ),
        rawPositionals = true,
        doc = "puts a warning on a step of the check list, which keeps it open even when it is done",
    ),
    // add <kind> --<field> <value>...: the fields differ by kind (see `add <kind> --help`).
    CommandSpec("add", agent = true, subcommands = ADD_SPECS, rawPositionals = true, doc = "appends one row to an array of the check list or the report, without touching the others"),
    CommandSpec(
        "preferences",
        agent = true,
        subcommands = listOf(
            CommandSpec("show", doc = "prints each preference, its value and where it came from"),
            CommandSpec(
                "set",
                listOf(
                    OptionSpec("nestedGroups", OptionKind.YesNo, doc = "whether groups are nested", param = "enabled", sample = "yes", keyValue = true),
                    OptionSpec("moduleDir", OptionKind.Path, doc = "where the test module goes, recorded as `meta.moduleDir` of the check list", param = "path", paramDoc = "Such as `static-analysis/katachi`.", keyValue = true),
                    OptionSpec("testFramework", OptionKind.Choice(TEST_FRAMEWORK_TYPE), doc = "the test framework `scaffold` picks", param = "framework", sample = "kotest", keyValue = true),
                    OptionSpec("dependencyStyle", OptionKind.Choice(DEPENDENCY_STYLE_TYPE), doc = "how `scaffold` writes the dependencies", param = "style", keyValue = true),
                    OptionSpec("constraintNames", OptionKind.Choice(CONSTRAINT_NAMES_TYPE), doc = "whether constraint names carry the question number", param = "names", keyValue = true),
                    OptionSpec("oneRolePerFile", OptionKind.YesNo, doc = "whether each Role is written in a file of its own", param = "enabled", keyValue = true),
                ),
                rawPositionals = true,
                doc = "records how the definition is written",
            ),
        ),
        defaultSubcommand = "show",
        doc = "shows and records how the definition is written",
    ),
    CommandSpec(
        "answers",
        agent = true,
        subcommands = listOf(
            CommandSpec("show", listOf(TARGET), doc = "prints the answers to the questions. Without `--target`, those of both files"),
            CommandSpec(
                "import",
                listOf(TARGET),
                listOf(PositionalSpec("json", doc = "The path of the JSON the browser exported.", sample = "answers.json")),
                doc = "reads the answers the browser exported",
            ),
            CommandSpec(
                "set",
                listOf(TARGET, OptionSpec("--note", OptionKind.Text, doc = "a note to keep with the answer", param = "text", paramDoc = "Any text.")),
                listOf(
                    PositionalSpec("id", doc = "The id of the question: `q<n>` of the check list or `cq<n>` of the report.", sample = "cq1"),
                    PositionalSpec("answer", doc = "The answer.", sample = "yes"),
                ),
                doc = "writes the answer to one question",
            ),
        ),
        defaultSubcommand = "show",
        doc = "shows and records the answers to the questions",
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
    CommandSpec("verify", agent = true, doc = "lists what is left to fill in, and closes the last step when nothing is"),
    CommandSpec("summary", agent = true, doc = "prints the message to hand back to the user"),
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

            OptionKind.Text, OptionKind.Path, OptionKind.YesNo -> {
                values[arg] = needValue(arg, args.getOrNull(index + 1))
                index += 2
            }
        }
    }
    return ParsedArgs(null, values, flags, positionals, passthrough)
}
