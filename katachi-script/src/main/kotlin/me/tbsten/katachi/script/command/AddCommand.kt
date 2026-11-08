// Generated from COMMAND_SPECS (internal/CommandSpecs.kt) by CommandBuildersSpec. Do not edit by hand:
// change the spec and run `./gradlew :katachi-script:test --tests '*CommandBuildersSpec' -Dkatachi.golden.update=true`.

package me.tbsten.katachi.script.command

import me.tbsten.katachi.script.Katachi
import me.tbsten.katachi.script.internal.CommandLine
import me.tbsten.katachi.script.internal.commandText
import me.tbsten.katachi.script.internal.run

/**
 * The subcommands of `katachi agent add`: appends one row to an array of the check list or the report, without touching the others.
 *
 * Each one returns the builder of that subcommand.
 *
 * ## Example 1: pick a subcommand
 *
 * ```kt
 * val command = Katachi().agent.add.violation.violation("UseCase depends on Repository").location("feature/ui/HomeViewModel.kt")
 * println(command) // katachi agent add violation --violation 'UseCase depends on Repository' --location feature/ui/HomeViewModel.kt
 * command.execute()
 * ```
 *
 * @see Katachi
 */
public class AddCommandGroup internal constructor(private val katachi: Katachi) {
    /**
     * Same as `katachi agent add violation` on the command line: appends one row to `violations` of the check list.
     */
    public val violation: AddViolationCommand
        get() = AddViolationCommand(katachi, CommandLine(listOf("agent", "add", "violation")))

    /**
     * Same as `katachi agent add question` on the command line: appends one row to `questions` of the check list.
     */
    public val question: AddQuestionCommand
        get() = AddQuestionCommand(katachi, CommandLine(listOf("agent", "add", "question")))

    /**
     * Same as `katachi agent add codebase-question` on the command line: appends one row to `questions` of the report.
     */
    public val `codebase-question`: AddCodebaseQuestionCommand
        get() = AddCodebaseQuestionCommand(katachi, CommandLine(listOf("agent", "add", "codebase-question")))

    /**
     * Same as `katachi agent add changed` on the command line: appends one row to `changedFiles` of the check list.
     */
    public val changed: AddChangedCommand
        get() = AddChangedCommand(katachi, CommandLine(listOf("agent", "add", "changed")))

    /**
     * Same as `katachi agent add module` on the command line: appends one row to `modules` of the report.
     */
    public val module: AddModuleCommand
        get() = AddModuleCommand(katachi, CommandLine(listOf("agent", "add", "module")))

    /**
     * Same as `katachi agent add role` on the command line: appends one row to `roles` of the report.
     */
    public val role: AddRoleCommand
        get() = AddRoleCommand(katachi, CommandLine(listOf("agent", "add", "role")))

    /**
     * Same as `katachi agent add tool` on the command line: appends one row to `tools` of the report.
     */
    public val tool: AddToolCommand
        get() = AddToolCommand(katachi, CommandLine(listOf("agent", "add", "tool")))

    /**
     * Same as `katachi agent add excluded` on the command line: appends one row to `excluded` of the report.
     */
    public val excluded: AddExcludedCommand
        get() = AddExcludedCommand(katachi, CommandLine(listOf("agent", "add", "excluded")))

    /**
     * Same as `katachi agent add template` on the command line: appends one row to `templates` of the report.
     */
    public val template: AddTemplateCommand
        get() = AddTemplateCommand(katachi, CommandLine(listOf("agent", "add", "template")))
}

/**
 * The `katachi agent add violation` command: appends one row to `violations` of the check list.
 *
 * Building one is writing `katachi agent add violation ...` in a shell: the methods are named after the
 * options of `katachi agent add violation --help` and add the same flag. Each returns a new
 * command; calling one twice keeps the last value.
 *
 * ## Example 1: build the command, print it and run it
 *
 * ```kt
 * val command = Katachi().agent.add.violation.violation("UseCase depends on Repository").location("feature/ui/HomeViewModel.kt")
 * println(command) // katachi agent add violation --violation 'UseCase depends on Repository' --location feature/ui/HomeViewModel.kt
 * command.execute()
 * ```
 *
 * The `execute()` above runs the same thing as this line in a shell:
 *
 * ```sh
 * katachi agent add violation --violation 'UseCase depends on Repository' --location feature/ui/HomeViewModel.kt
 * ```
 *
 * @see Katachi
 */
