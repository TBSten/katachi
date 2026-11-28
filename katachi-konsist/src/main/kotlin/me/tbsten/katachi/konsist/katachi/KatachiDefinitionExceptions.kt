package me.tbsten.katachi.konsist.katachi

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.KatachiDeclarationException
import me.tbsten.katachi.dsl.DeclarationSite

/**
 * An argument of [katachiDefinition] that cannot name a package, a directory or a file.
 *
 * The values end up in paths and in file name patterns of the group's layouts, so one that is
 * not path-shaped would declare roles covering nothing, and the files that should have been
 * theirs would be reported as covered by no role.
 *
 * ## Example 1: catch a package that is a path
 * ```kt
 * val failure = shouldThrow<KatachiInvalidDefinitionArgumentException> {
 *   architecture { katachiDefinition(rootPackage = "com/example") }
 * }
 * failure.argument shouldBe "rootPackage"
 * ```
 *
 * @property argument the name of the argument, as written in the call.
 * @property value the rejected value, as written.
 * @property expected what the argument has to look like.
 * @property declaredAt where the call was written.
 */
@ExperimentalKatachiApi
public class KatachiInvalidDefinitionArgumentException internal constructor(
    /** The name of the argument, as written in the call. */
    public val argument: String,
    /** The rejected value, as written. */
    public val value: String,
    /** What the argument has to look like. */
    public val expected: String,
    /** Where the call was written. */
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = buildString {
        appendLine("Invalid argument $argument = \"$value\" of katachiDefinition declared at $declaredAt.")
        appendLine("It ends up in the layouts of the roles katachiDefinition declares, so it has to be $expected.")
        append("Change $argument at $declaredAt.")
    },
)
