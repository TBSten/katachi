package me.tbsten.katachi.template

import me.tbsten.katachi.KatachiCheckException
import me.tbsten.katachi.KatachiDeclarationException
import me.tbsten.katachi.dsl.DeclarationSite

/**
 * A run gave no value for a capture its role's template needs.
 *
 * `capture("feature")` in a `layout { }` stands for a directory that template generation fills
 * in from `--arg feature=...`, and `":feature:${capture("feature")}".module { }` for a module.
 * Without the value there is no directory, and choosing one would be inventing the answer.
 *
 * The same exception, with the same message, whichever way the capture was needed: a file whose
 * place names it, or a template that reads it with `captureValue(...)`. A place that needs it fails
 * the file even when a place without captures would take it: writing there instead would drop the
 * forgotten `--arg` without a word.
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
 *         "ViewModel" {
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
public class KatachiMissingTemplateCaptureException internal constructor(
    /** The role whose template needs the values, qualified. */
    public val role: String,
    /** The generated file name that could not be placed, or `null` when `captureValue(...)` read the capture. */
    public val fileName: String?,
    /** The capture names without a value, in declaration order. */
    public val names: List<String>,
    /**
     * Each layout pattern naming one of [names], with every capture of it written as `<name>` in
     * place of its `*`, and the names of [names] it holds.
     */
    public val missing: Map<String, List<String>>,
    /** For each of [names], where the layout declares it. */
    public val captureDeclaredAt: Map<String, DeclarationSite>,
    /**
     * For each of [names], the values that pick a directory or a module that exists now, sorted.
     * Empty when none could be listed -- the directory above the capture holds a wildcard of its
     * own, or the project could not be read.
     */
    public val existingValues: Map<String, List<String>>,
    /** How each of [names] is declared: `capture("feature")` or `":feature:${capture("feature")}".module { }`. */
    internal val declaredWith: Map<String, String>,
    /** Where the `file(...)` that could not be placed, or the `template { }` that read the capture, was written. */
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
 * A capture is one level of a path, exactly like the `*` it replaces. A value that is empty or
 * blank, that climbs (`.` or `..`), that holds a separator, or that holds a character no file name
 * may hold would make generation write somewhere the layout does not describe -- or outside the
 * project. What Windows refuses or silently changes is refused on every platform, so that a value
 * means the same directory wherever the run happens: a trailing `.` or space (Windows drops it,
 * and `home.` would land in `home`) and a reserved device name such as `CON`.
 *
 * A module capture's value is one level of the module path -- `home` of `:feature:home` -- and is
 * checked by the same rule.
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
public class KatachiInvalidTemplateCaptureValueException internal constructor(
    /** The role whose layout names the capture, qualified. */
    public val role: String,
    /** The capture's name, which is also its `--arg` name. */
    public val name: String,
    /** The value the run passed. */
    public val value: String,
    /** What is wrong: with [value] itself when [segment] is `null`, with [segment] otherwise. */
    public val problem: Problem,
    /**
     * The whole path segment [value] landed in, with every capture of it filled in -- `"a."` for
     * `"${capture("x")}."` given `--arg x=a`. `null` when [problem] is about [value] on its own,
     * such as one holding a `/`, rather than about the segment it partially filled.
     */
    public val segment: String? = null,
    /** Where a layout path naming the capture was declared. */
    public val captureDeclaredAt: DeclarationSite,
    /** Whether the capture names a `*` of a module key rather than a directory level. */
    public val isModuleCapture: Boolean = false,
) : KatachiDeclarationException(
    message = buildString {
        val level = if (isModuleCapture) "one level of the module path" else "one directory level"
        if (segment == null) {
            appendLine(
                """Capture "$name" of role "$role" declared at $captureDeclaredAt was given "$value", """ +
                    "which is not $level: ${problem.description}.",
            )
        } else {
            appendLine(
                """Capture "$name" of role "$role" declared at $captureDeclaredAt was given "$value", """ +
                    """which fills its path segment in as "$segment": ${problem.description}.""",
            )
        }
        val lastLevel = value.split(':', '/', '\\').lastOrNull { it.isNotBlank() }
        when {
            isModuleCapture && ':' in value && lastLevel != null -> append(
                "A module capture is given the one level of the module path its * stands for, not the " +
                    "module path. Pass `$lastLevel`, not `$value`: --arg $name=$lastLevel.",
            )
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
         * The value holds a character no file name may hold: `*`, `:` and the rest Windows refuses,
         * a control character (DEL included) or a line separator such as U+2028.
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

/**
 * A module capture was given a value that names no existing module.
 *
 * A wildcard module key, `":feature:${capture("feature")}".module { }`, stands for the modules
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
 * // ./gradlew katachiTemplate --arg template=feature.Screen --arg feature=hoem --arg name=Home
 * val thrown = shouldThrow<KatachiTemplateModuleNotFoundException> { generate() }
 * thrown.existingArgs shouldBe listOf("--arg feature=home", "--arg feature=settings")
 * ```
 *
 * @see GenerateCodeFromTemplate
 */
public class KatachiTemplateModuleNotFoundException internal constructor(
    /** The role whose template produced the file, qualified. */
    public val role: String,
    /** The generated file name, after the template's parameters were filled in. */
    public val fileName: String,
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
            """Template file "$fileName" declared at $declaredAt would go into module $modulePath of role "$role", """ +
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