public class AddViolationCommand internal constructor(
    private val katachi: Katachi,
    private val line: CommandLine,
) : KatachiCommand {
    private fun next(line: CommandLine) = AddViolationCommand(katachi, line)

    /**
     * Same as `katachi agent add violation --violation <value>` on the command line: what the definition reports.
     *
     * @param value Becomes `violation` of the row.
     */
    public fun violation(value: String): AddViolationCommand = next(line.with("--violation", value))

    /**
     * Same as `katachi agent add violation --location <value>` on the command line: where it is.
     *
     * @param value Becomes `location` of the row.
     */
    public fun location(value: String): AddViolationCommand = next(line.with("--location", value))

    /**
     * Same as `katachi agent add violation --whyNotFixed <value>` on the command line: why it is left as it is.
     *
     * @param value Becomes `whyNotFixed` of the row.
     */
    public fun whyNotFixed(value: String): AddViolationCommand = next(line.with("--whyNotFixed", value))

    /**
     * Same as `katachi agent add violation --suggestion <value>` on the command line: how it could be fixed.
     *
     * @param value Becomes `suggestion` of the row.
     */
    public fun suggestion(value: String): AddViolationCommand = next(line.with("--suggestion", value))

    /**
     * Appends [arguments] as they are, for an option this builder has no method for yet:
     * `arg("--new-option", "v")` is `katachi agent add violation ... --new-option v` on the command line.
     *
     * @param arguments Such as `"--new-option", "value"`.
     */
    public fun arg(vararg arguments: String): AddViolationCommand = next(line.plus(arguments.toList()))

    override val arguments: List<String> get() = line.arguments

    override fun execute(): Int = katachi.run(arguments)

    /** The command line as typed in a shell: `katachi agent add violation --violation 'UseCase depends on Repository' --location feature/ui/HomeViewModel.kt`. Values a shell would split are quoted. */
    override fun toString(): String = commandText(arguments)
}

/**
 * The `katachi agent add question` command: appends one row to `questions` of the check list.
 *
 * Building one is writing `katachi agent add question ...` in a shell: the methods are named after the
 * options of `katachi agent add question --help` and add the same flag. Each returns a new
 * command; calling one twice keeps the last value, except for the ones that take several values, which add to them.
 *
 * ## Example 1: build the command, print it and run it
 *
 * ```kt
 * val command = Katachi().agent.add.question.question("Nest groups?").options("yes")
 * println(command) // katachi agent add question --question 'Nest groups?' --options yes
 * command.execute()
 * ```
 *
 * The `execute()` above runs the same thing as this line in a shell:
 *
 * ```sh
 * katachi agent add question --question 'Nest groups?' --options yes
 * ```
 *
 * @see Katachi
 */
public class AddQuestionCommand internal constructor(
    private val katachi: Katachi,
    private val line: CommandLine,
) : KatachiCommand {
    private fun next(line: CommandLine) = AddQuestionCommand(katachi, line)

    /**
     * Same as `katachi agent add question --question <value>` on the command line: the question to ask.
     *
     * @param value Becomes `question` of the row.
     */
    public fun question(value: String): AddQuestionCommand = next(line.with("--question", value))

    /**
     * Same as `katachi agent add question --observed <value>` on the command line: what was seen in the codebase that raised it.
     *
     * @param value Becomes `observed` of the row.
     */
    public fun observed(value: String): AddQuestionCommand = next(line.with("--observed", value))

    /**
     * Same as `katachi agent add question --recommendation <value>` on the command line: the answer recommended.
     *
     * @param value Becomes `recommendation` of the row.
     */
    public fun recommendation(value: String): AddQuestionCommand = next(line.with("--recommendation", value))

    /**
     * Same as `katachi agent add question --options <values>` on the command line, once for each value: the answers to choose from. Calling it again adds more.
     *
     * @param values Each becomes one entry of `options` of the row.
     */
    public fun options(vararg values: String): AddQuestionCommand =
        next(line.withAppended("--options", values.toList()))

    /**
     * Appends [arguments] as they are, for an option this builder has no method for yet:
     * `arg("--new-option", "v")` is `katachi agent add question ... --new-option v` on the command line.
     *
     * @param arguments Such as `"--new-option", "value"`.
     */
    public fun arg(vararg arguments: String): AddQuestionCommand = next(line.plus(arguments.toList()))

    override val arguments: List<String> get() = line.arguments

    override fun execute(): Int = katachi.run(arguments)

    /** The command line as typed in a shell: `katachi agent add question --question 'Nest groups?' --options yes`. Values a shell would split are quoted. */
    override fun toString(): String = commandText(arguments)
}

