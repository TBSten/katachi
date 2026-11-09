package me.tbsten.katachi.template

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.KatachiCheckException
import me.tbsten.katachi.KatachiDeclarationException
import me.tbsten.katachi.dsl.DeclarationSite

/**
 * A run gave no value for a capture its role's template needs.
 *
 * `capture("feature")` in a `layout { }` stands for a directory that the run names with
 * `--arg feature=...`. katachi does not pick one when the value is
 * missing, even if another place of the role names no capture.
 *
 * ## Example 1: catch a run that did not say which feature to generate into
 * ```kt
 * import io.kotest.assertions.throwables.shouldThrow
 * import me.tbsten.katachi.dsl.architecture
 * import me.tbsten.katachi.dsl.template
 * import me.tbsten.katachi.processor.process
 * import me.tbsten.katachi.template.GenerateCodeFromTemplate
 * import me.tbsten.katachi.template.KatachiMissingTemplateCaptureException
 *
 * val arch = architecture {
 *     "feature".group {
 *         "ViewModel".role {
 *             layout {
 *                 "feature" / capture("feature") / "ViewModel.kt".file()
 *                     .template { "class ViewModel" }
 *             }
 *         }
 *     }
 * }
 * val thrown = shouldThrow<KatachiMissingTemplateCaptureException> {
 *     arch.process(GenerateCodeFromTemplate, GenerateCodeFromTemplate.Args(template = listOf("feature.ViewModel"))).getOrThrow()
 * }
 * thrown.names shouldBe listOf("feature")
 * ```
 *
 * @see GenerateCodeFromTemplate
 */
