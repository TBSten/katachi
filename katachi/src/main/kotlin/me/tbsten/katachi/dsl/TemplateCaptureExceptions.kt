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
 * import me.tbsten.katachi.dsl.template
 * import me.tbsten.katachi.processor.process
 * import me.tbsten.katachi.template.GenerateCodeFromTemplate
 *
 * val arch = architecture {
 *     "feature".group {
 *         "ViewModel" {
 *             layout {
 *                 "feature" / capture("feature") / "ViewModel.kt".file()
 *                     .template { captureValue("featur") }
 *             }
 *         }
 *     }
 * }
 * val thrown = shouldThrow<KatachiUnknownTemplateCaptureException> {
 *     arch.process(GenerateCodeFromTemplate, GenerateCodeFromTemplate.Args(template = listOf("feature.ViewModel"))).getOrThrow()
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
            "A capture is named in the role's layout { }, by capture(\"...\") or by a " +
                "\":...:${'$'}{capture(\"...\")}\".module { } key, and a name it does not declare " +
                "has no value on any run.",
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
 * `GenerateCodeFromTemplate`'s own `template` and `onExisting` do. Two inputs answering to one
 * `--arg` cannot be given different values, and which of them a value was meant for is not
 * something the command line can say.
 *
 * Found whenever the template is used -- generated, asked for the names it accepts, or explained
 * by `katachiTemplates --arg template=...` -- because `layout { }` and `.template { }` are both
 * replayed only then; the check (`assert()`) does not run templates. Every branch a preview can
 * reach is replayed, so a parameter declared only inside `if (withImpl) { }` is found on a run that
 * does not set `withImpl` as well. The list of templates does not throw it: such a template is
 * listed with `conflict: true`.
 *
 * ## Example 1: catch a capture and a parameter of the same name
 * ```kt
 * import io.kotest.assertions.throwables.shouldThrow
 * import me.tbsten.katachi.dsl.architecture
 * import me.tbsten.katachi.dsl.template
 * import me.tbsten.katachi.processor.process
 * import me.tbsten.katachi.template.GenerateCodeFromTemplate
 *
 * val arch = architecture {
 *     "feature".group {
 *         "ViewModel" {
 *             layout {
 *                 "feature" / capture("feature") / "ViewModel.kt".file()
 *                     .template {
 *                         val feature by stringParameter()
 *                         "package $feature"
 *                     }
 *             }
 *         }
 *     }
 * }
 * val thrown = shouldThrow<KatachiTemplateParameterConflictException> {
 *     arch.process(GenerateCodeFromTemplate, GenerateCodeFromTemplate.Args(template = listOf("feature.ViewModel"))).getOrThrow()
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
     * `GenerateCodeFromTemplate.Args.template` / `GenerateCodeFromTemplate.Args.onExisting`.
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

/**
 * Two templates chosen by the same run each declare a parameter of the same name, but with a
 * different type or a different default.
 *
 * A parameter's value arrives as `--arg <name>=...`, shared by every template of the run that
 * declares one under that name -- design draft section 2, "複数指定のとき": "capture とパラメータは、
 * 書いたテンプレート全部の中で名前ごとに1つ". Two declarations that only differ in type or default
 * cannot both be "the" parameter of that name: a `stringParameter()` and an `intParameter()` would
 * read the same `--arg` string two different ways, and two different defaults would leave one of
 * them silently unused whenever the run's `--arg` list leaves the value out.
 *
 * Found the same way [KatachiTemplateParameterConflictException] is: generated, asked for the
 * names it accepts, or explained by `katachiTemplates --arg template=...`, since `.template { }`
 * is only replayed then. Every branch a preview can reach is replayed, so a parameter declared
 * only inside `if (withImpl) { }` is compared on a run that does not set `withImpl` too.
 *
 * ## Example 1: two templates disagree on a shared parameter's type
 * ```kt
 * import io.kotest.assertions.throwables.shouldThrow
 * import me.tbsten.katachi.dsl.architecture
 * import me.tbsten.katachi.dsl.template
 * import me.tbsten.katachi.processor.process
 * import me.tbsten.katachi.template.GenerateCodeFromTemplate
 *
 * val arch = architecture {
 *     "a".group {
 *         "A" { layout { "a" / "A.kt".file()
 *             .template { val name by stringParameter(); "// $name" } } }
 *     }
 *     "b".group {
 *         "B" { layout { "b" / "B.kt".file()
 *             .template { val name by intParameter(); "// $name" } } }
 *     }
 * }
 * val thrown = shouldThrow<KatachiTemplateParameterTypeConflictException> {
 *     arch.process(GenerateCodeFromTemplate, GenerateCodeFromTemplate.Args(template = listOf("a.A", "b.B"))).getOrThrow()
 * }
 * thrown.name shouldBe "name"
 * ```
 */
public class KatachiTemplateParameterTypeConflictException internal constructor(
    /** The name both parameters answer to. */
    public val name: String,
    /** The complete specifier (`--arg template=`) of the template this parameter was found on. */
    public val template: String,
    /** Where this parameter was declared. */
    public val declaredAt: DeclarationSite,
    /** This parameter's type: `String`, `Boolean`, `Int`, or the enum's simple name. */
    public val label: String,
    /** This parameter's default, spelled as `--arg` would take it, or `null` for none. */
    public val default: String?,
    /** The complete specifier of the template the first, conflicting declaration was found on. */
    public val conflictsWithTemplate: String,
    /** Where the first, conflicting declaration was declared. */
    public val conflictsWithDeclaredAt: DeclarationSite,
    /** The first, conflicting declaration's type. */
    public val conflictsWithLabel: String,
    /** The first, conflicting declaration's default, or `null` for none. */
    public val conflictsWithDefault: String?,
) : KatachiDeclarationException(
    message = buildString {
        appendLine(
            """Parameter "$name" of template "$template" declared at $declaredAt """ +
                "(${label.asParameterSignature(default)}) conflicts with template " +
                """"$conflictsWithTemplate"'s parameter of the same name, declared at """ +
                "$conflictsWithDeclaredAt (${conflictsWithLabel.asParameterSignature(conflictsWithDefault)}).",
        )
        appendLine(
            "Both would receive their value as --arg $name=..., so a run could not give them the " +
                "type and default the other one expects.",
        )
        append("Give both declarations the same type and the same default, or rename one of them.")
    },
)

/** `label` with its default, the way a message reads a parameter's full signature: `String (default = "x")`. */
private fun String.asParameterSignature(default: String?): String = default?.let { "$this (default = $it)" } ?: this
