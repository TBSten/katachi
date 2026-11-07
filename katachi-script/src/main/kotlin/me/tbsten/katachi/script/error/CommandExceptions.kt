package me.tbsten.katachi.script.error

import me.tbsten.katachi.KatachiDeclarationException
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.script.command.KatachiCommand

/**
 * A command was run without an option its subcommand requires.
 *
 * Thrown by [KatachiCommand.execute] before anything runs, so a script stops at the line that
 * built the command rather than halfway through the subcommand.
 *
 * ## Example 1: scaffold without a package
 *
 * ```kt
 * shouldThrow<KatachiMissingCommandOptionException> {
 *     Katachi().scaffold.execute()
 * }.option shouldBe "--package"
 * ```
 *
 * @property command The command line as it was built.
 * @property option The option that is missing, such as `--package`.
 * @property declaredAt The line of the script that built the command.
 */
public class KatachiMissingCommandOptionException internal constructor(
    public val command: String,
    public val option: String,
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = buildString {
        appendLine("`$command` built at $declaredAt has no $option.")
        appendLine("The subcommand cannot run without it, so nothing was run.")
        append("Add .`${option.removePrefix("--")}`(...) to the command")
        if (option == "--package") append(", or pass Katachi(defaultPackage = ...)")
        append(".")
    },
)
