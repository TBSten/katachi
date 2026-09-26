package me.tbsten.katachi.template

import me.tbsten.katachi.ExperimentalKatachiApi

/**
 * What [DescribeTemplates] answers: the list of templates, or the detail of one.
 *
 * Without `--arg roleName=` the answer is a [TemplateList]; with it, a [TemplateDetail].
 *
 * ## Example 1: tell the two answers apart
 * ```kt
 * import me.tbsten.katachi.processor.process
 * import me.tbsten.katachi.template.DescribeTemplates
 * import me.tbsten.katachi.template.TemplateDetail
 * import me.tbsten.katachi.template.TemplateList
 *
 * when (val answer = projectArchitecture.process(DescribeTemplates, DescribeTemplates.Args()).getOrThrow()) {
 *     is TemplateList -> answer.templates.map { it.roleName }
 *     is TemplateDetail -> answer.files.map { it.fileName }
 * }
 * ```
 *
 * @see DescribeTemplates
 */
@ExperimentalKatachiApi
public sealed interface TemplateDescription

/**
 * Every role that declares a `template { }`, in declaration order.
 *
 * ## Example 1: the qualified names of every role with a template
 * ```kt
 * import me.tbsten.katachi.processor.process
 * import me.tbsten.katachi.template.DescribeTemplates
 * import me.tbsten.katachi.template.TemplateList
 *
 * val list = projectArchitecture.process(DescribeTemplates, DescribeTemplates.Args()).getOrThrow()
 * (list as? TemplateList)?.templates?.map { it.roleName }
 * ```
 *
 * @see TemplateSummary
 */
@ExperimentalKatachiApi
public class TemplateList internal constructor(
    /** One entry per role with a template. Empty when no role declares one. */
    public val templates: List<TemplateSummary>,
) : TemplateDescription {
    override fun toString(): String =
        if (templates.size == 1) "1 template" else "${templates.size} templates"
}

/**
 * One line of a [TemplateList]: which role, what it is, what it takes and how much it writes.
 *
 * ## Example 1: the roles whose template takes no parameter at all
 * ```kt
 * val list = projectArchitecture.process(DescribeTemplates, DescribeTemplates.Args()).getOrThrow()
 * (list as? TemplateList)?.templates.orEmpty().filter { it.parameterNames.isEmpty() }
 * ```
 *
 * @see TemplateList
 */
@ExperimentalKatachiApi
public class TemplateSummary internal constructor(
    /** The role's qualified name (`data/Repository`), which `--arg roleName=` accepts. */
    public val roleName: String,
    /** The role's `title`, or `null` when it declares none. */
    public val title: String?,
    /** The role's `summary`, or `null` when it declares none. */
    public val summary: String?,
    /** The parameters the template declares when previewed, in declaration order. */
    public val parameterNames: List<String>,
    /**
     * How many files the template produces when previewed, or `null` when the preview failed.
     *
     * A preview fills the parameters as [TemplateDetail.parameters] explains, so a template whose
     * files depend on a Boolean or an enum may produce a different number with other values.
     */
    public val fileCount: Int?,
) {
    override fun toString(): String = "TemplateSummary($roleName)"
}

/**
 * One role's template explained: its parameters, and the files it produces with them filled in
 * by stand-in values.
 *
 * ## How the parameters are filled in
 *
 * A String parameter is filled with a placeholder spelling its own name, `${name}`, so the
 * contents show where each value goes. A Boolean, Int or enum parameter cannot hold such a
 * placeholder, so it takes its default, or -- when it has none -- `true`, `0` or its first entry.
 * Which value each parameter took is [TemplateParameterPreview.previewValue].
 *
 * The files are the ones the template produces with *those* values. A file that appears only
 * under another value of a Boolean or an enum is listed in [branches].
 *
 * ## Example 1: the paths the template would write
 * ```kt
 * import me.tbsten.katachi.processor.process
 * import me.tbsten.katachi.template.DescribeTemplates
 * import me.tbsten.katachi.template.TemplateDetail
 *
 * val detail = projectArchitecture.process(
 *     DescribeTemplates,
 *     DescribeTemplates.Args(roleName = "Repository"),
 * ).getOrThrow()
 * (detail as? TemplateDetail)?.files?.map { it.path ?: it.fileName }
 * ```
 *
 * @see TemplateParameterPreview
 * @see TemplateFilePreview
 * @see TemplateBranch
 */
@ExperimentalKatachiApi
public class TemplateDetail internal constructor(
    /** The role's qualified name. */
    public val roleName: String,
    /** The role's `title`, or `null` when it declares none. */
    public val title: String?,
    /** The role's `summary`, or `null` when it declares none. */
    public val summary: String?,
    /** The parameters the template declares with the preview's values, in declaration order. */
    public val parameters: List<TemplateParameterPreview>,
    /** The files the template produces with the preview's values, in declaration order. */
    public val files: List<TemplateFilePreview>,
    /** The other Boolean and enum values that change which files are produced. */
    public val branches: List<TemplateBranch>,
    /** A `./gradlew katachiTemplate ...` command that generates this template, ready to paste. */
    public val exampleCommand: String,
) : TemplateDescription {
    override fun toString(): String =
        "Template $roleName: ${parameters.size} parameters, ${files.size} files"
}

