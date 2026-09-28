package me.tbsten.katachi.template

import me.tbsten.katachi.KatachiCheckException
import me.tbsten.katachi.KatachiDeclarationException
import me.tbsten.katachi.dsl.DeclarationSite

/**
 * The only places accepting a generated file name are named wildcards the run gave no value.
 *
 * `capture("feature")` in a role's `layout { }` stands for a directory that template generation
 * fills in from `--arg feature=...`. Without the value there is no directory, and choosing one
 * would be inventing the answer.
 *
 * ## Example 1: catch a run that did not say which feature to generate into
 * ```kt
 * import io.kotest.assertions.throwables.shouldThrow
 * import me.tbsten.katachi.dsl.architecture
 * import me.tbsten.katachi.dsl.file
 * import me.tbsten.katachi.processor.process
 * import me.tbsten.katachi.template.GenerateCodeFromTemplate
 * import me.tbsten.katachi.template.KatachiMissingTemplateCaptureException
 *
 * val arch = architecture {
 *     "feature".group {
 *         "ViewModel" {
 *             layout { "feature" / capture("feature") / "*ViewModel.kt".file() }
 *             template { file("HomeViewModel.kt") { "class HomeViewModel" } }
 *         }
 *     }
 * }
 * val thrown = shouldThrow<KatachiMissingTemplateCaptureException> {
 *     arch.process(GenerateCodeFromTemplate, GenerateCodeFromTemplate.Args(roleName = "ViewModel")).getOrThrow()
 * }
 * thrown.missing.values.flatten() shouldBe listOf("feature")
 * ```
 *
 * @see GenerateCodeFromTemplate
 */
public class KatachiMissingTemplateCaptureException internal constructor(
    /** The role whose template produced the file, qualified. */
    public val role: String,
    /** The generated file name, after the template's parameters were filled in. */
    public val fileName: String,
    /** Each layout pattern accepting the name, and the capture names it still needs a value for. */
    public val missing: Map<String, List<String>>,
    /** Where the `file(...)` that produced the name was written. */
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = buildString {
        val names = missing.values.flatten().distinct().sorted()
        appendLine(
            """Template file "$fileName" declared at $declaredAt needs a value for """ +
                """${names.joinToString(", ")} to find its directory in role "$role".""",
        )
        appendLine("These patterns accept the name, and each names a directory by capture:")
        for ((pattern, needed) in missing) appendLine("  $pattern (needs ${needed.joinToString(", ")})")
        appendLine(
            "A capture stands for a directory the run chooses, so without its value the file " +
                "has nowhere to go.",
        )
        append("Pass ${names.joinToString(" ") { "--arg $it=<value>" }}.")
    },
)

/**
 * A capture was given a value that cannot be one directory level.
 *
 * A capture is one level of a path, exactly like the `*` it replaces. A value that is empty,
 * that climbs (`.` or `..`), that holds a separator, or that holds a character no file name may
 * hold would make generation write somewhere the layout does not describe -- or outside the
 * project.
 *
 * ## Example 1: catch a value that spans two levels
 * ```kt
 * import io.kotest.assertions.throwables.shouldThrow
 * import me.tbsten.katachi.template.KatachiInvalidTemplateCaptureValueException
 *
 * // ./gradlew katachiTemplate --arg roleName=ViewModel --arg feature=home/list
 * val thrown = shouldThrow<KatachiInvalidTemplateCaptureValueException> { generate() }
 * thrown.problem shouldBe KatachiInvalidTemplateCaptureValueException.Problem.Separator
 * ```
 *
 * @see GenerateCodeFromTemplate
 */
