package me.tbsten.katachi.template

import me.tbsten.katachi.KatachiDeclarationException

/**
 * `--arg template=` named something no template answers to.
 *
 * Almost always a typo, a template whose role or id was renamed, or a group left out of a
 * qualified name -- see the design draft's section 3 for how a specifier is read. The message
 * lists what the definition does declare, since the fix is one of those specifiers and reading
 * them is faster than opening the definition.
 *
 * ## Example 1: catch a misspelt specifier
 * ```kt
 * import io.kotest.assertions.throwables.shouldThrow
 * import me.tbsten.katachi.dsl.architecture
 * import me.tbsten.katachi.processor.process
 * import me.tbsten.katachi.template.GenerateCodeFromTemplate
 * import me.tbsten.katachi.template.KatachiUnknownTemplateException
 *
 * val arch = architecture { "domain".group { "UseCase" { } } }
 * val thrown = shouldThrow<KatachiUnknownTemplateException> {
 *     arch.process(GenerateCodeFromTemplate, GenerateCodeFromTemplate.Args(template = listOf("UseCse"))).getOrThrow()
 * }
 * thrown.declaredTemplates shouldBe emptyList()
 * ```
 *
 * @see GenerateCodeFromTemplate
 * @see DescribeTemplates
 */
public class KatachiUnknownTemplateException internal constructor(
    /** The specifier `--arg template=` carried, as written. */
    public val specifier: String,
    /** Every specifier the definition declares, sorted. */
    public val declaredTemplates: List<String>,
) : KatachiDeclarationException(
    message = buildString {
        appendLine("""Unknown template "$specifier".""")
        appendLine(
            "--arg template= names the template to run or describe, by role.id (or role alone " +
                "for a role with one template), and this definition declares no template of " +
                "that name.",
        )
        if (declaredTemplates.isEmpty()) {
            append("This definition declares no template at all.")
        } else {
            appendLine("Declared templates:")
            for (template in declaredTemplates) appendLine("  $template")
            append("Pass one of them.")
        }
    },
)

/**
 * `--arg template=` named something more than one template answers to.
 *
 * Reached two ways: a specifier that leaves its group out and its role has more than one
 * template, and a specifier read two ways at once -- `feature.Screen` as either the role `Screen`
 * of group `feature`, or the id `Screen` of a role `feature` -- see the design draft's section 3.
 * Either way, [candidates] is what to choose from: every one of them is a specifier
 * `--arg template=` accepts on its own.
 *
 * ## Example 1: catch a role name that no longer picks one template
 * ```kt
 * import io.kotest.assertions.throwables.shouldThrow
 * import me.tbsten.katachi.dsl.architecture
 * import me.tbsten.katachi.processor.process
 * import me.tbsten.katachi.template.GenerateCodeFromTemplate
 * import me.tbsten.katachi.template.KatachiAmbiguousTemplateException
 *
 * val thrown = shouldThrow<KatachiAmbiguousTemplateException> {
 *     projectArchitecture.process(
 *         GenerateCodeFromTemplate,
 *         GenerateCodeFromTemplate.Args(template = listOf("Repository")),
 *     ).getOrThrow()
 * }
 * thrown.candidates shouldBe listOf("data.Repository.repository", "data.Repository.repositoryImpl")
 * ```
 *
 * @see GenerateCodeFromTemplate
 * @see DescribeTemplates
 */
public class KatachiAmbiguousTemplateException internal constructor(
    /** The specifier `--arg template=` carried, as written. */
    public val specifier: String,
    /** Every template [specifier] could mean, each a complete `--arg template=` specifier, sorted. */
    public val candidates: List<String>,
) : KatachiDeclarationException(
    message = buildString {
        appendLine("""Template specifier "$specifier" names ${candidates.size} templates.""")
        for (candidate in candidates) appendLine("  $candidate")
        appendLine(
            "A specifier that leaves its group out reaches a role only while its plain name " +
                "picks one templated role, and a role reaches a template on its own only while " +
                "it has one -- picking one of several here would make the command line mean " +
                "more than one thing.",
        )
        append("""Pass one of the specifiers above, such as `--arg template=${candidates.first()}`.""")
    },
)

/**
 * `--arg template=` was given a set of specifiers that cannot mean what a run needs: none at all,
 * one that is empty, or two that name the same template.
 *
 * A run's values and its `onExisting` cover the whole set, and writing is all-or-nothing (see
 * [GenerateCodeFromTemplate]), so a specifier that could not have been meant is refused before any
 * of that runs, rather than silently ignored or generated twice.
 *
 * ## Example 1: catch a run given the same specifier twice
 * ```kt
 * import io.kotest.assertions.throwables.shouldThrow
 * import me.tbsten.katachi.processor.process
 * import me.tbsten.katachi.template.GenerateCodeFromTemplate
 * import me.tbsten.katachi.template.KatachiInvalidTemplateSpecifierException
 *
 * val thrown = shouldThrow<KatachiInvalidTemplateSpecifierException> {
 *     projectArchitecture.process(
 *         GenerateCodeFromTemplate,
 *         GenerateCodeFromTemplate.Args(template = listOf("UseCase", "UseCase")),
 *     ).getOrThrow()
 * }
 * thrown.problem shouldBe KatachiInvalidTemplateSpecifierException.Problem.Duplicate
 * ```
 *
 * @see GenerateCodeFromTemplate
 * @see DescribeTemplates
 */
public class KatachiInvalidTemplateSpecifierException internal constructor(
    /** What is wrong with [specifiers]. */
    public val problem: Problem,
    /** The specifiers `--arg template=` carried, exactly as split on `,`. */
    public val specifiers: List<String>,
) : KatachiDeclarationException(
    message = buildString {
        when (problem) {
            Problem.Empty -> {
                appendLine("--arg template= was given no specifier.")
                append("Pass at least one, such as --arg template=UseCase, or drop --arg template= " +
                    "entirely to list every template with katachiTemplates instead of generating.")
            }
            Problem.EmptyElement -> {
                appendLine("""--arg template=${specifiers.joinToString(",")} holds an empty element.""")
                append(
                    "A comma separates specifiers, so two commas in a row -- or one at either end " +
                        "-- leave an empty one between them. Remove it, or the extra comma.",
                )
            }
            Problem.Duplicate -> {
                appendLine(
                    """--arg template=${specifiers.joinToString(",")} names the same template more """ +
                        "than once.",
                )
                append(
                    "Whether spelled the same way twice or reached through two different " +
                        "specifiers, one template can be generated only once per run. Remove the " +
                        "repeat.",
                )
            }
        }
    },
) {
    /**
     * What is wrong with a set of specifiers.
     *
     * ## Example 1: tell an empty set from a set with an empty element
     * ```kt
     * when (thrown.problem) {
     *     KatachiInvalidTemplateSpecifierException.Problem.Empty -> "pass at least one"
     *     else -> "check the commas"
     * }
     * ```
     */
    public enum class Problem {
        /** `--arg template=` (or `Args(template = emptyList())`) named nothing at all. */
        Empty,

        /** Two commas in a row, or one at either end, left an empty specifier between them. */
        EmptyElement,

        /** The same template, reached by the same spelling twice or by two different ones. */
        Duplicate,
    }
}
