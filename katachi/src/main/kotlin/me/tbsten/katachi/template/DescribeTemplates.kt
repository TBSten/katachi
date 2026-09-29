package me.tbsten.katachi.template

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.check.internal.moduleIndex
import me.tbsten.katachi.dsl.files.internal.findProjectRoot
import me.tbsten.katachi.internal.absolutePathOf
import me.tbsten.katachi.internal.catching
import me.tbsten.katachi.internal.fileUri
import me.tbsten.katachi.internal.runProcessorCatching
import me.tbsten.katachi.processor.ArchitectureProcessContext
import me.tbsten.katachi.processor.ArchitectureProcessor
import me.tbsten.katachi.processor.internal.fileSystem
import me.tbsten.katachi.template.internal.DeclaredTemplate
import me.tbsten.katachi.template.internal.declaredTemplatesOf
import me.tbsten.katachi.template.internal.encodeTemplateDescriptionJson
import me.tbsten.katachi.template.internal.modulePlacementsOf
import me.tbsten.katachi.template.internal.resolveTemplate
import me.tbsten.katachi.template.internal.templateDetailLines
import me.tbsten.katachi.template.internal.templateDetailOf
import me.tbsten.katachi.template.internal.templateListLines
import me.tbsten.katachi.template.internal.templateSummaryOf
import me.tbsten.katachi.template.internal.writeTemplateDescriptionJson

