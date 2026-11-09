package me.tbsten.katachi.dsl

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.KatachiDeclarationException

/**
 * A `"name".templateSet { }` is written in a way that cannot work.
 *
 * Thrown when `architecture { }` is evaluated, or when files are generated or previewed.
 *
 * [problem][KatachiInvalidTemplateSetException.problem] says which of the seven it is, and [name][KatachiInvalidTemplateSetException.name] carries the parameter or `arg` it is
 * about when there is one.
 *
 * ## Example 1: catch a value read where only a member's block may read it
 * ```kt
 * val thrown = shouldThrow<KatachiInvalidTemplateSetException> {
 *     architecture {
 *         "feature".group {
 *             "NewFeature".templateSet {
 *                 val withTest by booleanParameter(default = true)
 *                 if (withTest) template("feature.FeatureTest")
 *                 template("feature.FeatureScreen")
 *             }
 *         }
 *     }
 * }
 * thrown.problem shouldBe KatachiInvalidTemplateSetException.Problem.ValueReadOutsideMember
 * ```
 *
 * @see TemplateSetScope
 */
@ExperimentalKatachiApi
public class KatachiInvalidTemplateSetException internal constructor(
    /** The set's specifier, such as `feature.NewFeature`. */
    public val templateSet: String,
    /** Which way the set is wrong. */
    public val problem: Problem,
    /** The parameter or `arg` name the problem is about, or `null` when it is about no name. */
    public val name: String?,
    /** Where the problem is: the line to change. */
    public val declaredAt: DeclarationSite,
    /**
     * The line the problem repeats or contradicts, or `null` when there is none.
     *
     * A parameter read
     * outside a member has none: [declaredAt][KatachiInvalidTemplateSetException.declaredAt] is the line the parameter is declared on, which is
     * also the line Kotlin attributes a read of a local delegated property to.
     */
    public val firstDeclaredAt: DeclarationSite?,
) : KatachiDeclarationException(
    message = messageOf(templateSet, problem, name, declaredAt, firstDeclaredAt),
) {
    /**
     * What is wrong with a template set.
     *
     * ## Example 1: branch on the problem
     * ```kt
     * when (thrown.problem) {
     *     KatachiInvalidTemplateSetException.Problem.Empty -> "add a member"
     *     else -> "read the message"
     * }
     * ```
     */
    public enum class Problem {
        /** The set has no `template(...)` member. */
        Empty,

        /** A parameter's value was read outside the block of a `template(...)`. */
        ValueReadOutsideMember,

        /** Two parameters of the set were written through properties of the same name. */
        DuplicateParameter,

        /** A parameter was written without `by`, so it has no name. */
        UnboundParameter,

        /** A parameter is named `template` or `onExisting`, which `katachiTemplate` reads itself. */
        ReservedParameterName,

        /** One member was given the same `arg` name twice. */
        DuplicateArg,

        /** The set's block declared different members on a later run than when `architecture { }` was evaluated. */
        MembersChanged,
    }
}

private fun messageOf(
    templateSet: String,
    problem: KatachiInvalidTemplateSetException.Problem,
    name: String?,
    declaredAt: DeclarationSite,
    firstDeclaredAt: DeclarationSite?,
): String = when (problem) {
    KatachiInvalidTemplateSetException.Problem.Empty -> """
        Template set "$templateSet" declared at $declaredAt has no template("...") member.
        A template set generates its members together, so a set with none would generate nothing.
        Add a template("group.Role") { } to the set, or remove the set.
    """.trimIndent()

    KatachiInvalidTemplateSetException.Problem.ValueReadOutsideMember -> """
        Template set "$templateSet" reads its parameter "$name", declared at $declaredAt, outside a template("...") { } block.
        The read is in the template set's own block, not inside a template("...") { }. The members of a template set are fixed when the definition is evaluated, before any --arg is known, so a value cannot decide them.
        Read the value inside template("...") { } and pass it with arg(...), or declare a second template set for the other members.
    """.trimIndent()

    KatachiInvalidTemplateSetException.Problem.DuplicateParameter -> """
        Template set "$templateSet" declares the parameter "$name" twice, at $firstDeclaredAt and at $declaredAt.
        One --arg $name=<value> cannot mean two parameters.
        Rename one of them, or remove the repeat.
    """.trimIndent()

    KatachiInvalidTemplateSetException.Problem.UnboundParameter -> """
        Template set "$templateSet" has a parameter that never reached a property, declared at $declaredAt.
        A parameter takes its name from the property it is written through, so one written without `by` has no name: no --arg can reach it, and interpolating it yields the parameter itself rather than a value.
        Write it as `val name by stringParameter()`, or with the typed function it was declared with.
    """.trimIndent()

    KatachiInvalidTemplateSetException.Problem.ReservedParameterName -> """
        Template set "$templateSet" declares a parameter named "$name" at $declaredAt.
        "template" and "onExisting" are --arg names katachiTemplate reads itself, so a parameter of that name could never receive a value.
        Rename the property, such as `val ${name}Value by stringParameter()`.
    """.trimIndent()

    KatachiInvalidTemplateSetException.Problem.DuplicateArg -> """
        Template set "$templateSet" passes "$name" twice in one template("...") block, at $firstDeclaredAt and at $declaredAt.
        A member takes one value per name.
        Remove one of the two arg("$name", ...) calls.
    """.trimIndent()

    KatachiInvalidTemplateSetException.Problem.MembersChanged -> """
        Template set "$templateSet" declared at $declaredAt declared different members when it was run again.
        The members are fixed when the definition is evaluated, so the block must write the same template("...") calls every time, whatever the arguments or the environment.
        Move the decision into the values passed with arg(...), or declare a second template set for the other members.
    """.trimIndent()
}