/**
 * One parameter of a [TemplateDetail]: what it accepts, and the value the preview gave it.
 *
 * ## Example 1: the parameters `katachiTemplate` has to be given
 * ```kt
 * val detail = projectArchitecture.process(
 *     DescribeTemplates,
 *     DescribeTemplates.Args(roleName = "Repository"),
 * ).getOrThrow()
 * (detail as? TemplateDetail)?.parameters.orEmpty().filter { it.isRequired }.map { it.name }
 * ```
 *
 * @see TemplateDetail
 */
@ExperimentalKatachiApi
public class TemplateParameterPreview internal constructor(
    /** The name, which is also its `--arg` name. */
    public val name: String,
    /** Which of `stringParameter()` and its siblings declared it. */
    public val kind: TemplateParameterKind,
    /** `String`, `Boolean`, `Int`, or the enum's simple name. */
    public val typeName: String,
    /** The default as `--arg` would spell it, or `null` when there is none. */
    public val default: String?,
    /** Every value accepted, where the list is short enough to print: Boolean and enum. */
    public val acceptedValues: List<String>,
    /** Whether a run of the template has to be given this parameter: it has no default. */
    public val isRequired: Boolean,
    /** The value the preview filled in: `${name}` for a String, a real value otherwise. */
    public val previewValue: String,
    /** Why the preview chose [previewValue]. */
    public val previewValueSource: PreviewValueSource,
) {
    override fun toString(): String = "TemplateParameterPreview($name: $typeName = $previewValue)"
}

/**
 * Which kind of template parameter a [TemplateParameterPreview] is.
 *
 * ## Example 1: the parameters that decide a branch
 * ```kt
 * detail.parameters.filter {
 *     it.kind == TemplateParameterKind.BooleanParameter || it.kind == TemplateParameterKind.EnumParameter
 * }
 * ```
 */
@ExperimentalKatachiApi
public enum class TemplateParameterKind {
    /** `stringParameter()`. */
    StringParameter,

    /** `booleanParameter()`. */
    BooleanParameter,

    /** `intParameter()`. */
    IntParameter,

    /** `enumParameter()`. */
    EnumParameter,
}

/**
 * Why a preview filled a parameter with the value it did.
 *
 * ## Example 1: the parameters whose value the preview made up
 * ```kt
 * detail.parameters.filter { it.previewValueSource == PreviewValueSource.StandIn }
 * ```
 */
@ExperimentalKatachiApi
public enum class PreviewValueSource {
    /** A String parameter, filled with `${name}` to show where its value goes. */
    Placeholder,

    /** A Boolean, Int or enum parameter, filled with its declared default. */
    Default,

    /** A Boolean, Int or enum parameter without a default: `true`, `0`, or the first entry. */
    StandIn,
}

/**
 * One file of a [TemplateDetail]: where it would land, and what it would hold.
 *
 * ## Example 1: the files whose directory the layout does not decide
 * ```kt
 * detail.files.filter { it.path == null }.map { it.unresolvedPatterns }
 * ```
 *
 * @see TemplateDetail
 */
@ExperimentalKatachiApi
public class TemplateFilePreview internal constructor(
    /** The file name, with the preview's values filled in: `${name}Repository.kt`. */
    public val fileName: String,
    /**
     * Where the file would land, relative to the project root, or `null` when the layout does
     * not decide it: every pattern that accepts the name still has a wildcard in its directory,
     * such as a module written `:feature:*`. `katachiTemplate` refuses such a file.
     */
    public val path: String?,
    /** When [path] is `null`, the patterns that accept the name. Empty otherwise. */
    public val unresolvedPatterns: List<String>,
    /** The contents, with the preview's values filled in. */
    public val content: String,
) {
    override fun toString(): String = "TemplateFilePreview(${path ?: fileName})"
}

/**
 * Another value of one Boolean or enum parameter, and how it changes the files produced.
 *
 * Found by changing that one parameter and keeping the preview's values for the rest.
 *
 * ## Example 1: what `--arg withImpl=false` leaves out
 * ```kt
 * detail.branches.filter { it.parameterName == "withImpl" && it.value == "false" }.flatMap { it.removedFiles }
 * ```
 *
 * @see TemplateDetail
 */
@ExperimentalKatachiApi
public class TemplateBranch internal constructor(
    /** The parameter whose value was changed. */
    public val parameterName: String,
    /** The value it was changed to, as `--arg` spells it. */
    public val value: String,
    /** File names produced with [value] and not with the preview's value. */
    public val addedFiles: List<String>,
    /** File names produced with the preview's value and not with [value]. */
    public val removedFiles: List<String>,
) {
    override fun toString(): String = "TemplateBranch($parameterName=$value)"
}