/**
 * The `katachi agent add codebase-question` command: appends one row to `questions` of the report.
 *
 * Building one is writing `katachi agent add codebase-question ...` in a shell: the methods are named after the
 * options of `katachi agent add codebase-question --help` and add the same flag. Each returns a new
 * command; calling one twice keeps the last value, except for the ones that take several values, which add to them.
 *
 * ## Example 1: build the command, print it and run it
 *
 * ```kt
 * val command = Katachi().agent.add.`codebase-question`.question("Is data/ a module of its own?").options("yes")
 * println(command) // katachi agent add codebase-question --question 'Is data/ a module of its own?' --options yes
 * command.execute()
 * ```
 *
 * The `execute()` above runs the same thing as this line in a shell:
 *
 * ```sh
 * katachi agent add codebase-question --question 'Is data/ a module of its own?' --options yes
 * ```
 *
 * @see Katachi
 */
public class AddCodebaseQuestionCommand internal constructor(
    private val katachi: Katachi,
    private val line: CommandLine,
) : KatachiCommand {
    private fun next(line: CommandLine) = AddCodebaseQuestionCommand(katachi, line)

    /**
     * Same as `katachi agent add codebase-question --question <value>` on the command line: the question to ask.
     *
     * @param value Becomes `question` of the row.
     */
    public fun question(value: String): AddCodebaseQuestionCommand = next(line.with("--question", value))

    /**
     * Same as `katachi agent add codebase-question --observed <value>` on the command line: what was seen in the codebase that raised it.
     *
     * @param value Becomes `observed` of the row.
     */
    public fun observed(value: String): AddCodebaseQuestionCommand = next(line.with("--observed", value))

    /**
     * Same as `katachi agent add codebase-question --recommendation <value>` on the command line: the answer recommended.
     *
     * @param value Becomes `recommendation` of the row.
     */
    public fun recommendation(value: String): AddCodebaseQuestionCommand = next(line.with("--recommendation", value))

    /**
     * Same as `katachi agent add codebase-question --options <values>` on the command line, once for each value: the answers to choose from. Calling it again adds more.
     *
     * @param values Each becomes one entry of `options` of the row.
     */
    public fun options(vararg values: String): AddCodebaseQuestionCommand =
        next(line.withAppended("--options", values.toList()))

    /**
     * Appends [arguments] as they are, for an option this builder has no method for yet:
     * `arg("--new-option", "v")` is `katachi agent add codebase-question ... --new-option v` on the command line.
     *
     * @param arguments Such as `"--new-option", "value"`.
     */
    public fun arg(vararg arguments: String): AddCodebaseQuestionCommand = next(line.plus(arguments.toList()))

    override val arguments: List<String> get() = line.arguments

    override fun execute(): Int = katachi.run(arguments)

    /** The command line as typed in a shell: `katachi agent add codebase-question --question 'Is data/ a module of its own?' --options yes`. Values a shell would split are quoted. */
    override fun toString(): String = commandText(arguments)
}

/**
 * The `katachi agent add changed` command: appends one row to `changedFiles` of the check list.
 *
 * Building one is writing `katachi agent add changed ...` in a shell: the methods are named after the
 * options of `katachi agent add changed --help` and add the same flag. Each returns a new
 * command; calling one twice keeps the last value.
 *
 * ## Example 1: build the command, print it and run it
 *
 * ```kt
 * val command = Katachi().agent.add.changed.path("settings.gradle.kts").change("New")
 * println(command) // katachi agent add changed --path settings.gradle.kts --change New
 * command.execute()
 * ```
 *
 * The `execute()` above runs the same thing as this line in a shell:
 *
 * ```sh
 * katachi agent add changed --path settings.gradle.kts --change New
 * ```
 *
 * @see Katachi
 */