@ExperimentalKatachiApi
public class KatachiMissingTemplateCaptureException internal constructor(
    /** The role whose template needs the values, qualified. */
    public val role: String,
    /** The generated file name that could not be placed, or `null` when `captureValue(...)` read the capture. */
    public val fileName: String?,
    /** The capture names without a value, in declaration order. */
    public val names: List<String>,
    /** Each layout pattern that names a missing capture, written with `<name>` for each capture, to the names it holds. */
    public val missing: Map<String, List<String>>,
    /**
     * For each of [names][KatachiMissingTemplateCaptureException.names], where the layout declares
     * it.
     */
    public val captureDeclaredAt: Map<String, DeclarationSite>,
    /** For each missing capture, the values that pick an existing directory or module, sorted; empty when none could be listed. */
    public val existingValues: Map<String, List<String>>,
    internal val declaredWith: Map<String, String>,
    /**
     * Where the file declaration that could not be placed, or the `.template { }` that read the
     * capture, was written.
     */
    public val declaredAt: DeclarationSite,
    cause: Throwable? = null,
) : KatachiDeclarationException(
    message = buildString {
        appendLine(
            """The run gave no value for capture ${names.joinToString(", ")}, which the template of """ +
                """role "$role" needs at $declaredAt.""",
        )
        appendLine("A capture is a level of the path the layout names, and each run chooses it with --arg:")
        for (name in names) {
            val pattern = missing.entries.firstOrNull { name in it.value }?.key
            append("  $name: ${declaredWith[name] ?: "capture(\"$name\")"}")
            captureDeclaredAt[name]?.let { append(" declared at $it") }
            pattern?.let { append(", in $it") }
            appendLine()
        }
        appendLine(
            "Without the value there is no directory to write into, and katachi does not pick one " +
                "-- not even another place of the role that names no capture.",
        )
        val existing = names.filter { existingValues[it].orEmpty().isNotEmpty() }
        if (existing.isNotEmpty()) {
            appendLine("Values that exist now:")
            for (name in existing) appendLine("  $name: ${existingValues.getValue(name).joinToString(", ")}")
        }
        append("Pass ")
        append(names.joinToString(" ") { "--arg $it=<$it>" })
        val example = names.mapNotNull { name -> existingValues[name]?.firstOrNull()?.let { "--arg $name=$it" } }
        if (example.size == names.size) append(", such as ${example.joinToString(" ")}")
        append(".")
    },
    cause = cause,
)

/**
 * A capture was given a value that cannot be one directory level.
 *
 * A capture is one level of a path, like the `*` it replaces. An empty value, `.` or `..`, a
 * separator, or a character no file name may hold is refused. So is what Windows refuses or
 * silently changes (a trailing `.` or space, a device name such as `CON`), on every platform.
 *
 * ## Example 1: catch a value that spans two levels
 * ```kt
 * import io.kotest.assertions.throwables.shouldThrow
 * import me.tbsten.katachi.template.KatachiInvalidTemplateCaptureValueException
 *
 * // ./gradlew katachiTemplate --arg template=feature.ViewModel --arg feature=home/list
 * val thrown = shouldThrow<KatachiInvalidTemplateCaptureValueException> { generate() }
 * thrown.problem shouldBe KatachiInvalidTemplateCaptureValueException.Problem.Separator
 * ```
 *
 * @see GenerateCodeFromTemplate
 */
@ExperimentalKatachiApi
public class KatachiInvalidTemplateCaptureValueException internal constructor(
    /** The role whose layout names the capture, qualified. */
    public val role: String,
    /** The capture's name, which is also its `--arg` name. */
    public val name: String,
    /** The value the run passed. */
    public val value: String,
    /**
     * What is wrong with the value, or with [segment][KatachiInvalidTemplateCaptureValueException.segment] when it is not `null`.
     */
    public val problem: Problem,
    /**
     * The path segment the value landed in, with captures filled in; `null` when the problem is with the value alone.
     *
     * For example `"a."` for `"${capture("x")}."` given `--arg x=a`.
     */
    public val segment: String? = null,
    /** Where a layout path naming the capture was declared. */
    public val captureDeclaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = buildString {
        if (segment == null) {
            appendLine(
                """Capture "$name" of role "$role" declared at $captureDeclaredAt was given "$value", """ +
                    "which is not one directory level: ${problem.description}.",
            )
        } else {
            appendLine(
                """Capture "$name" of role "$role" declared at $captureDeclaredAt was given "$value", """ +
                    """which fills its path segment in as "$segment": ${problem.description}.""",
            )
        }
        val lastLevel = value.split(':', '/', '\\').lastOrNull { it.isNotBlank() }
        when {
            problem == Problem.Separator -> {
                appendLine(
                    "A capture stands for exactly one level of the path, as the * it names does; " +
                        "several levels cannot be given as one value.",
                )
                append("Pass a single directory name, such as --arg $name=${lastLevel ?: "home"}.")
            }
            segment != null -> append(
                "A capture may fill only part of a segment -- \"\${capture(\"$name\")}Screen.kt\", say -- " +
                    "so the value alone can be fine and the segment it lands in still not: " + problem.fix(name),
            )
            else -> append(problem.fix(name))
        }
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

        /** The value holds nothing but whitespace. */
        Blank("it holds nothing but whitespace"),

        /** The value is `.` or `..`, which name the directory itself or its parent. */
        DotSegment("`.` and `..` name an existing directory, not a new level"),

        /** The value holds `/` or `\`. */
        Separator("it holds a path separator"),

        /**
         * The value holds a character no file name may hold.
         *
         * `*`, `:` and the rest Windows refuses, a control character (DEL included), or a line separator
         * such as U+2028.
         */
        UncreatableCharacter("it holds a character a directory name cannot hold"),

        /** The value starts or ends with whitespace, which a directory name would keep but no one could see. */
        SurroundingWhitespace("it starts or ends with whitespace"),

        /** The value ends with `.`, which Windows drops from a directory name without a word. */
        TrailingDot("it ends with `.`, which Windows drops from a directory name"),

        /** The value is a device name Windows reserves, such as `CON` or `nul.txt`. */
        ReservedName("Windows reserves it as a device name (CON, PRN, AUX, NUL, COM1-9, LPT1-9)"),
        ;

        internal fun fix(name: String): String = when (this) {
            Empty, Blank -> "Pass the name of the directory, such as --arg $name=home."
            DotSegment -> "Pass the name of the directory the file goes into, such as --arg $name=home."
            SurroundingWhitespace -> "Pass the name without the surrounding whitespace, such as --arg $name=home."
            TrailingDot -> "Pass the name without the trailing `.`, such as --arg $name=home."
            ReservedName -> "Pick another name: no directory of this name can be created on Windows."
            Separator, UncreatableCharacter ->
                "Pass a name of letters, digits, `-` and `_`, such as --arg $name=home."
        }
    }
}

