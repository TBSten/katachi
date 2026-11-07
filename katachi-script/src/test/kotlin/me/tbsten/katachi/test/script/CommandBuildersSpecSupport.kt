package me.tbsten.katachi.test.script

import me.tbsten.katachi.script.internal.COMMAND_SPECS
import me.tbsten.katachi.script.internal.ChoiceType
import me.tbsten.katachi.script.internal.CommandSpec
import me.tbsten.katachi.script.internal.KatachiDefault
import me.tbsten.katachi.script.internal.OptionKind
import me.tbsten.katachi.script.internal.OptionSpec
import me.tbsten.katachi.script.internal.PositionalSpec
import me.tbsten.katachi.script.internal.commandText

/** The first line of every generated file; also how a stale one is told from a hand-written one. */
internal const val GENERATED_HEADER: String = "// Generated from COMMAND_SPECS (internal/CommandSpecs.kt) by CommandBuildersSpec. Do not edit by hand:"

private val HEADER = """
    $GENERATED_HEADER
    // change the spec and run `./gradlew :katachi-script:test --tests '*CommandBuildersSpec' -Dkatachi.golden.update=true`.
""".trimIndent()

private const val PACKAGE = "me.tbsten.katachi.script"

/** Where the builders go, so the API reference of [PACKAGE] lists only what a script types. */
private const val COMMAND_PACKAGE = "$PACKAGE.command"

/** Where the value types of the options go; [PACKAGE] keeps their bare names (`kotest`, `ja`). */
private const val OPTION_PACKAGE = "$PACKAGE.option"

/** The longest code line the generated files keep; a longer `fun ... = body` breaks after `=` as the IDE formatter does. */
private const val MAX_LINE_LENGTH = 120

/**
 * The builder sources for [specs], by path under `me/tbsten/katachi/script/`: the entry point
 * (`katachi.add`) as `add.kt`, its builders as `command/AddCommand.kt`, each value type of an
 * option as `option/Lang.kt` and their bare names as `CommandValues.kt`.
 */
internal fun generateCommandBuilders(specs: List<CommandSpec> = COMMAND_SPECS): Map<String, String> {
    val files = linkedMapOf<String, String>()
    for (spec in specs) {
        val file = CommandFile(spec)
        files["${camel(spec.name)}.kt"] = file.renderEntry()
        files["command/${pascal(spec.name)}Command.kt"] = file.renderBuilders()
    }
    files += renderValues(specs)
    return files
}

// region names

private val HARD_KEYWORDS = setOf(
    "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface", "is", "null",
    "object", "package", "return", "super", "this", "throw", "true", "try", "typealias", "typeof", "val", "var", "when", "while",
)

private val PLAIN_IDENTIFIER = Regex("[A-Za-z_][A-Za-z0-9_]*")

/** A Kotlin name as the command line spells it: `` `test-framework` ``, `` `package` ``, `lang`. */
private fun identifier(name: String): String =
    if (name.matches(PLAIN_IDENTIFIER) && name !in HARD_KEYWORDS) name else "`$name`"

private fun words(name: String): List<String> = name.split('-', '_').filter { it.isNotEmpty() }

private fun pascal(name: String): String = words(name).joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }

private fun camel(name: String): String = pascal(name).replaceFirstChar(Char::lowercaseChar)

private fun OptionSpec.methodName(): String = identifier(name.removePrefix("--"))

private fun ChoiceType.constant(value: String): String = "$typeName.${pascal(value)}"

/** How a script writes [value]: the bare name when there is one, otherwise `Type.Constant`. */
private fun ChoiceType.reference(value: String): String = if (bareNames) camel(value) else constant(value)

