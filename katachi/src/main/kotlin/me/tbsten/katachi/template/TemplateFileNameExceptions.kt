package me.tbsten.katachi.template

import me.tbsten.katachi.KatachiDeclarationException
import me.tbsten.katachi.dsl.DeclarationSite

/**
 * A rendered file name holds a character katachi will not create a file with.
 *
 * The name is built from `--arg` values, so a value carrying a `*` or a `?` would reach the disk.
 * Such a file is matched by the very patterns that are supposed to describe it and cannot be named
 * back from a shell, and the Windows-illegal characters are refused everywhere so that a check
 * gives the same answer on every machine.
 *
 * ## Example 1: catch a parameter value that would make an unnameable file
 * ```kt
 * import io.kotest.assertions.throwables.shouldThrow
 * import me.tbsten.katachi.dsl.architecture
 * import me.tbsten.katachi.dsl.file
 * import me.tbsten.katachi.processor.process
 * import me.tbsten.katachi.template.GenerateCodeFromTemplate
 * import me.tbsten.katachi.template.KatachiUnsafeTemplateFileNameException
 *
 * val arch = architecture {
 *     "domain".group {
 *         "UseCase" {
 *             layout { "useCase" / "*UseCase.kt".file() }
 *             template {
 *                 val name by stringParameter(default = "*")
 *                 file("${name}UseCase.kt") { "// a use case" }
 *             }
 *         }
 *     }
 * }
 * val thrown = shouldThrow<KatachiUnsafeTemplateFileNameException> {
 *     arch.process(GenerateCodeFromTemplate, GenerateCodeFromTemplate.Args(roleName = "UseCase")).getOrThrow()
 * }
 * thrown.characters shouldBe listOf("*")
 * ```
 *
 * @see GenerateCodeFromTemplate
 */
public class KatachiUnsafeTemplateFileNameException internal constructor(
    /** The role whose template produced it, qualified. */
    public val role: String,
    /** The generated file name, exactly as it came out of the template. */
    public val fileName: String,
    /** The refused characters, each as a one-character string, sorted. */
    public val characters: List<String>,
    /** Where the `file(...)` that produced the name was written. */
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = buildString {
        appendLine(
            """Template file "$fileName" declared at $declaredAt, in role "$role", holds """ +
                """${characters.joinToString(" ") { "'$it'" }}.""",
        )
        appendLine(
            "katachi reads '*', '?', '[', ']', '{' and '}' as glob syntax, so a file named with " +
                "one is matched by the patterns that are meant to describe it; ':', '\"', '<', " +
                "'>' and '|' cannot be part of a file name on Windows, and a check has to give " +
                "the same answer on every machine.",
        )
        append(
            "The name is built from --arg values, so this is almost always a value that is not " +
                "what it was meant to be. Pass one made of the characters a file name may hold.",
        )
    },
)

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
 *             layout { ".." / "*UseCase.kt".file() }
 *             template { file("GetUserUseCase.kt") { "// a use case" } }
 *         }
 *     }
 * }
 * shouldThrow<KatachiTemplatePathOutsideProjectException> {
 *     arch.process(GenerateCodeFromTemplate, GenerateCodeFromTemplate.Args(roleName = "UseCase")).getOrThrow()
 * }
 * ```
 *
 * @see GenerateCodeFromTemplate
 */
public class KatachiTemplatePathOutsideProjectException internal constructor(
    /** The role whose layout resolved to it, qualified. */
    public val role: String,
    /** The refused path, as the layout spelled it. */
    public val path: String,
    /** Where the `file(...)` that produced the name was written. */
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
