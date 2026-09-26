package me.tbsten.katachi.template

import me.tbsten.katachi.KatachiCheckException
import me.tbsten.katachi.KatachiDeclarationException
import me.tbsten.katachi.KatachiInternalException
import me.tbsten.katachi.internal.absolutePathOf
import me.tbsten.katachi.internal.fileUri

/**
 * `--arg format=json` was given without `--arg output=`, so there is nowhere to write the JSON.
 *
 * The Gradle plugin passes both to its `katachiInternalTemplatesJson` task. Seeing this means the
 * two were separated: a run started by hand, or an `args("internalTemplatesJson") { }` block that
 * replaced one of them.
 *
 * ## Example 1: catch a JSON run that names no file
 * ```kt
 * import io.kotest.assertions.throwables.shouldThrow
 * import me.tbsten.katachi.processor.process
 * import me.tbsten.katachi.template.DescribeTemplates
 * import me.tbsten.katachi.template.DescribeTemplatesFormat
 * import me.tbsten.katachi.template.KatachiTemplateJsonOutputMissingException
 *
 * shouldThrow<KatachiTemplateJsonOutputMissingException> {
 *     projectArchitecture.process(
 *         DescribeTemplates,
 *         DescribeTemplates.Args(format = DescribeTemplatesFormat.Json),
 *     ).getOrThrow()
 * }
 * ```
 *
 * @see DescribeTemplates
 */
public class KatachiTemplateJsonOutputMissingException internal constructor() : KatachiDeclarationException(
    message = buildString {
        appendLine("--arg format=json was given without --arg output=.")
        appendLine(
            "The JSON form of the template list is written to a file rather than printed, and " +
                "nothing says which file.",
        )
        append(
            "Pass --arg output=<path>, relative to the module directory or absolute, or drop " +
                "--arg format=json to print the list as text.",
        )
    },
)

/**
 * `--arg format=json` and `--arg roleName=` were given together.
 *
 * The JSON form always holds every template, so a role to narrow it to would be silently
 * ignored. Refusing says so instead.
 *
 * ## Example 1: catch a JSON run narrowed to one role
 * ```kt
 * import io.kotest.assertions.throwables.shouldThrow
 * import io.kotest.matchers.shouldBe
 * import me.tbsten.katachi.processor.process
 * import me.tbsten.katachi.template.DescribeTemplates
 * import me.tbsten.katachi.template.DescribeTemplatesFormat
 * import me.tbsten.katachi.template.KatachiTemplateJsonWithRoleNameException
 *
 * val thrown = shouldThrow<KatachiTemplateJsonWithRoleNameException> {
 *     projectArchitecture.process(
 *         DescribeTemplates,
 *         DescribeTemplates.Args(roleName = "Repository", format = DescribeTemplatesFormat.Json, output = "out.json"),
 *     ).getOrThrow()
 * }
 * thrown.roleName shouldBe "Repository"
 * ```
 *
 * @see DescribeTemplates
 */
public class KatachiTemplateJsonWithRoleNameException internal constructor(
    /** The name `--arg roleName=` carried, as written. */
    public val roleName: String,
) : KatachiDeclarationException(
    message = buildString {
        appendLine("""--arg format=json was given together with --arg roleName=$roleName.""")
        appendLine(
            "The JSON form always holds every template and the detail of each, so it has no " +
                "use for a role to narrow it to.",
        )
        append(
            "Drop --arg roleName= to write the JSON, or drop --arg format=json to print that " +
                "one template as text.",
        )
    },
)

/**
 * katachi could not write the JSON form of the template list.
 *
 * Nothing about the definition is wrong: the directory could not be created, the disk is full,
 * the path names a directory. The file that was there before, if any, is left as it was, since the
 * JSON is written next to it first and moved over it only once it is complete.
 *
 * ## Example 1: say where a failing run was writing
 * ```kt
 * import io.kotest.assertions.throwables.shouldThrow
 * import me.tbsten.katachi.processor.process
 * import me.tbsten.katachi.template.DescribeTemplates
 * import me.tbsten.katachi.template.DescribeTemplatesFormat
 * import me.tbsten.katachi.template.KatachiTemplateJsonIoException
 *
 * val failure = shouldThrow<KatachiTemplateJsonIoException> {
 *     projectArchitecture.process(
 *         DescribeTemplates,
 *         DescribeTemplates.Args(format = DescribeTemplatesFormat.Json, output = "/nowhere/templates.json"),
 *     ).getOrThrow()
 * }
 * println("could not write ${failure.output}")
 * ```
 *
 * @see DescribeTemplates
 */
public class KatachiTemplateJsonIoException internal constructor(
    /** The file being written, exactly as `--arg output=` spelled it. */
    public val output: String,
    cause: Throwable,
) : KatachiCheckException(
    message = buildString {
        appendLine("Cannot write the template list to ${fileUri(absolutePathOf(output))}.")
        appendLine("The filesystem refused it: ${cause::class.simpleName}: ${cause.message}")
        append(
            "Check that --arg output= names a file this process may write. A relative path is " +
                "resolved against the directory the processor was started in, which under " +
                "Gradle is the module the task belongs to.",
        )
    },
    cause = cause,
)

/**
 * The JSON form of the template list met a value it has no way to write.
 *
 * It writes katachi's own template descriptions, and every value they hold is one it supports. A
 * run cannot cause this; only a change to those types that the writer was not taught about can.
 *
 * ## Example 1: report it rather than treating it as a failed run
 * ```kt
 * try {
 *     projectArchitecture.process(
 *         DescribeTemplates,
 *         DescribeTemplates.Args(format = DescribeTemplatesFormat.Json, output = "out.json"),
 *     ).getOrThrow()
 * } catch (bug: KatachiUnsupportedTemplateJsonValueException) {
 *     println("Please report this at https://github.com/TBSten/katachi/issues: ${bug.kind}")
 * }
 * ```
 *
 * @see DescribeTemplates
 */
public class KatachiUnsupportedTemplateJsonValueException internal constructor(
    /** What could not be written: a serial kind such as `MAP`, or a type such as `Double`. */
    public val kind: String,
) : KatachiInternalException(
    message = """
        The JSON form of the template list met $kind, which it has no way to write.
        Every value of katachi's template descriptions is one it supports, so a definition cannot
        cause this. This is a bug in katachi. Please report it at https://github.com/TBSten/katachi/issues.
    """.trimIndent(),
)
