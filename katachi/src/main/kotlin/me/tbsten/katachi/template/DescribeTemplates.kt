package me.tbsten.katachi.template

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.internal.runProcessorCatching
import me.tbsten.katachi.processor.ArchitectureProcessContext
import me.tbsten.katachi.processor.ArchitectureProcessor
import me.tbsten.katachi.template.internal.templateDetailLines
import me.tbsten.katachi.template.internal.templateDetailOf
import me.tbsten.katachi.template.internal.templateListLines
import me.tbsten.katachi.template.internal.templateRoleOf
import me.tbsten.katachi.template.internal.templateSummaryOf

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
 * It writes nothing, and reads nothing but the declarations.
 *
 * ## What it answers
 *
 * A [TemplateList] or a [TemplateDetail]. A `roleName` no role answers to is a `failure` carrying
 * [KatachiUnknownTemplateRoleException], and a role without a template one carrying
 * [KatachiNoTemplateException]. Nothing is thrown out of `process`.
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
            if (roleName == null) {
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
    )
}
