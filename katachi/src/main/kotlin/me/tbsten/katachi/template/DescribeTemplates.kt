package me.tbsten.katachi.template

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.internal.absolutePathOf
import me.tbsten.katachi.internal.fileUri
import me.tbsten.katachi.internal.runProcessorCatching
import me.tbsten.katachi.processor.ArchitectureProcessContext
import me.tbsten.katachi.processor.ArchitectureProcessor
import me.tbsten.katachi.template.internal.DeclaredTemplate
import me.tbsten.katachi.template.internal.TemplateTarget
import me.tbsten.katachi.template.internal.declaredTemplatesOf
import me.tbsten.katachi.template.internal.encodeTemplateDescriptionJson
import me.tbsten.katachi.template.internal.resolveTemplateTarget
import me.tbsten.katachi.template.internal.templateDescriptionJsonOf
import me.tbsten.katachi.template.internal.templateDetailLines
import me.tbsten.katachi.template.internal.templateDetailOf
import me.tbsten.katachi.template.internal.templateListLines
import me.tbsten.katachi.template.internal.templateSetDetailLines
import me.tbsten.katachi.template.internal.templateSetDetailOf
import me.tbsten.katachi.template.internal.templateSetSummaryOf
import me.tbsten.katachi.template.internal.templateSummaryOf
import me.tbsten.katachi.template.internal.writeTemplateDescriptionJson

/**
 * Lists the templates of a definition, or explains one: its parameters and the file it produces.
 *
 * Registered by default under the key `templates`. [GenerateCodeFromTemplate] writes the file;
 * this one only says what a template takes and what it would write.
 *
 * ## What it prints
 *
 * Without `--arg template=`, every template with its specifier, title, role, parameters and
 * named wildcards, followed by every template set. With `--arg template=` naming a template set,
 * that set's parameters and, for each member, the values it passes and the file it would write.
 * With `--arg template=` naming a template, each parameter's type, default and accepted values,
 * the wildcards its `layout { }` names with `capture(...)`, a preview of its file's path and
 * contents, and a `katachiTemplate` command to paste.
 *
 * In the preview, a String parameter or a capture shows as `${name}`. A Boolean, Int or enum
 * parameter is given its default (or `true`, `0` or its first entry), and the output says which.
 * A file below a module capture is shown without a path.
 *
 * It reads only the declarations and writes nothing, except with `--arg format=json`, which is
 * for the katachi IDE plugin.
 *
 * ## What it answers
 *
 * A [TemplateList] or a [TemplateDetail]. A `--arg template=` naming more than one template is a
 * `failure` carrying [KatachiMultipleTemplatesToDescribeException], one no declaration answers to
 * [KatachiUnknownTemplateException], and one that answers to more than one
 * [KatachiAmbiguousTemplateException]. Nothing is thrown out of `process`.
 *
 * ## Example 1: list the templates, then explain one
 * ```kt
 * import me.tbsten.katachi.processor.process
 * import me.tbsten.katachi.template.DescribeTemplates
 *
 * projectArchitecture.process(DescribeTemplates, DescribeTemplates.Args()).getOrThrow()
 * projectArchitecture.process(DescribeTemplates, DescribeTemplates.Args(template = "data.Repository.repository")).getOrThrow()
 * ```
 *
 * ## Example 2: from the command line
 * ```sh
 * ./gradlew :architecture-test:katachiTemplates
 * ./gradlew :architecture-test:katachiTemplates --arg template=data.Repository.repository
 * ```
 *
 * @see GenerateCodeFromTemplate
 * @see TemplateDescription
 */
@ExperimentalKatachiApi
public object DescribeTemplates : ArchitectureProcessor<DescribeTemplates.Args, TemplateDescription> {
    override val argsSerializer: KSerializer<Args> = Args.serializer()

    override fun process(context: ArchitectureProcessContext<Args>): Result<TemplateDescription> =
        runProcessorCatching {
            val template = context.args.template
            if (context.args.format == DescribeTemplatesFormat.Json) {
                if (template != null) throw KatachiTemplateJsonWithTemplateException(template)
                val output = context.args.output ?: throw KatachiTemplateJsonOutputMissingException()
                writeJson(context, output)
            } else if (template == null) {
                val list = listAll(context)
                templateListLines(list).forEach(context::log)
                list
            } else {
                if (',' in template) throw KatachiMultipleTemplatesToDescribeException(template)
                describeOne(context, template)
            }
        }