private fun kotlinString(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$") + "\""

// endregion

// region usage text

private fun OptionSpec.usage(): String {
    val values = when (val kind = kind) {
        OptionKind.Flag -> null
        OptionKind.Text, OptionKind.Path -> "<$param>"
        OptionKind.YesNo -> "<yes|no>"
        is OptionKind.Choice -> "<${kind.values.joinToString("|")}>"
    }
    return when {
        values == null -> name
        keyValue -> "$name=$values"
        valueOptional -> "$name [$values]"
        else -> "$name $values"
    }
}

private fun PositionalSpec.usage(index: Int): String = when {
    variadic -> "<$name>..."
    required || index == 0 -> "<$name>"
    else -> "[<$name>]"
}

private fun choiceParamDoc(type: ChoiceType): String {
    val references = type.values.map { "[${type.reference(it.value)}]" }
    return when (references.size) {
        1 -> references.single()
        else -> references.dropLast(1).joinToString(", ") + " or " + references.last()
    } + "."
}

private fun sentence(text: String): String = if (text.isEmpty() || text.endsWith(".")) text else "$text."

// endregion

/** One subcommand of `katachi` and everything under it: an entry point, and a builder or a group of builders. */
private class CommandFile(private val spec: CommandSpec) {
    private val group = "${pascal(spec.name)}CommandGroup"

    /** `katachi.add` / `katachi.guide("3-2")`, in [PACKAGE]: what a script types first. */
    fun renderEntry(): String {
        val imports = sortedSetOf<String>()
        val body = if (spec.subcommands.isEmpty()) {
            imports += "$COMMAND_PACKAGE.${pascal(spec.name)}Command"
            imports += "me.tbsten.katachi.script.internal.CommandLine"
            entry(spec, listOf(spec.name), receiver = "Katachi", katachi = "this", imports)
        } else {
            imports += "$COMMAND_PACKAGE.$group"
            doc(
                "The subcommands of `katachi ${spec.name}`: ${sentence(spec.doc)}",
                "",
                "`katachi ${spec.name}` is not a command of its own here: pick one of its subcommands.",
            ) + "public val Katachi.${identifier(spec.name)}: $group\n    get() = $group(this)\n"
        }
        return source(PACKAGE, imports, body)
    }

    /** The builders the entry point returns, in [COMMAND_PACKAGE]. */
    fun renderBuilders(): String {
        val imports = sortedSetOf(
            "me.tbsten.katachi.script.Katachi",
            "me.tbsten.katachi.script.internal.CommandLine",
            "me.tbsten.katachi.script.internal.commandText",
            "me.tbsten.katachi.script.internal.run",
        )
        val body = StringBuilder()
        if (spec.subcommands.isEmpty()) {
            body.append(builder(spec, listOf(spec.name), imports))
        } else {
            body.append(group(spec, group, imports))
            for (sub in spec.subcommands) {
                body.append("\n")
                body.append(builder(sub, listOf(spec.name, sub.name), imports))
            }
        }
        return source(COMMAND_PACKAGE, imports, body.toString())
    }

    private fun group(spec: CommandSpec, group: String, imports: MutableSet<String>): String = buildString {
        val first = spec.subcommands.first()
        val default = spec.defaultSubcommand?.let { " `katachi ${spec.name}` alone is the same as [$it]." }.orEmpty()
        append(
            doc(
                "The subcommands of `katachi ${spec.name}`: ${sentence(spec.doc)}",
                "",
                "Each one returns the builder of that subcommand.$default",
                "",
                "## Example 1: pick a subcommand",
                "",
                "```kt",
                "val command = Katachi().${entryChain(first, listOf(spec.name, first.name))}${optionChain(first)}",
                "println(command) // ${exampleLine(first, listOf(spec.name, first.name))}",
                "command.execute()",
                "```",
                "",
                "@see Katachi",
            ),
        )
        append("public class $group internal constructor(private val katachi: Katachi) {\n")
        for ((index, sub) in spec.subcommands.withIndex()) {
            if (index > 0) append("\n")
            append(entry(sub, listOf(spec.name, sub.name), receiver = null, katachi = "katachi", imports).indented())
        }
        append("}\n")
    }

    /** `katachi.doctor` / `katachi.guide("3-2")`: a property when every positional can be left out, a function when there are positionals. */
    private fun entry(spec: CommandSpec, path: List<String>, receiver: String?, katachi: String, imports: MutableSet<String>): String = buildString {
        val className = "${pascal(path.joinToString("-"))}Command"
        val start = startLine(spec, path, katachi)
        val declared = if (spec.options.any { it.required }) ", DeclarationSite.capture()" else ""
        val prefix = receiver?.let { "$it." }.orEmpty()
        val defaults = if (spec.options.any { it.katachiDefault != null }) ", starting from this [Katachi]'s defaults" else ""
        val summary = "Same as `katachi ${path.joinToString(" ")}` on the command line$defaults: ${sentence(spec.doc)}"
        if (spec.positionals.none { it.required }) {
            append(doc(summary))
            append("public val $prefix${identifier(spec.name)}: $className\n")
            append("    get() = $className($katachi, $start$declared)\n")
        }
        if (spec.positionals.isNotEmpty()) {
            if (spec.positionals.none { it.required }) append("\n")
            val usage = spec.positionals.mapIndexed { index, it -> it.usage(index) }.joinToString(" ")
            val params = spec.positionals.mapIndexed { index, it ->
                when {
                    it.variadic -> "vararg ${it.name}: String"
                    it.required || index == 0 -> "${it.name}: String"
                    else -> "${it.name}: String? = null"
                }
            }
            val single = spec.positionals.filterNot { it.variadic }.map { it.name }
            val variadic = spec.positionals.firstOrNull { it.variadic }?.name
            val list = if (params.any { it.endsWith("? = null") }) "listOfNotNull" else "listOf"
            val values = when {
                single.isEmpty() -> "$variadic.toList()"
                variadic == null -> "$list(${single.joinToString()})"
                else -> "$list(${single.joinToString()}) + $variadic"
            }
            // A top-level function needs an example of its own; a member is shown by its class's.
            val example = if (receiver == null) {
                emptyList()
            } else {
                listOf(
                    "## Example 1: pass the arguments",
                    "",
                    "```kt",
                    "val command = Katachi().${entryChain(spec, path)}",
                    "println(command) // ${commandText(path + examplePositionals(spec).orEmpty())}",
                    "```",
                    "",
                )
            }
            append(
                doc(
                    "Same as `katachi ${path.joinToString(" ")} $usage` on the command line$defaults: ${sentence(spec.doc)}",
                    "",
                    *example.toTypedArray(),
                    *spec.positionals.map { "@param ${it.name} ${it.doc}" }.toTypedArray(),
                ),
            )
            append("public fun $prefix${identifier(spec.name)}(${params.joinToString()}): $className =\n")
            append("    $className($katachi, $start.withPositionals($values)$declared)\n")
        }
        if (declared.isNotEmpty()) imports += "me.tbsten.katachi.dsl.DeclarationSite"
    }

    private fun startLine(spec: CommandSpec, path: List<String>, katachi: String): String = buildString {
        append("CommandLine(listOf(${path.joinToString { kotlinString(it) }}))")
        for (option in spec.options) {
            val default = option.katachiDefault ?: continue
            val value = when (option.kind) {
                is OptionKind.Choice -> "$katachi.${default.property}?.value"
                else -> "$katachi.${default.property}"
            }
            append(".withNonEmpty(${kotlinString(option.name)}, $value)")
        }
    }

    private fun builder(spec: CommandSpec, path: List<String>, imports: MutableSet<String>): String = buildString {
        val className = "${pascal(path.joinToString("-"))}Command"
        val command = "katachi ${path.joinToString(" ")}"
        val required = spec.options.filter { it.required }
        if (required.isNotEmpty()) {
            imports += "me.tbsten.katachi.dsl.DeclarationSite"
            imports += "me.tbsten.katachi.script.internal.requireOption"
        }
        for (option in spec.options) {
            val kind = option.kind as? OptionKind.Choice ?: continue
            imports += "$OPTION_PACKAGE.${kind.type.typeName}"
        }
        val repeatable = spec.options.any { it.repeatable }
        val lines = mutableListOf(
            "The `$command` command: ${sentence(spec.doc)}",
            "",
        )
        if (spec.options.isEmpty() && spec.passthrough == null) {
            lines += "It takes no options, so building one is writing `$command` in a shell."
        } else {
            lines += "Building one is writing `$command ...` in a shell: the methods are named after the"
            lines += "options of `$command --help` and add the same ${if (spec.options.any { it.keyValue }) "`key=value`" else "flag"}. Each returns a new"
            lines += "command; calling one twice keeps the last value" + if (repeatable) ", except for the ones that take several values, which add to them." else "."
        }
        for (option in required) {
            lines += "`${option.name}` is required and is checked by [execute], not while the command is built."
        }
        val chain = "Katachi()." + entryChain(spec, path) + optionChain(spec)
        val line = exampleLine(spec, path)
        lines += listOf(
            "",
            "## Example 1: build the command, print it and run it",
            "",
            "```kt",
            "val command = $chain",
            "println(command) // $line",
            "command.execute()",
            "```",
            "",
            "The `execute()` above runs the same thing as this line in a shell:",
            "",
            "```sh",
            line,
            "```",
            "",
            "@see Katachi",
        )
        append(doc(*lines.toTypedArray()))
        val declared = if (required.isNotEmpty()) ",\n    private val declaredAt: DeclarationSite," else ","
        append("public class $className internal constructor(\n")
        append("    private val katachi: Katachi,\n")
        append("    private val line: CommandLine$declared\n")
        append(") : KatachiCommand {\n")
        val nextArgs = if (required.isNotEmpty()) "katachi, line, declaredAt" else "katachi, line"
        append("    private fun next(line: CommandLine) = $className($nextArgs)\n")
        for (option in spec.options) {
            append("\n")
            append(method(option, className, command).indented())
        }
        spec.passthrough?.let { passthrough ->
            append("\n")
            append(
                doc(
                    "Same as `$command -- <arguments>...` on the command line: ${sentence(passthrough.doc)} Calling it again adds more.",
                    "",
                    "@param arguments Such as `--info`.",
                ).indented(),
            )
            append(oneLiner("public fun ${identifier(passthrough.method)}(vararg arguments: String): $className", "next(line.plusPassthrough(arguments.toList()))").indented())
        }
        append("\n")
        append(
            doc(
                "Appends [arguments] as they are, for an option this builder has no method for yet:",
                "`arg(\"--new-option\", \"v\")` is `$command ... --new-option v` on the command line.",
                "",
                "@param arguments Such as `\"--new-option\", \"value\"`.",
            ).indented(),
        )
        append(oneLiner("public fun arg(vararg arguments: String): $className", "next(line.plus(arguments.toList()))").indented())
        append("\n")
        append("    override val arguments: List<String> get() = line.arguments\n\n")
        if (required.isEmpty()) {
            append("    override fun execute(): Int = katachi.run(arguments)\n\n")
        } else {
            append("    override fun execute(): Int {\n")
            for (option in required) append("        requireOption(line, ${kotlinString(option.name)}, declaredAt)\n")
            append("        return katachi.run(arguments)\n")
            append("    }\n\n")
        }
        append("    /** The command line as typed in a shell: `$line`. Values a shell would split are quoted. */\n")
        append("    override fun toString(): String = commandText(arguments)\n")
        append("}\n")
    }

    private fun method(option: OptionSpec, className: String, command: String): String = buildString {
        val summary = "Same as `$command ${option.usage()}` on the command line" +
            (if (option.repeatable) ", once for each value" else "") +
            (if (option.doc.isEmpty()) "." else ": ${sentence(option.doc)}") +
            (if (option.required) " Required." else "") +
            (if (option.repeatable) " Calling it again adds more." else "")
        val name = option.methodName()
        val kind = option.kind
        when {
            kind == OptionKind.Flag -> {
                append(doc(summary, "", "@param enabled False takes the option off again."))
                append(oneLiner("public fun $name(enabled: Boolean = true): $className", "next(line.withFlag(${kotlinString(option.name)}, enabled))"))
            }

            option.repeatable -> {
                append(doc(summary, "", "@param ${option.param} ${option.paramDoc.ifEmpty { "One or more values." }}"))
                append(oneLiner("public fun $name(vararg ${option.param}: String): $className", "next(line.withAppended(${kotlinString(option.name)}, ${option.param}.toList()))"))
            }

            else -> {
                val (type, value, paramDoc) = when (kind) {
                    is OptionKind.Choice -> Triple(kind.type.typeName, "${option.param}.value", choiceParamDoc(kind.type))
                    OptionKind.YesNo -> Triple("Boolean", "if (${option.param}) \"yes\" else \"no\"", "True for `yes`, false for `no`.")
                    else -> Triple("String", option.param, option.paramDoc.ifEmpty { "The value." })
                }
                val set = if (option.keyValue) "withKeyValue" else "with"
                if (option.valueOptional) {
                    append(doc("Same as `$command ${option.name}` on the command line, without a value: ${sentence(option.doc)}"))
                    append(oneLiner("public fun $name(): $className", "next(line.with(${kotlinString(option.name)}, null))"))
                    append("\n")
                }
                append(doc(summary, "", "@param ${option.param} $paramDoc"))
                append(oneLiner("public fun $name(${option.param}: $type): $className", "next(line.$set(${kotlinString(option.name)}, $value))"))
            }
        }
    }
}

// region examples

/** `data.get("check-list", "meta.moduleDir")`: how the example reaches the builder, after `Katachi().` and the group. */
private fun entryChain(spec: CommandSpec, path: List<String>): String {
    val groups = path.dropLast(1).joinToString("") { "${identifier(it)}." }
    val name = identifier(spec.name)
    val positionals = examplePositionals(spec)
    return if (positionals == null) "$groups$name" else "$groups$name(${positionals.joinToString { kotlinString(it) }})"
}

/** The sample positionals, or null when the command takes none and the example uses the property. */
private fun examplePositionals(spec: CommandSpec): List<String>? =
    if (spec.positionals.isEmpty()) null else spec.positionals.map { it.sample }

private fun optionChain(spec: CommandSpec): String = spec.options.filter { it.sample != null }.joinToString("") { option ->
    val sample = option.sample!!
    val argument = when (val kind = option.kind) {
        OptionKind.Flag -> ""
        OptionKind.YesNo -> (sample == "yes").toString()
        is OptionKind.Choice -> kind.type.reference(sample)
        OptionKind.Text, OptionKind.Path -> kotlinString(sample)
    }
    ".${option.methodName()}($argument)"
}

private fun exampleArguments(spec: CommandSpec, path: List<String>): List<String> {
    val options = spec.options.filter { it.sample != null }.flatMap { option ->
        val sample = option.sample!!
        when {
            option.kind == OptionKind.Flag -> listOf(option.name)
            option.keyValue -> listOf("${option.name}=$sample")
            else -> listOf(option.name, sample)
        }
    }
    return path + examplePositionals(spec).orEmpty() + options
}

private fun exampleLine(spec: CommandSpec, path: List<String>): String = commandText(exampleArguments(spec, path))

// endregion

// region values

/** `option/<Type>.kt` for each value type of an option, and `CommandValues.kt` with the bare names. */
private fun renderValues(specs: List<CommandSpec>): Map<String, String> {
    val uses = linkedMapOf<String, Pair<ChoiceType, String>>()
    fun visit(spec: CommandSpec, path: List<String>) {
        for (option in spec.options) {
            val kind = option.kind as? OptionKind.Choice ?: continue
            if (kind.type.typeName in uses) continue
            val sample = option.sample?.takeIf { it in kind.values } ?: kind.values.first()
            val chain = "katachi." + entryChain(spec, path) + ".${option.methodName()}(${kind.type.reference(sample)})"
            uses[kind.type.typeName] = kind.type to chain
        }
        spec.subcommands.forEach { visit(it, path + it.name) }
    }
    specs.forEach { visit(it, listOf(it.name)) }

    val files = linkedMapOf<String, String>()
    for ((type, chain) in uses.values) {
        val sample = Regex("""\((\w+)\)$""").find(chain)?.groupValues?.get(1)
        val lines = mutableListOf("${sentence(type.doc)}", "")
        val imports = sortedSetOf<String>()
        if (type.bareNames) {
            val names = type.values.map { "[${camel(it.value)}]" }
            imports += type.values.map { "$PACKAGE.${camel(it.value)}" }
            lines += "The top-level ${names.dropLast(1).joinToString(", ")} and ${names.last()} are the same values, so a script can write them bare."
            lines += ""
        }
        lines += "## Example 1: pass one to the command"
        lines += ""
        lines += "```kt"
        lines += chain
        if (type.bareNames && sample != null) {
            val value = type.values.first { camel(it.value) == sample }.value
            lines += chain.replace("($sample)", "(${type.constant(value)})") + " // the same"
        }
        lines += "```"
        lines += ""
        lines += "@property value How the command line spells it."
        val body = StringBuilder()
        body.append(doc(*lines.toTypedArray()))
        body.append("public enum class ${type.typeName}(public val value: String) {\n")
        for ((index, value) in type.values.withIndex()) {
            if (index > 0) body.append("\n")
            body.append("    /** ${value.doc} (`${value.value}`). */\n")
            body.append("    ${pascal(value.value)}(${kotlinString(value.value)}),\n")
        }
        body.append("}\n")
        files["option/${type.typeName}.kt"] = source(OPTION_PACKAGE, imports, body.toString())
    }

    val bare = uses.values.map { it.first }.filter { it.bareNames }
    val body = StringBuilder()
    for (type in bare) {
        for (value in type.values) {
            body.append("/** [${type.constant(value.value)}], to write `${camel(value.value)}` bare. */\n")
            body.append("public val ${camel(value.value)}: ${type.typeName} = ${type.constant(value.value)}\n\n")
        }
    }
    files["CommandValues.kt"] = source(PACKAGE, bare.map { "$OPTION_PACKAGE.${it.typeName}" }.toSortedSet(), body.toString())
    return files
}

// endregion

/**
 * `$signature = $body` on one line, or broken after `=` when that line would be longer than [MAX_LINE_LENGTH]
 * inside a class body (four spaces in), as the IDE formatter would break it.
 */
private fun oneLiner(signature: String, body: String): String {
    val line = "$signature = $body"
    return if (line.length + 4 > MAX_LINE_LENGTH) "$signature =\n    $body\n" else "$line\n"
}

/** A generated file: the header, [packageName], [imports] when there are any, and [body]. */
private fun source(packageName: String, imports: Set<String>, body: String): String = buildString {
    append(HEADER).append("\n\n")
    append("package $packageName\n\n")
    if (imports.isNotEmpty()) {
        for (import in imports) append("import $import\n")
        append("\n")
    }
    append(body.trimEnd()).append("\n")
}

/** Indents every line but the empty ones, so the generated code has no trailing spaces. */
private fun String.indented(): String = lines().joinToString("\n") { if (it.isEmpty()) it else "    $it" }

private fun doc(vararg lines: String): String = buildString {
    append("/**\n")
    for (line in lines) append(if (line.isEmpty()) " *\n" else " * $line\n")
    append(" */\n")
}
