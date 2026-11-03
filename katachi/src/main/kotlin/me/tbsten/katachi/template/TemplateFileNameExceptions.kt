package me.tbsten.katachi.template

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.KatachiDeclarationException
import me.tbsten.katachi.KatachiInternalException
import me.tbsten.katachi.dsl.DeclarationSite

/**
 * A role's layout resolves a generated file to a path outside the project.
 *
 * Generated files are written into the repository itself, so a path that climbs out of the
 * project root is refused. Remove the `..` from the role's `layout { }`.
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
 *         "UseCase".role {
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
 * A template's generated path does not match its own `layout { }` pattern, which is a bug in katachi.
 *
 * Nothing in a definition or in the run's values can cause this. Please report it at
 * https://github.com/TBSten/katachi/issues.
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
    /**
     * The template that produced [path][KatachiTemplatePathMismatchException.path], its complete
     * `--arg template=` specifier.
     */
    public val template: String,
    /** The declared pattern the filled-in path was checked against. */
    public val pattern: String,
    /**
     * The path the run's values filled in, which
     * [pattern][KatachiTemplatePathMismatchException.pattern] does not match.
     */
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
 * A template below a module capture could not be found again for the module the run picked, which is a bug in katachi.
 *
 * Nothing is generated. Please report it at https://github.com/TBSten/katachi/issues.
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
