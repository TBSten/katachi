package me.tbsten.katachi.template

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.internal.absolutePathOf
import me.tbsten.katachi.internal.catching
import me.tbsten.katachi.internal.fileUri
import me.tbsten.katachi.internal.runProcessorCatching
import me.tbsten.katachi.processor.ArchitectureProcessContext
import me.tbsten.katachi.processor.ArchitectureProcessor
import me.tbsten.katachi.template.internal.encodeTemplateDescriptionJson
import me.tbsten.katachi.template.internal.templateDetailLines
import me.tbsten.katachi.template.internal.templateDetailOf
import me.tbsten.katachi.template.internal.templateListLines
import me.tbsten.katachi.template.internal.templateRoleOf
import me.tbsten.katachi.template.internal.templateSummaryOf
import me.tbsten.katachi.template.internal.writeTemplateDescriptionJson

/**
 * Lists the templates of a definition, or explains one: its parameters and the files it produces.
 *
 * Registered under the key `templates`, so `./gradlew katachiTemplates` works without
 * registering anything. It is the companion of [GenerateCodeFromTemplate]: this one says what a
 * template takes and what it would write, that one writes it.
 *
 * ## What it prints
 *
 * Without `--arg roleName=`, every role that declares a `template { }`: its qualified name,
 * title and summary, the names of its parameters and how many files it produces. With
 * `--arg roleName=`, that role's template in full: each parameter's type, default, accepted
 * values and whether it is required; each file's path, as the role's `layout { }` decides it,
 * and its contents; the other Boolean and enum values that change which files are produced; and a
 * `katachiTemplate` command to paste.
 *
 * ## How the files are previewed
 *
 * A template is Kotlin code, so its files exist only once its parameters have values. A String
 * parameter is given a placeholder of its own name, `${name}`, so the contents show where each
 * value goes. A Boolean, Int or enum parameter cannot hold one, so it is given its default, or
 * `true`, `0` or its first entry when it has none -- and the output says which it was. Only the
 * branch those values take is rendered; every other value of a Boolean or an enum is tried one
 * parameter at a time, and those that change the set of files are listed.
 *
 * It reads nothing but the declarations, and writes nothing unless `--arg format=json` asks it
 * to.
 *
 * ## The JSON form
 *
 * With `--arg format=json` it writes every template, listed and explained, to the one file
 * `--arg output=` names, and still prints the list. The katachi IDE plugin reads that file: the
 * Gradle plugin registers this processor a second time under the internal key
 * `internalTemplatesJson` with both arguments set, and that task is not meant to be run by hand.
 * A template whose preview fails is listed without a detail rather than failing the run.
 *
 * ## What it answers
 *
 * A [TemplateList] or a [TemplateDetail]. A `roleName` no role answers to is a `failure` carrying
 * [KatachiUnknownTemplateRoleException], and a role without a template one carrying
 * [KatachiNoTemplateException]. With `format=json`, a missing `output` is
 * [KatachiTemplateJsonOutputMissingException], a `roleName` given too is
 * [KatachiTemplateJsonWithRoleNameException], and a file that cannot be written is
 * [KatachiTemplateJsonIoException]. Nothing is thrown out of `process`.
 *
 * ## Example 1: list the templates, then explain one
 * ```kt
 * import me.tbsten.katachi.processor.process
 * import me.tbsten.katachi.template.DescribeTemplates
 *
 * projectArchitecture.process(DescribeTemplates, DescribeTemplates.Args()).getOrThrow()
 * projectArchitecture.process(DescribeTemplates, DescribeTemplates.Args(roleName = "Repository")).getOrThrow()
 * ```
 *
 * From the command line -- no registration needed, the Gradle plugin registers it under
 * `templates`:
 * ```sh
 * ./gradlew :architecture-test:katachiTemplates
 * ./gradlew :architecture-test:katachiTemplates --arg roleName=Repository
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
            val roleName = context.args.roleName
            if (context.args.format == DescribeTemplatesFormat.Json) {
                if (roleName != null) throw KatachiTemplateJsonWithRoleNameException(roleName)
                val output = context.args.output ?: throw KatachiTemplateJsonOutputMissingException()
                writeJson(context, output)
            } else if (roleName == null) {
                val list = TemplateList(
                    templates = context.roles.filter { it.templates.isNotEmpty() }.map(::templateSummaryOf),
                )
                templateListLines(list).forEach(context::log)
                list
            } else {
                val role = templateRoleOf(context.roles, roleName)
                val detail = templateDetailOf(role, context.declaredEntries)
                templateDetailLines(detail).forEach(context::log)
                detail
            }
        }

    /** Lists every template, and writes them with their details as JSON to [output]. */
    private fun writeJson(context: ArchitectureProcessContext<Args>, output: String): TemplateList {
        val roles = context.roles.filter { it.templates.isNotEmpty() }
        val list = TemplateList(templates = roles.map(::templateSummaryOf))
        templateListLines(list).forEach(context::log)
        // One template that cannot be previewed must not cost the IDE every other one. It is
        // still listed, with `fileCount: null`, as the text list already shows it.
        val details = roles.mapNotNull { role ->
            catching { templateDetailOf(role, context.declaredEntries) }.getOrNull()
        }
        writeTemplateDescriptionJson(output, encodeTemplateDescriptionJson(list.templates, details))
        context.log("Wrote ${fileUri(absolutePathOf(output))}")
        return list
    }

    /**
     * Which template to explain; none lists them all.
     *
     * ## Example 1: from code and from the command line
     * ```kt
     * import me.tbsten.katachi.template.DescribeTemplates
     *
     * // ./gradlew katachiTemplates --arg roleName=data/Repository
     * DescribeTemplates.Args(roleName = "data/Repository")
     * ```
     */
    @Serializable
    public data class Args(
        /**
         * The role to explain, by its name (`Repository`) or its qualified name
         * (`data/Repository`), as `katachiTemplate` accepts it. `null` lists every template.
         */
        val roleName: String? = null,
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
     * `roleName`, since the JSON always holds every template.
     */
    @SerialName("json")
    Json,
}
