package me.tbsten.katachi.template

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.KatachiDeclarationException
import me.tbsten.katachi.dsl.DeclarationSite

/**
 * A run of a template set was short of values for the set's own parameters, or was given one that
 * its parameter cannot read.
 *
 * Both are reported at once, so that a second run is not needed to learn about the second problem.
 * Only the parameters of the set are covered here: what a member receives is judged by
 * [KatachiInvalidTemplateSetArgException].
 *
 * ## Example 1: catch a run that gave a set no value for its required parameter
 * ```kt
 * val arch = architecture {
 *     "feature".group {
 *         "FeatureScreen".role {
 *             layout { "feature" / "FeatureScreen.kt".file().template { "// screen" } }
 *         }
 *         "NewFeature".templateSet {
 *             val name by stringParameter()
 *             template("feature.FeatureScreen")
 *         }
 *     }
 * }
 * val thrown = shouldThrow<KatachiTemplateSetParameterValueException> {
 *     arch.process(GenerateCodeFromTemplate, GenerateCodeFromTemplate.Args(template = listOf("feature.NewFeature"))).getOrThrow()
 * }
 * thrown.missing shouldBe listOf("name")
 * ```
 *
 * @see GenerateCodeFromTemplate
 */
@ExperimentalKatachiApi
public class KatachiTemplateSetParameterValueException internal constructor(
    /** The set's specifier, such as `feature.NewFeature`. */
    public val templateSet: String,
    /** The parameters of the set that had neither a value nor a default, sorted. */
    public val missing: List<String>,
    /** The parameters whose value could not be read, by name, with the value that was passed. */
    public val invalid: Map<String, String>,
    /** What each of the parameters named above takes, for the ones that do not take any string. */
    public val accepted: Map<String, String>,
    /** Where the template set is declared. */
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = buildString {
        appendLine("""Template set "$templateSet" declared at $declaredAt was run with values its parameters cannot use.""")
        if (missing.isNotEmpty()) {
            appendLine("  No value for: ${missing.joinToString(", ")}.")
        }
        for ((name, raw) in invalid) {
            appendLine("""  $name="$raw" cannot be read as the type $name was declared with.""")
        }
        appendLine("A parameter declared with no default needs a value on every run, and a value that does not fit is refused rather than guessed at.")
        val names = missing + invalid.keys
        append("Pass ")
        append(names.joinToString(" ") { "--arg $it=<value>" })
        append(".")
        val takes = names.mapNotNull { name -> accepted[name]?.let { "$name takes $it" } }
        if (takes.isNotEmpty()) append(" ").append(takes.joinToString("; ", postfix = "."))
    },
)

/**
 * A member of a template set was given `arg(...)` values that its template cannot use.
 *
 * A member only receives what its `template("...") { }` block passes with `arg(...)`; a parameter
 * of the set with the same name is not passed on by itself. [problem] says which of the three it
 * is, and [declaredAt] is the line of the member's `template("...")`, which is where to add or
 * change an `arg(...)`.
 *
 * ## Example 1: catch an arg name the member does not take
 * ```kt
 * val arch = architecture {
 *     "feature".group {
 *         "FeatureScreen".role {
 *             layout { "feature" / "FeatureScreen.kt".file().template { "// screen" } }
 *         }
 *         "NewFeature".templateSet {
 *             template("feature.FeatureScreen") { arg("typo", "x") }
 *         }
 *     }
 * }
 * val thrown = shouldThrow<KatachiInvalidTemplateSetArgException> {
 *     arch.process(GenerateCodeFromTemplate, GenerateCodeFromTemplate.Args(template = listOf("feature.NewFeature"))).getOrThrow()
 * }
 * thrown.problem shouldBe KatachiInvalidTemplateSetArgException.Problem.UnknownName
 * ```
 *
 * @see GenerateCodeFromTemplate
 */