public class AddChangedCommand internal constructor(
    private val katachi: Katachi,
    private val line: CommandLine,
) : KatachiCommand {
    private fun next(line: CommandLine) = AddChangedCommand(katachi, line)

    /**
     * Same as `katachi agent add changed --path <value>` on the command line: the file changed.
     *
     * @param value Becomes `path` of the row.
     */
    public fun path(value: String): AddChangedCommand = next(line.with("--path", value))

    /**
     * Same as `katachi agent add changed --change <value>` on the command line: how it changed, such as `New`.
     *
     * @param value Becomes `change` of the row.
     */
    public fun change(value: String): AddChangedCommand = next(line.with("--change", value))

    /**
     * Same as `katachi agent add changed --summary <value>` on the command line: what was changed in it.
     *
     * @param value Becomes `summary` of the row.
     */
    public fun summary(value: String): AddChangedCommand = next(line.with("--summary", value))

    /**
     * Appends [arguments] as they are, for an option this builder has no method for yet:
     * `arg("--new-option", "v")` is `katachi agent add changed ... --new-option v` on the command line.
     *
     * @param arguments Such as `"--new-option", "value"`.
     */
    public fun arg(vararg arguments: String): AddChangedCommand = next(line.plus(arguments.toList()))

    override val arguments: List<String> get() = line.arguments

    override fun execute(): Int = katachi.run(arguments)

    /** The command line as typed in a shell: `katachi agent add changed --path settings.gradle.kts --change New`. Values a shell would split are quoted. */
    override fun toString(): String = commandText(arguments)
}

/**
 * The `katachi agent add module` command: appends one row to `modules` of the report.
 *
 * Building one is writing `katachi agent add module ...` in a shell: the methods are named after the
 * options of `katachi agent add module --help` and add the same flag. Each returns a new
 * command; calling one twice keeps the last value.
 *
 * ## Example 1: build the command, print it and run it
 *
 * ```kt
 * val command = Katachi().agent.add.module.path("feature/home").kind("feature")
 * println(command) // katachi agent add module --path feature/home --kind feature
 * command.execute()
 * ```
 *
 * The `execute()` above runs the same thing as this line in a shell:
 *
 * ```sh
 * katachi agent add module --path feature/home --kind feature
 * ```
 *
 * @see Katachi
 */
public class AddModuleCommand internal constructor(
    private val katachi: Katachi,
    private val line: CommandLine,
) : KatachiCommand {
    private fun next(line: CommandLine) = AddModuleCommand(katachi, line)

    /**
     * Same as `katachi agent add module --path <value>` on the command line: the directory of the module.
     *
     * @param value Becomes `path` of the row.
     */
    public fun path(value: String): AddModuleCommand = next(line.with("--path", value))

    /**
     * Same as `katachi agent add module --kind <value>` on the command line: the kind of module, such as `feature`.
     *
     * @param value Becomes `kind` of the row.
     */
    public fun kind(value: String): AddModuleCommand = next(line.with("--kind", value))

    /**
     * Same as `katachi agent add module --responsibility <value>` on the command line: what the module is responsible for.
     *
     * @param value Becomes `responsibility` of the row.
     */
    public fun responsibility(value: String): AddModuleCommand = next(line.with("--responsibility", value))

    /**
     * Same as `katachi agent add module --buildFile <value>` on the command line: its build file.
     *
     * @param value Becomes `buildFile` of the row.
     */
    public fun buildFile(value: String): AddModuleCommand = next(line.with("--buildFile", value))

    /**
     * Same as `katachi agent add module --deviation <value>` on the command line: how it differs from the other modules of the same kind. Leave it out when it does not.
     *
     * @param value Becomes `deviation` of the row.
     */
    public fun deviation(value: String): AddModuleCommand = next(line.with("--deviation", value))

    /**
     * Appends [arguments] as they are, for an option this builder has no method for yet:
     * `arg("--new-option", "v")` is `katachi agent add module ... --new-option v` on the command line.
     *
     * @param arguments Such as `"--new-option", "value"`.
     */
    public fun arg(vararg arguments: String): AddModuleCommand = next(line.plus(arguments.toList()))

    override val arguments: List<String> get() = line.arguments

    override fun execute(): Int = katachi.run(arguments)

    /** The command line as typed in a shell: `katachi agent add module --path feature/home --kind feature`. Values a shell would split are quoted. */
    override fun toString(): String = commandText(arguments)
}