/**
 * A `template("...")` of a template set names a template that cannot be used as a member.
 *
 * Raised when the definition is evaluated, because the specifiers are resolved against every
 * template the definition declares once all of them are known.
 *
 * ## Example 1: catch a member that names no template
 * ```kt
 * val thrown = shouldThrow<KatachiInvalidTemplateSetMemberException> {
 *     architecture {
 *         "feature".group {
 *             "NewFeature".templateSet { template("feature.Missing") }
 *         }
 *     }.assert()
 * }
 * thrown.problem shouldBe KatachiInvalidTemplateSetMemberException.Problem.Unknown
 * ```
 *
 * @see TemplateSetScope.template
 */
@ExperimentalKatachiApi
public class KatachiInvalidTemplateSetMemberException internal constructor(
    /** The set's specifier, such as `feature.NewFeature`. */
    public val templateSet: String,
    /** The specifier the member was written with. */
    public val member: String,
    /** Which way the member is wrong. */
    public val problem: Problem,
    /**
     * The templates the specifier could have meant.
     *
     * Those it matched when [problem][KatachiInvalidTemplateSetMemberException.problem] is [Problem.Ambiguous], every declared template when it is
     * [Problem.Unknown], and the one template both members name when it is [Problem.Duplicate].
     */
    public val candidates: List<String>,
    /** Where the `template(...)` was written. */
    public val declaredAt: DeclarationSite,
    /** For [Problem.Duplicate], where the earlier `template(...)` of the same template was written. */
    public val firstDeclaredAt: DeclarationSite? = null,
) : KatachiDeclarationException(
    message = buildString {
        when (problem) {
            Problem.Unknown -> {
                appendLine("""Template set "$templateSet" has a member "$member" at $declaredAt that names no template.""")
                appendLine("A member is written as `--arg template=` takes it, such as group.Role or group.Role.id, and cannot be another template set.")
                append(
                    if (candidates.isEmpty()) {
                        "The definition declares no template at all; add a .template { } to a role first."
                    } else {
                        "Use one of: ${candidates.joinToString(", ")}."
                    },
                )
            }
            Problem.Ambiguous -> {
                appendLine("""Template set "$templateSet" has a member "$member" at $declaredAt that names more than one template.""")
                appendLine("It matches ${candidates.joinToString(", ")}.")
                append("Write the specifier in full, such as template(\"${candidates.first()}\").")
            }
            Problem.Duplicate -> {
                val resolved = candidates.single()
                appendLine("""Template set "$templateSet" has a member "$member" at $declaredAt that names "$resolved", which the member at $firstDeclaredAt already lists.""")
                appendLine("One template can be generated only once per run, however it is spelled.")
                append("Remove the repeat, or pass the values you need through one template(\"$resolved\") { } block.")
            }
        }
    },
) {
    /**
     * What is wrong with a member.
     *
     * ## Example 1: branch on the problem
     * ```kt
     * when (thrown.problem) {
     *     KatachiInvalidTemplateSetMemberException.Problem.Unknown -> thrown.candidates
     *     else -> emptyList()
     * }
     * ```
     */
    public enum class Problem {
        /** The specifier matches no template. */
        Unknown,

        /** The specifier matches more than one template. */
        Ambiguous,

        /** The same template is listed twice in one set. */
        Duplicate,
    }
}