/**
 * Lists the templates of a definition, or explains one: its parameters and the file it produces.
 *
 * Registered under the key `templates`, so `./gradlew katachiTemplates` works without
 * registering anything. It is the companion of [GenerateCodeFromTemplate]: this one says what a
 * template takes and what it would write, that one writes it.
 *
 * ## What it prints
 *
 * Without `--arg template=`, every declared template: its complete specifier, title and the role
 * it belongs to, the names of its parameters and its named wildcards. With `--arg template=`,
 * that one template in full: each parameter's type, default, accepted values and whether it is
 * required; the wildcards its `layout { }` names with `capture(...)`, which a run also takes as
 * `--arg`; its file's path, as its own declaration decides it, and its contents; the other
 * Boolean and enum values that change its content or its parameters; and a `katachiTemplate`
 * command to paste.
 *
 * ## How the file is previewed
 *
 * A template is Kotlin code, so its content exists only once its parameters have values. A
 * String parameter is given a placeholder of its own name, `${name}`, so the contents show where
 * each value goes. A Boolean, Int or enum parameter cannot hold one, so it is given its default,
 * or `true`, `0` or its first entry when it has none -- and the output says which it was. A
 * capture reads as `${name}` too, in the path as in the contents -- except a module capture,
 * whose module only the modules that exist could pick, so such a file is shown without a path.
 *
 * It reads nothing but the declarations, and writes nothing unless `--arg format=json` asks it
 * to -- which also lists the modules, as "The JSON form" below says.
 *
 * ## The JSON form
 *
 * With `--arg format=json` it writes every template, listed and explained, to the one file
 * `--arg output=` names, and still prints the list. The katachi IDE plugin reads that file: the
 * Gradle plugin registers this processor a second time under the internal key
 * `internalTemplatesJson` with both arguments set, and that task is not meant to be run by hand.
 * A template whose preview fails is listed without a detail rather than failing the run.
 *
 * The JSON also says, for a template whose file sits below a module capture, where that file
 * lands in each module that exists and that the capture can pick -- what the text form leaves
 * open. That one part reads the project's modules, and only when such a template is declared.
 *
 * ## What it answers
 *
 * A [TemplateList] or a [TemplateDetail]. A `--arg template=` naming more than one template is a
 * `failure` carrying [KatachiMultipleTemplatesToDescribeException], one no declaration answers to
 * [KatachiUnknownTemplateException], and one that answers to more than one
 * [KatachiAmbiguousTemplateException]. With `format=json`, a missing `output` is
 * [KatachiTemplateJsonOutputMissingException], a `template` given too is
 * [KatachiTemplateJsonWithTemplateException], and a file that cannot be written is
 * [KatachiTemplateJsonIoException]. Nothing is thrown out of `process`.
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
 * From the command line -- no registration needed, the Gradle plugin registers it under
 * `templates`:
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
                val list = TemplateList(templates = declaredTemplatesOf(context.declaredEntries).map(::templateSummaryOf))
                templateListLines(list).forEach(context::log)
                list
            } else {
                if (',' in template) throw KatachiMultipleTemplatesToDescribeException(template)
                val resolved = resolveTemplate(template, declaredTemplatesOf(context.declaredEntries))
                val detail = templateDetailOf(resolved)
                templateDetailLines(detail).forEach(context::log)
                detail
            }
        }

    /** Lists every template, and writes them with their details as JSON to [output]. */
    private fun writeJson(context: ArchitectureProcessContext<Args>, output: String): TemplateList {
        val table: List<DeclaredTemplate> = declaredTemplatesOf(context.declaredEntries)
        val list = TemplateList(templates = table.map(::templateSummaryOf))
        templateListLines(list).forEach(context::log)
        // One template that cannot be previewed must not cost the IDE every other one. It is
        // still listed, with `conflict: true`, as the text list already shows it.
        val previewed = table.mapNotNull { template -> catching { template to templateDetailOf(template) }.getOrNull() }
        val details = previewed.map { it.second }
        // Reads the modules only when a template sits below a module capture: which modules exist
        // is what decides where such a file lands, and the declarations alone cannot say it.
        val placements = modulePlacementsOf(previewed.map { it.first }) {
            moduleIndex(context.fileSystem, findProjectRoot(context.fileSystem).path, context.architecture.moduleResolver)
        }
        writeTemplateDescriptionJson(output, encodeTemplateDescriptionJson(list.templates, details, placements))
        context.log("Wrote ${fileUri(absolutePathOf(output))}")
        return list
    }

    /**
     * Which template to explain; `null` lists them all.
     *
     * ## Example 1: from code and from the command line
     * ```kt
     * import me.tbsten.katachi.template.DescribeTemplates
     *
     * // ./gradlew katachiTemplates --arg template=data.Repository.repository
     * DescribeTemplates.Args(template = "data.Repository.repository")
     * ```
     */
    @Serializable
    public data class Args(
        /**
         * The template to explain, by `role.id` (or `role` alone for its one template), as
         * `katachiTemplate` accepts it. `null` lists every template.
         *
         * A `String` rather than a one-element `List`: writing `Args(template = "data.Repository.repository")`
         * from Kotlin is the natural way to name one thing, and a `List` would add a second,
         * meaningless "no template" (an empty list) beside `null`.
         */
        val template: String? = null,
        /**
         * Whether to print the answer only, or to write every template as JSON as well.
         *
         * `json` is what the Gradle plugin's internal `internalTemplatesJson` task passes for the
         * katachi IDE plugin; it is not a format meant to be read by hand.
         */
        val format: DescribeTemplatesFormat = DescribeTemplatesFormat.Text,
        /**
         * The file `format=json` writes, relative to the working directory -- under Gradle, the
         * module directory -- or absolute. Required with `format=json`, unused otherwise.
         */
        val output: String? = null,
    )
}

/**
 * What [DescribeTemplates] produces besides its printed answer.
 *
 * ## Example 1: write the JSON the IDE plugin reads
 * ```kt
 * import me.tbsten.katachi.template.DescribeTemplates
 * import me.tbsten.katachi.template.DescribeTemplatesFormat
 *
 * // ./gradlew katachiInternalTemplatesJson, which passes these two for you
 * DescribeTemplates.Args(
 *     format = DescribeTemplatesFormat.Json,
 *     output = "build/katachi/internalTemplatesJson/templateDescription.json",
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