    private fun listAll(context: ArchitectureProcessContext<Args>): TemplateList {
        val table = declaredTemplatesOf(context.declaredEntries)
        return TemplateList(
            templates = table.map(::templateSummaryOf),
            templateSets = context.architecture.allTemplateSets.map { templateSetSummaryOf(it, table) },
        )
    }

    private fun describeOne(context: ArchitectureProcessContext<Args>, specifier: String): TemplateDescription {
        val table = declaredTemplatesOf(context.declaredEntries)
        return when (val target = resolveTemplateTarget(listOf(specifier), table, context.architecture.allTemplateSets)) {
            is TemplateTarget.Set -> {
                val detail = templateSetDetailOf(target.declaration, table)
                templateSetDetailLines(detail).forEach(context::log)
                detail
            }
            is TemplateTarget.Templates -> {
                val detail = templateDetailOf(target.chosen.single())
                templateDetailLines(detail).forEach(context::log)
                detail
            }
        }
    }

    private fun writeJson(context: ArchitectureProcessContext<Args>, output: String): TemplateList {
        val table: List<DeclaredTemplate> = declaredTemplatesOf(context.declaredEntries)
        val list = listAll(context)
        templateListLines(list).forEach(context::log)
        writeTemplateDescriptionJson(output, encodeTemplateDescriptionJson(templateDescriptionJsonOf(context, table)))
        context.log("Wrote ${fileUri(absolutePathOf(output))}")
        return list
    }

    /**
     * The arguments of [DescribeTemplates]: which template to explain, or none to list them all.
     *
     * ## Example 1: in code
     * ```kt
     * DescribeTemplates.Args(template = "data.Repository.repository")
     * ```
     *
     * ## Example 2: on the command line
     * ```sh
     * ./gradlew :architecture-test:katachiTemplates --arg template=data.Repository.repository
     * ```
     *
     * ## Example 3: as a default in `build.gradle.kts`
     * ```kts
     * katachi {
     *     processors {
     *         args("templates") {
     *             arg("template", "data.Repository.repository")
     *         }
     *     }
     * }
     * ```
     *
     * A `--arg` of the same name wins over the build script.
     *
     * @constructor Takes the template to explain, or nothing to list them all.
     */
    @Serializable
    public data class Args(
        /**
         * The template to explain, by `role.id` (or `role` alone for its one template), as
         * `katachiTemplate` accepts it. `null` lists every template.
         */
        val template: String? = null,
        /** Whether to only print the answer, or also write every template as JSON for the katachi IDE plugin. */
        val format: DescribeTemplatesFormat = DescribeTemplatesFormat.Text,
        /**
         * The file `format=json` writes; required with `format=json`, unused otherwise.
         *
         * Relative to the working directory (under Gradle, the module directory), or absolute.
         */
        val output: String? = null,
    )
}

/**
 * The output format of [DescribeTemplates]: printed text only, or JSON written to a file as well.
 *
 * The JSON form is what the katachi IDE plugin reads; the Gradle plugin runs it for the IDE, so
 * there is no need to pass it by hand. With `Json`, a missing `output` fails with
 * [KatachiTemplateJsonOutputMissingException], a `template` given too with
 * [KatachiTemplateJsonWithTemplateException], and a file that cannot be written with
 * [KatachiTemplateJsonIoException].
 *
 * ## Example 1: write the JSON
 * ```kt
 * DescribeTemplates.Args(
 *     format = DescribeTemplatesFormat.Json,
 *     output = "build/katachi/templateDescription.json",
 * )
 * ```
 *
 * @see DescribeTemplates
 */
@ExperimentalKatachiApi
@Serializable
public enum class DescribeTemplatesFormat {
    /** Print the list or the detail, and write nothing. */
    @SerialName("text")
    Text,

    /**
     * Print the list, and write every template with its detail as JSON to `output`. Refuses a
     * `template`, since the JSON always holds every template.
     */
    @SerialName("json")
    Json,
}
