package me.tbsten.katachi.template

import me.tbsten.katachi.KatachiDeclarationException
import me.tbsten.katachi.dsl.DeclarationSite

/**
 * A template produced a file no pattern of its role's `layout { }` accepts.
 *
 * Writing it anyway would put a file where the role says none may live, so the very next
 * `assert()` would report it as `[UnexpectedFile]` -- a generator whose output fails the check
 * that generated it. The layout is the only thing that knows where a role's files go, so a name
 * it does not accept has nowhere to go.
 *
 * ## Example 1: catch a generated name the layout does not accept
 * ```kt
 * import io.kotest.assertions.throwables.shouldThrow
 * import me.tbsten.katachi.dsl.architecture
 * import me.tbsten.katachi.dsl.file
 * import me.tbsten.katachi.processor.process
 * import me.tbsten.katachi.template.GenerateCodeFromTemplate
 * import me.tbsten.katachi.template.KatachiNoTemplatePlacementException
 *
 * val arch = architecture {
 *     "domain".group {
 *         "UseCase" {
 *             layout { "useCase" / "*UseCase.kt".file() }
 *             template { file("GetUser.kt") { "// not a use case" } }
 *         }
 *     }
 * }
 * val thrown = shouldThrow<KatachiNoTemplatePlacementException> {
 *     arch.process(GenerateCodeFromTemplate, GenerateCodeFromTemplate.Args(roleName = "UseCase")).getOrThrow()
 * }
 * thrown.fileName shouldBe "GetUser.kt"
 * ```
 *
 * @see GenerateCodeFromTemplate
 */
public class KatachiNoTemplatePlacementException internal constructor(
    /** The role whose template produced it, qualified. */
    public val role: String,
    /** The generated file name, after the template's parameters were filled in. */
    public val fileName: String,
    /** Every file pattern that role's `layout { }` declares, sorted. */
    public val declaredPatterns: List<String>,
    /** Where the `file(...)` that produced the name was written. */
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = buildString {
        appendLine(
            """Template file "$fileName" declared at $declaredAt has no place in role "$role".""",
        )
        appendLine(
            "A template names files and that role's layout { } names directories, so a name no " +
                "pattern of the layout accepts would be written somewhere the role says nothing " +
                "may live -- and the next check would report it as an unexpected file.",
        )
        if (declaredPatterns.isEmpty()) {
            append(
                """That role declares no file pattern at all. Add one to its layout { }, such """ +
                    """as `"useCase" / "*UseCase.kt".file()`.""",
            )
        } else {
            appendLine("That role declares these file patterns:")
            for (pattern in declaredPatterns) appendLine("  $pattern")
            append(
                "Rename what file(...) produces so that one of them matches it, or declare the " +
                    "pattern the template needs.",
            )
        }
    },
)

/**
 * The only patterns accepting a generated file name sit below a wildcard directory.
 *
 * `":feature:*".module { }` declares where a *kind* of module keeps this role. Which modules exist
 * is a question only a walk of the project answers, so the flattened entry keeps the `*` standing
 * where a module name would be -- and katachi will not pick one of them to generate into.
 *
 * ## Example 1: catch a role whose only place is a wildcard module
 * ```kt
 * import io.kotest.assertions.throwables.shouldThrow
 * import me.tbsten.katachi.dsl.architecture
 * import me.tbsten.katachi.dsl.file
 * import me.tbsten.katachi.dsl.gradle.div
 * import me.tbsten.katachi.dsl.gradle.module
 * import me.tbsten.katachi.processor.process
 * import me.tbsten.katachi.template.GenerateCodeFromTemplate
 * import me.tbsten.katachi.template.KatachiWildcardTemplatePlacementException
 *
 * val arch = architecture {
 *     "feature".group {
 *         "Screen" {
 *             layout { ":feature:*".module { "*Screen.kt".file() } }
 *             template { file("HomeScreen.kt") { "// a screen" } }
 *         }
 *     }
 * }
 * shouldThrow<KatachiWildcardTemplatePlacementException> {
 *     arch.process(GenerateCodeFromTemplate, GenerateCodeFromTemplate.Args(roleName = "Screen")).getOrThrow()
 * }
 * ```
 *
 * @see GenerateCodeFromTemplate
 */
public class KatachiWildcardTemplatePlacementException internal constructor(
    /** The role whose template produced it, qualified. */
    public val role: String,
    /** The generated file name, after the template's parameters were filled in. */
    public val fileName: String,
    /** The patterns that accept the name but name no single directory, sorted. */
    public val patterns: List<String>,
    /** Where the `file(...)` that produced the name was written. */
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = buildString {
        appendLine(
            """Template file "$fileName" declared at $declaredAt has no single directory in """ +
                """role "$role".""",
        )
        appendLine("Every pattern of that role accepting this name still holds a wildcard:")
        for (pattern in patterns) appendLine("  $pattern")
        appendLine(
            "A wildcard there stands for the modules the project happens to have, which is not " +
                "something a declaration says -- so katachi cannot choose one of them to write " +
                "into without inventing the answer.",
        )
        append(
            "Declare the template on a role whose layout { } names one module, or name that " +
                "module in a layout { } block of its own.",
        )
    },
)

/**
 * A generated file name fits two different directories of one role.
 *
 * A role may live in several places, and both of them accepting the name is a real thing to
 * declare. What it is not is an instruction: picking the first would make where a file lands
 * depend on the order two lines happen to be in.
 *
 * ## Example 1: catch a name two places of one role accept
 * ```kt
 * import io.kotest.assertions.throwables.shouldThrow
 * import me.tbsten.katachi.dsl.architecture
 * import me.tbsten.katachi.dsl.file
 * import me.tbsten.katachi.processor.process
 * import me.tbsten.katachi.template.GenerateCodeFromTemplate
 * import me.tbsten.katachi.template.KatachiAmbiguousTemplatePlacementException
 *
 * val arch = architecture {
 *     "domain".group {
 *         "UseCase" {
 *             layout { "api" / "*UseCase.kt".file() }
 *             layout { "impl" / "*UseCase.kt".file() }
 *             template { file("GetUserUseCase.kt") { "// a use case" } }
 *         }
 *     }
 * }
 * val thrown = shouldThrow<KatachiAmbiguousTemplatePlacementException> {
 *     arch.process(GenerateCodeFromTemplate, GenerateCodeFromTemplate.Args(roleName = "UseCase")).getOrThrow()
 * }
 * thrown.candidates.size shouldBe 2
 * ```
 *
 * @see GenerateCodeFromTemplate
 */
public class KatachiAmbiguousTemplatePlacementException internal constructor(
    /** The role whose template produced it, qualified. */
    public val role: String,
    /** The generated file name, after the template's parameters were filled in. */
    public val fileName: String,
    /** Every path the file could have been written to, sorted. */
    public val candidates: List<String>,
    /** Where the `file(...)` that produced the name was written. */
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = buildString {
        appendLine(
            """Template file "$fileName" declared at $declaredAt fits """ +
                """${candidates.size} places of role "$role".""",
        )
        for (candidate in candidates) appendLine("  $candidate")
        appendLine(
            "Choosing one of them would make where this file lands depend on the order the " +
                "layout { } blocks happen to be written in, which is not something the " +
                "definition says.",
        )
        append(
            "Give the places file patterns that tell them apart, or split the role into one " +
                "role per place.",
        )
    },
)