@ExperimentalKatachiApi
public class KatachiInvalidTemplateSetArgException internal constructor(
    /** The set's specifier, such as `feature.NewFeature`. */
    public val templateSet: String,
    /** The member's specifier, as written in `template("...")`. */
    public val member: String,
    /** Which way the values are wrong. */
    public val problem: Problem,
    /**
     * The names the problem is about: the unknown `arg` names, the names the member was given no
     * value for, or the names whose value could not be read.
     */
    public val names: List<String>,
    /** The names the member accepts: its captures and its parameters on any branch, sorted. */
    public val accepted: List<String>,
    /** The line of the member's `template("...")`: where to add or change an `arg(...)`. */
    public val declaredAt: DeclarationSite,
    /** The line of the template set. */
    public val templateSetDeclaredAt: DeclarationSite,
    /** For [Problem.InvalidValue]: the value each of [names] was given, by name. Empty otherwise. */
    public val values: Map<String, String> = emptyMap(),
    /** For [Problem.InvalidValue]: what each of [names] takes, for the ones that do not take any string. */
    public val acceptedValues: Map<String, String> = emptyMap(),
    /** For [Problem.InvalidValue]: names the member was also given no value for, sorted. Empty otherwise. */
    public val alsoMissing: List<String> = emptyList(),
    cause: Throwable? = null,
) : KatachiDeclarationException(
    message = buildString {
        when (problem) {
            Problem.UnknownName -> {
                appendLine("""Template set "$templateSet" passes ${names.joinToString(", ") { "\"$it\"" }} to member "$member", which takes no such name.""")
                appendLine("A member accepts the names of its captures and of its parameters, on any branch.")
                if (accepted.isEmpty()) {
                    appendLine("This member accepts no name at all.")
                } else {
                    appendLine("Accepted names: ${accepted.joinToString(", ")}.")
                }
                append("Fix or remove the arg(...) call in template(\"$member\") at $declaredAt.")
            }
            Problem.Missing -> {
                appendLine("""Template set "$templateSet" does not give member "$member" a value for ${names.joinToString(", ") { "\"$it\"" }}.""")
                appendLine("A member of a template set only receives what its template(\"...\") { } block passes with arg(...);")
                appendLine("a parameter of the template set of the same name is not passed on by itself.")
                append("Add ${names.joinToString(" and ") { "arg(\"$it\", ...)" }} to template(\"$member\") at $declaredAt.")
            }
            Problem.InvalidValue -> {
                appendLine("""Template set "$templateSet" gives member "$member" a value that its parameter ${names.joinToString(", ") { "\"$it\"" }} cannot read.""")
                for (name in names) {
                    val given = values[name]?.let { "$name=\"$it\" " } ?: "$name "
                    val takes = acceptedValues[name]?.let { "$name takes $it" } ?: "$name takes a different kind of value"
                    appendLine("  ${given}does not fit: $takes.")
                }
                appendLine("A parameter reads its value as the type it was declared with, and a value that does not fit is refused rather than guessed at.")
                append("Change ${names.joinToString(" and ") { "arg(\"$it\", ...)" }} in template(\"$member\") at $declaredAt.")
                if (alsoMissing.isNotEmpty()) {
                    append("\nThe member is also short of a value for ${alsoMissing.joinToString(", ") { "\"$it\"" }}. ")
                    append("Add ${alsoMissing.joinToString(" and ") { "arg(\"$it\", ...)" }} to template(\"$member\") at $declaredAt.")
                }
            }
        }
    },
    cause = cause,
) {
    /**
     * Which way the values given to a member are wrong.
     *
     * ## Example 1: branch on the problem
     * ```kt
     * when (thrown.problem) {
     *     KatachiInvalidTemplateSetArgException.Problem.UnknownName -> thrown.accepted
     *     else -> thrown.names
     * }
     * ```
     */
    public enum class Problem {
        /** An `arg` name that is neither a capture nor a parameter of the member. */
        UnknownName,

        /** The member needs a value that no `arg(...)` gave. */
        Missing,

        /** A parameter of the member cannot read the value an `arg(...)` gave. */
        InvalidValue,
    }
}