/**
 * The `katachi agent add role` command: appends one row to `roles` of the report.
 *
 * Building one is writing `katachi agent add role ...` in a shell: the methods are named after the
 * options of `katachi agent add role --help` and add the same flag. Each returns a new
 * command; calling one twice keeps the last value, except for the ones that take several values, which add to them.
 *
 * ## Example 1: build the command, print it and run it
 *
 * ```kt
 * val command = Katachi().agent.add.role.name("UseCase").group("feature.domain").examples("GetUserUseCase.kt")
 * println(command) // katachi agent add role --name UseCase --group feature.domain --examples GetUserUseCase.kt
 * command.execute()
 * ```
 *
 * The `execute()` above runs the same thing as this line in a shell:
 *
 * ```sh
 * katachi agent add role --name UseCase --group feature.domain --examples GetUserUseCase.kt
 * ```
 *
 * @see Katachi
 */
public class AddRoleCommand internal constructor(
    private val katachi: Katachi,
    private val line: CommandLine,
) : KatachiCommand {
    private fun next(line: CommandLine) = AddRoleCommand(katachi, line)

    /**
     * Same as `katachi agent add role --importance <value>` on the command line: how much the Role matters, as a number such as `5`.
     *
     * @param value Becomes `importance` of the row.
     */
    public fun importance(value: String): AddRoleCommand = next(line.with("--importance", value))

    /**
     * Same as `katachi agent add role --name <value>` on the command line: the name of the Role.
     *
     * @param value Becomes `name` of the row.
     */
    public fun name(value: String): AddRoleCommand = next(line.with("--name", value))

    /**
     * Same as `katachi agent add role --layout <value>` on the command line: where its files go.
     *
     * @param value Becomes `layout` of the row.
     */
    public fun layout(value: String): AddRoleCommand = next(line.with("--layout", value))

    /**
     * Same as `katachi agent add role --naming <value>` on the command line: how its files are named.
     *
     * @param value Becomes `naming` of the row.
     */
    public fun naming(value: String): AddRoleCommand = next(line.with("--naming", value))

    /**
     * Same as `katachi agent add role --count <value>` on the command line: how many files it has, as a number.
     *
     * @param value Becomes `count` of the row.
     */
    public fun count(value: String): AddRoleCommand = next(line.with("--count", value))

    /**
     * Same as `katachi agent add role --note <value>` on the command line: a note; the reason, with `constraint-only`.
     *
     * @param value Becomes `note` of the row.
     */
    public fun note(value: String): AddRoleCommand = next(line.with("--note", value))

    /**
     * Same as `katachi agent add role --group <value>` on the command line: where the Role goes in the definition, nested with dots: `feature.ui`.
     *
     * @param value Becomes `group` of the row.
     */
    public fun group(value: String): AddRoleCommand = next(line.with("--group", value))

    /**
     * Same as `katachi agent add role --allowed <values>` on the command line, once for each value: what its files may depend on. Calling it again adds more.
     *
     * @param values Each becomes one entry of `allowed` of the row.
     */
    public fun allowed(vararg values: String): AddRoleCommand = next(line.withAppended("--allowed", values.toList()))

    /**
     * Same as `katachi agent add role --forbidden <values>` on the command line, once for each value: what its files must not depend on. Calling it again adds more.
     *
     * @param values Each becomes one entry of `forbidden` of the row.
     */
    public fun forbidden(vararg values: String): AddRoleCommand =
        next(line.withAppended("--forbidden", values.toList()))

    /**
     * Same as `katachi agent add role --examples <values>` on the command line, once for each value: files that belong to it. Calling it again adds more.
     *
     * @param values Each becomes one entry of `examples` of the row.
     */
    public fun examples(vararg values: String): AddRoleCommand = next(line.withAppended("--examples", values.toList()))

    /**
     * Same as `katachi agent add role --constraint-only` on the command line: records a Role that exists only to put a constraint on a Gradle build file. Needs a `note`.
     *
     * @param enabled False takes the option off again.
     */
    public fun `constraint-only`(enabled: Boolean = true): AddRoleCommand =
        next(line.withFlag("--constraint-only", enabled))

    /**
     * Appends [arguments] as they are, for an option this builder has no method for yet:
     * `arg("--new-option", "v")` is `katachi agent add role ... --new-option v` on the command line.
     *
     * @param arguments Such as `"--new-option", "value"`.
     */
    public fun arg(vararg arguments: String): AddRoleCommand = next(line.plus(arguments.toList()))

    override val arguments: List<String> get() = line.arguments

    override fun execute(): Int = katachi.run(arguments)

    /** The command line as typed in a shell: `katachi agent add role --name UseCase --group feature.domain --examples GetUserUseCase.kt`. Values a shell would split are quoted. */
    override fun toString(): String = commandText(arguments)
}

