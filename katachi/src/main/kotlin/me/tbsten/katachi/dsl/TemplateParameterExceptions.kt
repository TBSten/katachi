package me.tbsten.katachi.dsl

import me.tbsten.katachi.KatachiDeclarationException

/**
 * A `stringParameter()` that never reached a property, which is `by` left out.
 *
 * A parameter takes its name from the property it is written through, so one written as
 * `val name = stringParameter()` has no name at all: no `--arg` can reach it, and interpolating
 * it puts the parameter object into the template instead of a value. Kotlin cannot refuse that
 * on its own, so it is refused when the block ends -- the first moment every parameter of the
 * template is known.
 *
 * ## Example 1: catch a parameter written without `by`
 * ```kt
 * val arch = architecture {
 *     "domain".group {
 *         "UseCase" {
 *             template {
 *                 val name = stringParameter()
 *                 file("${name}UseCase.kt") { "" }
 *             }
 *         }
 *     }
 * }
 * // Raised when the template is replayed, not where it was written.
 * arch.allRoles.single().name shouldBe "UseCase"
 * ```
 *
 * @see TemplateScope.stringParameter
 */
public class KatachiUnboundTemplateParameterException internal constructor(
    /** The role whose template declared them. */
    public val role: String,
    /** Where each unnamed `stringParameter()` was called. */
    public val parameterSites: List<DeclarationSite>,
    /** Where `template { }` was written. */
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = buildString {
        appendLine(
            """Template of role "$role" declared at $declaredAt has """ +
                "${parameterSites.size} parameter(s) that never reached a property: " +
                "${parameterSites.joinToString(", ")}.",
        )
        appendLine(
            "stringParameter() takes its name from the property it is written through, so one " +
                "written without `by` has no name: no argument can reach it, and interpolating " +
                "it yields the parameter itself rather than a value.",
        )
        append("Write it as `val name by stringParameter()`.")
    },
)

/**
 * Two parameters of one template were written through properties of the same name.
 *
 * One `--arg name=<value>` cannot mean two parameters. Two `val name by stringParameter()` in
 * one block is a Kotlin error already; this catches the ones Kotlin cannot see, where the two
 * declarations sit in different nested scopes of the same template.
 *
 * ## Example 1: catch a parameter name declared twice
 * ```kt
 * val arch = architecture {
 *     "domain".group {
 *         "UseCase" {
 *             template {
 *                 val name by stringParameter()
 *                 run { val name by stringParameter(default = "other") }
 *                 file("${name}UseCase.kt") { "" }
 *             }
 *         }
 *     }
 * }
 * // Raised when the template is replayed, not where it was written.
 * arch.allRoles.single().name shouldBe "UseCase"
 * ```
 *
 * @see TemplateScope.stringParameter
 */
public class KatachiDuplicateTemplateParameterException internal constructor(
    /** The role whose template declared both. */
    public val role: String,
    /** The name declared twice. */
    public val name: String,
    /** Where the parameter that took the name first was declared. */
    public val firstDeclaredAt: DeclarationSite,
    /** Where the refused second one was declared. */
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = buildString {
        appendLine(
            """Duplicate template parameter "$name" declared at $declaredAt, in role "$role".""",
        )
        appendLine(
            "It was already declared at $firstDeclaredAt, and one argument of that name cannot " +
                "mean two parameters.",
        )
        append("Declare it once, and read that one property everywhere it is needed.")
    },
)

/**
 * One `stringParameter()` was written through two properties.
 *
 * A parameter is named by its property, so a single one behind two of them would have two names
 * and one value. Holding the result of `stringParameter()` in a `val` and reusing it is the way
 * to get here.
 *
 * ## Example 1: catch a parameter reused behind a second property
 * ```kt
 * val arch = architecture {
 *     "domain".group {
 *         "UseCase" {
 *             template {
 *                 val shared = stringParameter()
 *                 val name by shared
 *                 val other by shared
 *                 file("${name}${other}UseCase.kt") { "" }
 *             }
 *         }
 *     }
 * }
 * // Raised when the template is replayed, not where it was written.
 * arch.allRoles.single().name shouldBe "UseCase"
 * ```
 *
 * @see TemplateScope.stringParameter
 */
public class KatachiTemplateParameterReusedException internal constructor(
    /** The property name it was given first. */
    public val firstName: String,
    /** The property name it was then asked to answer to as well. */
    public val secondName: String,
    /** Where `stringParameter()` was called. */
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = buildString {
        appendLine(
            """The template parameter declared at $declaredAt is written through two """ +
                """properties, "$firstName" and "$secondName".""",
        )
        appendLine(
            "A parameter takes its name from its property, so one behind two of them would " +
                "have two names and a single value.",
        )
        append("Call stringParameter() once per parameter.")
    },
)

/**
 * The run had no value for one or more parameters, and no default to fall back on.
 *
 * Every missing name is reported at once: the template is replayed to the end first, so that a
 * second run is not needed to learn about the second missing value.
 *
 * ## Example 1: declare a template whose run can be short of values
 * ```kt
 * val arch = architecture {
 *     "domain".group {
 *         "UseCase" {
 *             template {
 *                 val name by stringParameter()
 *                 file("${name}UseCase.kt") { "interface ${name}UseCase" }
 *             }
 *         }
 *     }
 * }
 * // `--processor=template --arg roleName=UseCase` with no `--arg name=...` is refused with
 * // `Template of role "UseCase" declared at ProjectArchitecture.kt:5 was run without values
 * // for: name.`
 * arch.allRoles.single().name shouldBe "UseCase"
 * ```
 *
 * @see TemplateScope.stringParameter
 */
public class KatachiMissingTemplateParameterException internal constructor(
    /** The role whose template was replayed. */
    public val role: String,
    /** Every name the run was short of, sorted. */
    public val names: List<String>,
    /** Where `template { }` was written. */
    public val declaredAt: DeclarationSite,
    cause: Throwable?,
) : KatachiDeclarationException(
    message = buildString {
        appendLine(
            """Template of role "$role" declared at $declaredAt was run without values for: """ +
                "${names.joinToString(", ")}.",
        )
        appendLine(
            "A parameter declared with stringParameter() and no default has to be given a " +
                "value on every run.",
        )
        append(
            "Pass them on the command line: " +
                names.joinToString(" ") { "--arg $it=<value>" } + ".",
        )
    },
    cause = cause,
)
