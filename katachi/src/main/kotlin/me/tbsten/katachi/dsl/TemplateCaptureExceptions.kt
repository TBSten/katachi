package me.tbsten.katachi.dsl

import me.tbsten.katachi.KatachiDeclarationException

/**
 * A template read a capture its role's `layout { }` does not name.
 *
 * `captureValue(name)` reads the value of a named wildcard, and only the layout names them. A
 * name it does not know would read nothing on every run, so it is refused where it is written
 * instead of quietly standing in for a value.
 *
 * ## Example 1: catch a capture the layout never named
 * ```kt
 * import io.kotest.assertions.throwables.shouldThrow
 * import me.tbsten.katachi.dsl.architecture
 * import me.tbsten.katachi.dsl.file
 * import me.tbsten.katachi.processor.process
 * import me.tbsten.katachi.template.GenerateCodeFromTemplate
 *
 * val arch = architecture {
 *     "feature".group {
 *         "ViewModel" {
 *             layout { "feature" / capture("feature") / "*ViewModel.kt".file() }
 *             template { file("${captureValue("featur")}ViewModel.kt") { "" } }
 *         }
 *     }
 * }
 * val thrown = shouldThrow<KatachiUnknownTemplateCaptureException> {
 *     arch.process(GenerateCodeFromTemplate, GenerateCodeFromTemplate.Args(roleName = "ViewModel")).getOrThrow()
 * }
 * thrown.knownNames shouldBe listOf("feature")
 * ```
 *
 * @see TemplateScope.captureValue
 */
public class KatachiUnknownTemplateCaptureException internal constructor(
    /** The role whose template read it, qualified. */
    public val role: String,
    /** The name `captureValue(...)` was given. */
    public val name: String,
    /** Every capture name the role's `layout { }` declares, sorted. Empty when it declares none. */
    public val knownNames: List<String>,
    /** Where `captureValue(...)` was called. */
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = buildString {
        appendLine(
            """captureValue("$name") at $declaredAt reads a capture role "$role" does not declare.""",
        )
        appendLine(
            "A capture is named in the role's layout { }, by capture(\"...\") or " +
                ".module(capture = \"...\"), and a name it does not declare has no value on any run.",
        )
        if (knownNames.isEmpty()) {
            append(
                "That role names no wildcard. Name the one this value comes from in its layout { }, " +
                    "such as `\"feature\" / capture(\"$name\")`, or use a stringParameter() instead.",
            )
        } else {
            append("That role declares: ${knownNames.joinToString(", ")}. Read one of them.")
        }
    },
)

/**
 * A capture name of a role is also the name of another input of its template.
 *
 * A capture's value arrives as `--arg <name>=...`, exactly as a template parameter's does and as
 * `GenerateCodeFromTemplate`'s own `roleName` and `onExisting` do. Two inputs answering to one
 * `--arg` cannot be given different values, and which of them a value was meant for is not
 * something the command line can say.
 *
 * Found whenever the template is used -- generated, asked for the names it accepts, or explained
 * by `katachiTemplates --arg roleName=...` -- because `layout { }` and `template { }` are both
 * replayed only then; the check (`assert()`) does not run templates. Every branch a preview can
 * reach is replayed, so a parameter declared only inside `if (withImpl) { }` is found on a run that
 * does not set `withImpl` as well. The list of templates does not throw it: such a template is
 * listed without a file count.
 *
 * ## Example 1: catch a capture and a parameter of the same name
 * ```kt
 * import io.kotest.assertions.throwables.shouldThrow
 * import me.tbsten.katachi.dsl.architecture
 * import me.tbsten.katachi.dsl.file
 * import me.tbsten.katachi.processor.process
 * import me.tbsten.katachi.template.GenerateCodeFromTemplate
 *
 * val arch = architecture {
 *     "feature".group {
 *         "ViewModel" {
 *             layout { "feature" / capture("feature") / "*ViewModel.kt".file() }
 *             template {
 *                 val feature by stringParameter()
 *                 file("${feature}ViewModel.kt") { "" }
 *             }
 *         }
 *     }
 * }
 * val thrown = shouldThrow<KatachiTemplateParameterConflictException> {
 *     arch.process(GenerateCodeFromTemplate, GenerateCodeFromTemplate.Args(roleName = "ViewModel")).getOrThrow()
 * }
 * thrown.name shouldBe "feature"
 * ```
 *
 * @see TemplateScope.captureValue
 */
public class KatachiTemplateParameterConflictException internal constructor(
    /** The role whose layout and template disagree, qualified. */
    public val role: String,
    /** The name both of them answer to. */
    public val name: String,
    /**
     * What else answers to [name]: `stringParameter()` and its siblings, or
     * `GenerateCodeFromTemplate.Args.roleName` / `GenerateCodeFromTemplate.Args.onExisting`.
     */
    public val conflictsWith: String,
    /** Where a layout path naming the capture was declared. */
    public val captureDeclaredAt: DeclarationSite,
    /** Where the conflicting parameter was declared, or `null` for an argument of the processor itself. */
    public val parameterDeclaredAt: DeclarationSite?,
) : KatachiDeclarationException(
    message = buildString {
        append("""Capture "$name" of role "$role" declared at $captureDeclaredAt is also the name of """)
        append(conflictsWith)
        parameterDeclaredAt?.let { append(" at ").append(it) }
        appendLine(".")
        appendLine(
            "Both would receive their value as --arg $name=..., so one run could not give them " +
                "different values, and nothing would say which of them a value was meant for.",
        )
        if (parameterDeclaredAt == null) {
            append(
                "Rename the capture: $name is an argument of the template processor itself, and " +
                    "keeps its name.",
            )
        } else {
            append(
                "Rename the capture or the parameter. To use the capture's value inside the template, " +
                    "read it with captureValue(\"$name\") instead of declaring a parameter for it.",
            )
        }
    },
)