/**
 * The `katachi agent add tool` command: appends one row to `tools` of the report.
 *
 * Building one is writing `katachi agent add tool ...` in a shell: the methods are named after the
 * options of `katachi agent add tool --help` and add the same flag. Each returns a new
 * command; calling one twice keeps the last value.
 *
 * ## Example 1: build the command, print it and run it
 *
 * ```kt
 * val command = Katachi().agent.add.tool.name("detekt").configPath("config/detekt.yml")
 * println(command) // katachi agent add tool --name detekt --configPath config/detekt.yml
 * command.execute()
 * ```
 *
 * The `execute()` above runs the same thing as this line in a shell:
 *
 * ```sh
 * katachi agent add tool --name detekt --configPath config/detekt.yml
 * ```
 *
 * @see Katachi
 */
public class AddToolCommand internal constructor(
    private val katachi: Katachi,
    private val line: CommandLine,
) : KatachiCommand {
    private fun next(line: CommandLine) = AddToolCommand(katachi, line)

    /**
     * Same as `katachi agent add tool --name <value>` on the command line: the name of the tool.
     *
     * @param value Becomes `name` of the row.
     */
    public fun name(value: String): AddToolCommand = next(line.with("--name", value))

    /**
     * Same as `katachi agent add tool --configPath <value>` on the command line: where its configuration is.
     *
     * @param value Becomes `configPath` of the row.
     */
    public fun configPath(value: String): AddToolCommand = next(line.with("--configPath", value))

    /**
     * Same as `katachi agent add tool --declareInKatachi <value>` on the command line: whether the definition declares it, and why.
     *
     * @param value Becomes `declareInKatachi` of the row.
     */
    public fun declareInKatachi(value: String): AddToolCommand = next(line.with("--declareInKatachi", value))

    /**
     * Same as `katachi agent add tool --note <value>` on the command line: a note.
     *
     * @param value Becomes `note` of the row.
     */
    public fun note(value: String): AddToolCommand = next(line.with("--note", value))

    /**
     * Appends [arguments] as they are, for an option this builder has no method for yet:
     * `arg("--new-option", "v")` is `katachi agent add tool ... --new-option v` on the command line.
     *
     * @param arguments Such as `"--new-option", "value"`.
     */
    public fun arg(vararg arguments: String): AddToolCommand = next(line.plus(arguments.toList()))

    override val arguments: List<String> get() = line.arguments

    override fun execute(): Int = katachi.run(arguments)

    /** The command line as typed in a shell: `katachi agent add tool --name detekt --configPath config/detekt.yml`. Values a shell would split are quoted. */
    override fun toString(): String = commandText(arguments)
}

/**
 * The `katachi agent add excluded` command: appends one row to `excluded` of the report.
 *
 * Building one is writing `katachi agent add excluded ...` in a shell: the methods are named after the
 * options of `katachi agent add excluded --help` and add the same flag. Each returns a new
 * command; calling one twice keeps the last value.
 *
 * ## Example 1: build the command, print it and run it
 *
 * ```kt
 * val command = Katachi().agent.add.excluded.path("legacy").reason("to be removed")
 * println(command) // katachi agent add excluded --path legacy --reason 'to be removed'
 * command.execute()
 * ```
 *
 * The `execute()` above runs the same thing as this line in a shell:
 *
 * ```sh
 * katachi agent add excluded --path legacy --reason 'to be removed'
 * ```
 *
 * @see Katachi
 */