public class KatachiInvalidTemplateCaptureValueException internal constructor(
    /** The role whose layout names the capture, qualified. */
    public val role: String,
    /** The capture's name, which is also its `--arg` name. */
    public val name: String,
    /** The value the run passed. */
    public val value: String,
    /** What is wrong with [value]. */
    public val problem: Problem,
    /** Where a layout path naming the capture was declared. */
    public val captureDeclaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = buildString {
        appendLine(
            """Capture "$name" of role "$role" declared at $captureDeclaredAt was given "$value", """ +
                "which is not one directory level: ${problem.description}.",
        )
        appendLine(
            "A capture stands for exactly one level of the path, as the * it names does; " +
                "several levels cannot be given as one value.",
        )
        append("Pass a single directory name, such as --arg $name=home.")
    },
) {
    /**
     * Why a value cannot be one directory level.
     *
     * ## Example 1: tell an empty value from one with a separator
     * ```kt
     * when (thrown.problem) {
     *     KatachiInvalidTemplateCaptureValueException.Problem.Empty -> "pass a value"
     *     else -> "pass one level"
     * }
     * ```
     */
    public enum class Problem(internal val description: String) {
        /** The value is empty. */
        Empty("it is empty"),

        /** The value is `.` or `..`, which name the directory itself or its parent. */
        DotSegment("`.` and `..` name an existing directory, not a new level"),

        /** The value holds `/` or `\`. */
        Separator("it holds a path separator"),

        /** The value holds a character no file name may hold, such as `*`, `:` or a control character. */
        UncreatableCharacter("it holds a character a directory name cannot hold"),
    }
}

/**
 * A module capture was given a value that names no existing module.
 *
 * A wildcard module key, `":feature:*".module(capture = "feature") { }`, stands for the modules
 * that exist. Generating into one that does not would put files in a directory no module owns,
 * and the next `assert()` would report it as `[UnexpectedDirectory]`. Refused even when another
 * place of the role would take the file: the value was passed on purpose, and generating
 * somewhere else would drop it without a word.
 *
 * A [KatachiCheckException], like [KatachiExistingTemplateFileException]: the definition is right
 * and so is the shape of the value; what is wrong is that the project does not hold the module
 * (yet), or that the value names another than intended.
 *
 * ## Example 1: catch a misspelt module
 * ```kt
 * import io.kotest.assertions.throwables.shouldThrow
 * import me.tbsten.katachi.template.KatachiTemplateModuleNotFoundException
 *
 * // ./gradlew katachiTemplate --arg roleName=Screen --arg feature=hoem --arg name=Home
 * val thrown = shouldThrow<KatachiTemplateModuleNotFoundException> { generate() }
 * thrown.existingArgs shouldBe listOf("--arg feature=home", "--arg feature=settings")
 * ```
 *
 * @see GenerateCodeFromTemplate
 */
public class KatachiTemplateModuleNotFoundException internal constructor(
    /** The role whose template produced the file, qualified. */
    public val role: String,
    /** The module key as written in the layout, `":feature:*"`. */
    public val modulePattern: String,
    /** The names the key gives its `*`s, in order, which are also their `--arg` names. */
    public val captureNames: List<String>,
    /** The key with the run's values put in, `":feature:hoem"`. */
    public val modulePath: String,
    /** Every existing module [modulePattern] matches. */
    public val existing: List<String>,
    /**
     * For each of [existing], in the same order, the `--arg`s that pick it:
     * `"--arg feature=home"`, or `"--arg feature=home --arg layer=data"` for a key of two names.
     */
    public val existingArgs: List<String>,
    /** Where the `file(...)` being placed was written. */
    public val declaredAt: DeclarationSite,
) : KatachiCheckException(
    message = buildString {
        appendLine(
            """Template file declared at $declaredAt would go into module $modulePath of role "$role", """ +
                "which does not exist.",
        )
        appendLine(
            "The module key $modulePattern stands for the modules that exist, so a file written into " +
                "one that does not would be reported as [UnexpectedDirectory] by the next check.",
        )
        val names = captureNames.joinToString(" ") { "--arg $it=..." }
        if (existing.isEmpty()) {
            append(
                "No module matches $modulePattern yet. Create the module first (settings.gradle.kts), " +
                    "then pass its name as $names.",
            )
        } else {
            appendLine("Modules matching $modulePattern, and the value that picks each ($names):")
            val width = existing.maxOf { it.length }
            existing.forEachIndexed { index, module ->
                appendLine("  ${module.padEnd(width)}  ${existingArgs.getOrElse(index) { "" }}".trimEnd())
            }
            append(
                "Pass the value, not the module path -- `home`, not `:feature:home` -- or create the " +
                    "module first (settings.gradle.kts).",
            )
        }
    },
)
