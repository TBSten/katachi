package me.tbsten.katachi.dsl

import me.tbsten.katachi.KatachiDeclarationException
import me.tbsten.katachi.dsl.internal.InvalidArgReason
import me.tbsten.katachi.dsl.internal.InvalidTemplateValue
import me.tbsten.katachi.dsl.internal.TemplateParameterType

/**
 * A template parameter that never reached a property, which is `by` left out.
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
    /** Where each unnamed parameter was declared. */
    public val parameterSites: List<DeclarationSite>,
    /** The function the first unnamed one was declared with, for the fix to quote. */
    internal val declaredWith: String,
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
            "A template parameter takes its name from the property it is written through, so " +
                "one written without `by` has no name: no argument can reach it, and " +
                "interpolating it yields the parameter itself rather than a value.",
        )
        append("Write it as `val name by $declaredWith`.")
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
 * One template parameter was written through two properties.
 *
 * A parameter is named by its property, so a single one behind two of them would have two names
 * and one value. Holding the result of `stringParameter()` (or any of its typed siblings) in a
 * `val` and reusing it is the way to get here.
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
    /** The function it was declared with, for the fix to quote. */
    internal val declaredWith: String,
    /** Where the parameter was declared. */
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
        append("Call $declaredWith once per parameter.")
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
 * // `katachiTemplate --arg roleName=UseCase` with no `--arg name=...` is refused with
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
    /** What each of [names] accepts, for the ones not declared with `stringParameter()`. */
    internal val accepted: Map<String, String>,
    /** Where `template { }` was written. */
    public val declaredAt: DeclarationSite,
    cause: Throwable?,
) : KatachiDeclarationException(
    message = buildString {
        appendLine(
            """Template of role "$role" declared at $declaredAt was run without values for: """ +
                "${names.joinToString(", ")}.",
        )
        appendLine("A parameter declared with no default has to be given a value on every run.")
        append(
            """Pass them as --arg, on the command line or in processors { args("template") { } }: """ +
                argPlaceholders(names) + ".",
        )
        acceptedLine(names, accepted)?.let { append("\n").append(it) }
    },
    cause = cause,
)

/**
 * A run passed a value that its parameter cannot read as the type it was declared with.
 *
 * Every such value is reported at once, together with any value the run was missing: the
 * template is replayed to the end first. A value that does not fit is refused rather than read
 * as `false`, `0` or the default -- a guess there would produce the wrong files without a word.
 *
 * ## Example 1: declare a template whose run can pass an unreadable value
 * ```kt
 * val arch = architecture {
 *     "data".group {
 *         "Repository" {
 *             template {
 *                 val name by stringParameter()
 *                 val withImpl by booleanParameter(default = true)
 *                 file("${name}Repository.kt") { "interface ${name}Repository" }
 *                 if (withImpl) file("${name}RepositoryImpl.kt") { "class ${name}RepositoryImpl" }
 *             }
 *         }
 *     }
 * }
 * // `katachiTemplate --arg roleName=Repository --arg name=User --arg withImpl=yes` is
 * // refused with `withImpl="yes": withImpl is a booleanParameter() declared at ...
 * // Accepted values: true, false.`
 * arch.allRoles.single().name shouldBe "Repository"
 * ```
 *
 * @see TemplateScope.booleanParameter
 * @see TemplateScope.intParameter
 * @see TemplateScope.enumParameter
 */
public class KatachiInvalidTemplateParameterValueException internal constructor(
    /** The role whose template was replayed. */
    public val role: String,
    /** Every parameter whose value could not be read, in declaration order. */
    public val names: List<String>,
    internal val problems: List<InvalidTemplateValue>,
    /** Names the run was also short of, sorted. Empty when none were missing. */
    public val missing: List<String>,
    internal val missingAccepted: Map<String, String>,
    /** Where `template { }` was written. */
    public val declaredAt: DeclarationSite,
    cause: Throwable?,
) : KatachiDeclarationException(
    message = buildString {
        appendLine(
            """Template of role "$role" declared at $declaredAt was given values it cannot """ +
                "read: ${names.joinToString(", ")}.",
        )
        for (problem in problems) appendLine("  " + problemLine(problem))
        appendLine(
            "Each parameter is read as the type it was declared with, and a value that does not " +
                "fit is refused rather than guessed at.",
        )
        val first = problems.first()
        append(
            """Pass a value it accepts as --arg, on the command line or in processors """ +
                """{ args("template") { } }, e.g. --arg ${first.name}=""" +
                first.type.exampleValue(first.default) + ".",
        )
        if (missing.isNotEmpty()) {
            append(
                "\nThe run was also short of values for: ${missing.joinToString(", ")}. " +
                    "Pass ${argPlaceholders(missing)}.",
            )
            acceptedLine(missing, missingAccepted)?.let { append(" ").append(it) }
        }
    },
    cause = cause,
)