public class AddExcludedCommand internal constructor(
    private val katachi: Katachi,
    private val line: CommandLine,
) : KatachiCommand {
    private fun next(line: CommandLine) = AddExcludedCommand(katachi, line)

    /**
     * Same as `katachi agent add excluded --path <value>` on the command line: the path left out of the definition. Not one `.gitignore` leaves out already.
     *
     * @param value Becomes `path` of the row.
     */
    public fun path(value: String): AddExcludedCommand = next(line.with("--path", value))

    /**
     * Same as `katachi agent add excluded --reason <value>` on the command line: why it is left out.
     *
     * @param value Becomes `reason` of the row.
     */
    public fun reason(value: String): AddExcludedCommand = next(line.with("--reason", value))

    /**
     * Appends [arguments] as they are, for an option this builder has no method for yet:
     * `arg("--new-option", "v")` is `katachi agent add excluded ... --new-option v` on the command line.
     *
     * @param arguments Such as `"--new-option", "value"`.
     */
    public fun arg(vararg arguments: String): AddExcludedCommand = next(line.plus(arguments.toList()))

    override val arguments: List<String> get() = line.arguments

    override fun execute(): Int = katachi.run(arguments)

    /** The command line as typed in a shell: `katachi agent add excluded --path legacy --reason 'to be removed'`. Values a shell would split are quoted. */
    override fun toString(): String = commandText(arguments)
}

/**
 * The `katachi agent add template` command: appends one row to `templates` of the report.
 *
 * Building one is writing `katachi agent add template ...` in a shell: the methods are named after the
 * options of `katachi agent add template --help` and add the same flag. Each returns a new
 * command; calling one twice keeps the last value, except for the ones that take several values, which add to them.
 *
 * ## Example 1: build the command, print it and run it
 *
 * ```kt
 * val command = Katachi().agent.add.template.role("UseCase").params("name")
 * println(command) // katachi agent add template --role UseCase --params name
 * command.execute()
 * ```
 *
 * The `execute()` above runs the same thing as this line in a shell:
 *
 * ```sh
 * katachi agent add template --role UseCase --params name
 * ```
 *
 * @see Katachi
 */
public class AddTemplateCommand internal constructor(
    private val katachi: Katachi,
    private val line: CommandLine,
) : KatachiCommand {
    private fun next(line: CommandLine) = AddTemplateCommand(katachi, line)

    /**
     * Same as `katachi agent add template --role <value>` on the command line: the Role the template makes files of.
     *
     * @param value Becomes `role` of the row.
     */
    public fun role(value: String): AddTemplateCommand = next(line.with("--role", value))

    /**
     * Same as `katachi agent add template --basedOn <value>` on the command line: the existing file it is made from.
     *
     * @param value Becomes `basedOn` of the row.
     */
    public fun basedOn(value: String): AddTemplateCommand = next(line.with("--basedOn", value))

    /**
     * Same as `katachi agent add template --reason <value>` on the command line: why it helps.
     *
     * @param value Becomes `reason` of the row.
     */
    public fun reason(value: String): AddTemplateCommand = next(line.with("--reason", value))

    /**
     * Same as `katachi agent add template --files <values>` on the command line, once for each value: the files it makes. Calling it again adds more.
     *
     * @param values Each becomes one entry of `files` of the row.
     */
    public fun files(vararg values: String): AddTemplateCommand = next(line.withAppended("--files", values.toList()))

    /**
     * Same as `katachi agent add template --params <values>` on the command line, once for each value: the names filled in when the template is used, such as `name`. Calling it again adds more.
     *
     * @param values Each becomes one entry of `params` of the row.
     */
    public fun params(vararg values: String): AddTemplateCommand = next(line.withAppended("--params", values.toList()))

    /**
     * Appends [arguments] as they are, for an option this builder has no method for yet:
     * `arg("--new-option", "v")` is `katachi agent add template ... --new-option v` on the command line.
     *
     * @param arguments Such as `"--new-option", "value"`.
     */
    public fun arg(vararg arguments: String): AddTemplateCommand = next(line.plus(arguments.toList()))

    override val arguments: List<String> get() = line.arguments

    override fun execute(): Int = katachi.run(arguments)

    /** The command line as typed in a shell: `katachi agent add template --role UseCase --params name`. Values a shell would split are quoted. */
    override fun toString(): String = commandText(arguments)
}
