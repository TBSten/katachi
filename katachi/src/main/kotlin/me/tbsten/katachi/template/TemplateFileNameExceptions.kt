package me.tbsten.katachi.template

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.KatachiDeclarationException
import me.tbsten.katachi.KatachiInternalException
import me.tbsten.katachi.dsl.DeclarationSite

/**
 * A role's layout resolves a generated file to a path outside the project.
 *
 * Every other thing katachi writes lands below `build/`; these files land in the user's own source
 * tree, so the one path that must never be reachable is checked rather than assumed.
 *
 * ## Example 1: catch a layout that climbs out of the project root
 * ```kt
 * import io.kotest.assertions.throwables.shouldThrow
 * import me.tbsten.katachi.dsl.architecture
 * import me.tbsten.katachi.dsl.file
 * import me.tbsten.katachi.processor.process
 * import me.tbsten.katachi.template.GenerateCodeFromTemplate
 * import me.tbsten.katachi.template.KatachiTemplatePathOutsideProjectException
 *
 * val arch = architecture {
 *     "domain".group {
 *         "UseCase" {
 *             layout { ".." / "GetUserUseCase.kt".file()
 *                 .template { "// a use case" } }
 *         }
 *     }
 * }
 * shouldThrow<KatachiTemplatePathOutsideProjectException> {
 *     arch.process(GenerateCodeFromTemplate, GenerateCodeFromTemplate.Args(template = listOf("UseCase"))).getOrThrow()
 * }
 * ```
 *
 * @see GenerateCodeFromTemplate
 */
@ExperimentalKatachiApi
public class KatachiTemplatePathOutsideProjectException internal constructor(
    /** The role whose layout resolved to it, qualified. */
    public val role: String,
    /** The refused path, as the layout spelled it. */
    public val path: String,
    /** Where the `.template { }` declaration was written. */
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = buildString {
        appendLine(
            """Template file declared at $declaredAt, in role "$role", would be written to """ +
                """"$path", which leaves the project root.""",
        )
        appendLine(
            "Generated files go into the repository itself rather than below build/, so a path " +
                "climbing out of it would write somewhere no check ever looks.",
        )
        append("""Remove the ".." from that role's layout { }, or root the path inside the project.""")
    },
)

/**
 * A run's values, once filled into a template's declared path, produced a path that template's
 * own `layout { }` pattern does not match.
 *
 * The path this template generates is worked out from the same declaration its `layout { }` and
 * its `.template { }` share, so the two cannot disagree once every value has passed capture
 * validation -- reaching this means the value checks upstream of it missed something.
 *
 * ## Example 1: report it rather than treating it as a failed run
 * ```kt
 * try {
 *     projectArchitecture.process(
 *         GenerateCodeFromTemplate,
 *         GenerateCodeFromTemplate.Args(template = listOf("data.Repository.repository")),
 *     ).getOrThrow()
 * } catch (bug: KatachiTemplatePathMismatchException) {
 *     println("Please report this at https://github.com/TBSten/katachi/issues: ${bug.path}")
 * }
 * ```
 *
 * @see GenerateCodeFromTemplate
 */
@ExperimentalKatachiApi
public class KatachiTemplatePathMismatchException internal constructor(
    /** The template that produced [path], its complete `--arg template=` specifier. */
    public val template: String,
    /** The declared pattern the filled-in path was checked against. */
    public val pattern: String,
    /** The path the run's values filled in, which [pattern] does not match. */
    public val path: String,
) : KatachiInternalException(
    message = """
        Template "$template" filled its declared path $pattern in as "$path", which the pattern
        itself does not match. The path a template generates is worked out from the same
        declaration its layout { } and its .template { } share, so this cannot happen once every
        value has passed capture validation -- this is a bug in katachi. Please report it at
        https://github.com/TBSten/katachi/issues.
    """.trimIndent(),
)

/**
 * A template whose path runs through a module capture found no declaration of itself once the
 * layout was flattened again against the module the run's values picked.
 *
 * Re-flattening the same `layout { }` against that module reaches the very `.template { }` call
 * the template was listed from, so this cannot happen once the module has been found -- reaching
 * this means katachi lost track of which declaration the template is. Refused rather than
 * generated from the un-resolved declaration, which still holds `<name>` in place of what
 * `wildcard(name)` reads and would write that text into the path.
 *
 * ## Example 1: report it rather than treating it as a failed run
 * ```kt
 * try {
 *     projectArchitecture.process(
 *         GenerateCodeFromTemplate,
 *         GenerateCodeFromTemplate.Args(template = listOf("feature.FeatureComponent")),
 *     ).getOrThrow()
 * } catch (bug: KatachiTemplateEntryNotResolvedException) {
 *     println("Please report this at https://github.com/TBSten/katachi/issues: ${bug.template}")
 * }
 * ```
 *
 * @see GenerateCodeFromTemplate
 */
@ExperimentalKatachiApi
public class KatachiTemplateEntryNotResolvedException internal constructor(
    /** The template being generated, its complete `--arg template=` specifier. */
    public val template: String,
    /** The module captures' values the run passed, by capture name. */
    public val values: Map<String, String>,
    /** Where the `.template { }` declaration was written. */
    public val declaredAt: DeclarationSite,
) : KatachiInternalException(
    message = """
        Template "$template", declared at $declaredAt, was not found again after binding its module captures to
        ${values.entries.joinToString { (name, value) -> "$name=$value" }}. The module was found, and
        flattening the layout against it reaches the same .template { } call the template was
        listed from, so this is a bug in katachi. Please report it at
        https://github.com/TBSten/katachi/issues.
    """.trimIndent(),
)