/**
 * `enumParameter(entries)` was given an enum with no entries.
 *
 * No `--arg` value could ever be read as such an enum, so every run of the template would fail.
 * It is refused where it is declared, like a bad `file()` name.
 *
 * ## Example 1: declare an enum parameter over an enum that has entries
 * ```kt
 * enum class Visibility { Public, Internal } // an enum with no entries here is what is refused
 *
 * val arch = architecture {
 *     "domain".group {
 *         "UseCase" {
 *             template {
 *                 val visibility by enumParameter(Visibility.entries)
 *                 file("GetUserUseCase.kt") { "${visibility.name.lowercase()} class GetUserUseCase" }
 *             }
 *         }
 *     }
 * }
 * arch.allRoles.single().name shouldBe "UseCase"
 * ```
 *
 * @see TemplateScope.enumParameter
 */
public class KatachiEmptyEnumTemplateParameterException internal constructor(
    /** The role whose template declared it. */
    public val role: String,
    /** Where `enumParameter()` was called. */
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = buildString {
        appendLine(
            """enumParameter() of role "$role" declared at $declaredAt was given an enum with """ +
                "no entries.",
        )
        appendLine(
            "No --arg value could ever be read as it, so every run of this template would fail.",
        )
        append("Give the enum at least one entry, or declare the parameter with stringParameter().")
    },
)

/** `--arg a=<value> --arg b=<value>`: a shape to paste, with no `|` a shell would take as a pipe. */
private fun argPlaceholders(names: List<String>): String =
    names.joinToString(" ") { "--arg $it=<value>" }

/** `withImpl takes true or false; pageSize takes a whole number.`, or `null` when all take anything. */
private fun acceptedLine(names: List<String>, accepted: Map<String, String>): String? {
    val parts = names.mapNotNull { name -> accepted[name]?.let { "$name takes $it" } }
    return if (parts.isEmpty()) null else parts.joinToString("; ", postfix = ".")
}

/** One unreadable value, as one line: what was passed, what the parameter is, what it takes. */
private fun problemLine(problem: InvalidTemplateValue): String {
    val type = problem.type
    val head = """${problem.name}="${problem.raw}": ${problem.name} is ${articleFor(type.declaredWith)} """ +
        type.declaredWith
    return when {
        problem.reason == InvalidArgReason.OutOfRange ->
            "$head declared at ${problem.parameterDeclaredAt}, and that number is outside " +
                "${Int.MIN_VALUE}..${Int.MAX_VALUE}."
        type is TemplateParameterType.EnumType<*> ->
            "$head of ${type.label} declared at ${problem.parameterDeclaredAt} and takes an entry " +
                "name spelled exactly as declared. Accepted values: " +
                type.acceptedValues.joinToString(", ", postfix = ".")
        type.acceptedValues.isNotEmpty() ->
            "$head declared at ${problem.parameterDeclaredAt}. Accepted values: " +
                type.acceptedValues.joinToString(", ", postfix = ".")
        // The same example as the closing "e.g." line, so one message shows one accepted value.
        else ->
            "$head declared at ${problem.parameterDeclaredAt} and takes " +
                "${type.acceptedDescription ?: "any value"}, such as " +
                "${type.exampleValue(problem.default)}."
    }
}

/** `an` before `intParameter()` / `enumParameter()`, `a` before the rest. */
private fun articleFor(word: String): String = if (word.first() in "aeiou") "an" else "a"
