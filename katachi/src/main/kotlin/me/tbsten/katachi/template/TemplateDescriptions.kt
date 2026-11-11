package me.tbsten.katachi.template

import kotlinx.serialization.Serializable
import me.tbsten.katachi.ExperimentalKatachiApi

/**
 * What [DescribeTemplates] answers: the list of templates, or the detail of one.
 *
 * Without `--arg template=` the answer is a [TemplateList]; with it, a [TemplateDetail], or a
 * [TemplateSetDetail] when it names a template set.
 *
 * ## Example 1: tell the answers apart
 * ```kt
 * import me.tbsten.katachi.processor.process
 * import me.tbsten.katachi.template.DescribeTemplates
 * import me.tbsten.katachi.template.TemplateDetail
 * import me.tbsten.katachi.template.TemplateList
 * import me.tbsten.katachi.template.TemplateSetDetail
 *
 * when (val answer = projectArchitecture.process(DescribeTemplates, DescribeTemplates.Args()).getOrThrow()) {
 *     is TemplateList -> answer.templates.map { it.template }
 *     is TemplateDetail -> answer.files.map { it.fileName }
 *     is TemplateSetDetail -> answer.members.map { it.template }
 * }
 * ```
 *
 * @see DescribeTemplates
 */
@ExperimentalKatachiApi
public sealed interface TemplateDescription

/**
 * Every `.template { }` and `.templateSet { }` declared, in declaration order.
 *
 * ## Example 1: the specifiers of every template
 * ```kt
 * import me.tbsten.katachi.processor.process
 * import me.tbsten.katachi.template.DescribeTemplates
 * import me.tbsten.katachi.template.TemplateList
 *
 * val list = projectArchitecture.process(DescribeTemplates, DescribeTemplates.Args()).getOrThrow()
 * (list as? TemplateList)?.templates?.map { it.template }
 * ```
 *
 * @see TemplateSummary
 */
@ExperimentalKatachiApi
public class TemplateList internal constructor(
    /** One entry per declared template. Empty when no declaration has one. */
    public val templates: List<TemplateSummary>,
    /** One entry per declared `"name".templateSet { }`, in declaration order. Empty when there is none. */
    public val templateSets: List<TemplateSetSummary> = emptyList(),
) : TemplateDescription {
    /** `<n> templates`, the count alone. */
    override fun toString(): String =
        if (templates.size == 1) "1 template" else "${templates.size} templates"
}

/**
 * One line of a [TemplateList]: which template, what it is, and what it takes.
 *
 * ## Example 1: the templates that take no parameter at all
 * ```kt
 * val list = projectArchitecture.process(DescribeTemplates, DescribeTemplates.Args()).getOrThrow()
 * (list as? TemplateList)?.templates.orEmpty().filter { it.parameterNames.isEmpty() }
 * ```
 *
 * @see TemplateList
 */
@ExperimentalKatachiApi
@Serializable
public class TemplateSummary internal constructor(
    /** The complete specifier `--arg template=` accepts for this template: this list's own key. */
    public val template: String,
    /** This template's `id`, or `null` for the one template of a role that declares no other. */
    public val id: String?,
    /**
     * `.template { }`'s own `title`, falling back to [id][TemplateSummary.id], then to the role's
     * `title`.
     */
    public val title: String,
    /** The role this template belongs to, qualified, `.` separated. */
    public val roleName: String,
    /** The role's `summary`, or `null` when it declares none. */
    public val summary: String?,
    /** This template's parameters when previewed, in declaration order. */
    public val parameterNames: List<String>,
    /** This template's named wildcards, which a run also takes as `--arg`. See [TemplateCapturePreview]. */
    public val captures: List<TemplateCapturePreview>,
    /**
     * Whether this template's preview could not be built, such as when a capture and a parameter share a name.
     *
     * The template is still listed and may still run; describing it alone says why.
     */
    public val conflict: Boolean,
) {
    /** `TemplateSummary(<template>)`. */
    override fun toString(): String = "TemplateSummary($template)"
}

/**
 * One template explained: its parameters, and the file it produces with them filled in by
 * stand-in values.
 *
 * ## How the parameters are filled in
 *
 * A String parameter is filled with a placeholder spelling its own name, `${name}`, so the
 * contents show where each value goes. A Boolean, Int or enum parameter cannot hold one, so it is
 * given its default, or -- when it has none -- `true`, `0` or its first entry. Which value each
 * parameter took is [TemplateParameterPreview.previewValue].
 *
 * ## Example 1: the path the template would write
 * ```kt
 * import me.tbsten.katachi.processor.process
 * import me.tbsten.katachi.template.DescribeTemplates
 * import me.tbsten.katachi.template.TemplateDetail
 *
 * val detail = projectArchitecture.process(
 *     DescribeTemplates,
 *     DescribeTemplates.Args(template = "data.Repository.repository"),
 * ).getOrThrow()
 * (detail as? TemplateDetail)?.files?.single()?.let { it.path ?: it.fileName }
 * ```
 *
 * @see TemplateParameterPreview
 * @see TemplateFilePreview
 * @see TemplateBranch
 */
@ExperimentalKatachiApi
@Serializable
public class TemplateDetail internal constructor(
    /** The complete specifier `--arg template=` accepts for this template. */
    public val template: String,
    /** This template's `id`, or `null` for the one template of a role that declares no other. */
    public val id: String?,
    /**
     * `.template { }`'s own `title`, falling back to [id][TemplateDetail.id], then to the role's
     * `title`.
     */
    public val title: String,
    /** The role this template belongs to, qualified, `.` separated. */
    public val roleName: String,
    /** The role's `summary`, or `null` when it declares none. */
    public val summary: String?,
    /** The parameters this template declares with the preview's values, in declaration order. */
    public val parameters: List<TemplateParameterPreview>,
    /** The file this template produces with the preview's values; always one entry. */
    public val files: List<TemplateFilePreview>,
    /** The other Boolean and enum values that change this template's content or its parameters. */
    public val branches: List<TemplateBranch>,
    /** A `./gradlew katachiTemplate ...` command that generates this template, ready to paste. */
    public val exampleCommand: String,
    /** This template's named wildcards, which a run also takes as `--arg`. See [TemplateCapturePreview]. */
    public val captures: List<TemplateCapturePreview>,
) : TemplateDescription {
    /** `Template <template>: <n> parameters`. */
    override fun toString(): String = "Template $template: ${parameters.size} parameters"
}

/**
 * One parameter of a [TemplateDetail]: what it accepts, and the value the preview gave it.
 *
 * ## Example 1: the parameters `katachiTemplate` has to be given
 * ```kt
 * val detail = projectArchitecture.process(
 *     DescribeTemplates,
 *     DescribeTemplates.Args(template = "data.Repository.repository"),
 * ).getOrThrow()
 * (detail as? TemplateDetail)?.parameters.orEmpty().filter { it.isRequired }.map { it.name }
 * ```
 *
 * @see TemplateDetail
 */
@ExperimentalKatachiApi
@Serializable
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
    /** Why the preview chose [previewValue][TemplateParameterPreview.previewValue]. */
    public val previewValueSource: PreviewValueSource,
) {
    /** `TemplateParameterPreview(<name>: <type> = <previewValue>)`. */
    override fun toString(): String = "TemplateParameterPreview($name: $typeName = $previewValue)"
}

/**
 * One named wildcard of a template, as a run takes it: a `capture("...")` in its declared path.
 *
 * A name used in several places is listed once per place, so that where a value lands can be read
 * from the list. Its preview value, where one appears in [TemplateDetail.files], is `${name}`, as
 * a String parameter's is.
 *
 * ## Example 1: the `--arg` names a run has to add for the directories
 * ```kt
 * val detail = projectArchitecture.process(
 *     DescribeTemplates,
 *     DescribeTemplates.Args(template = "feature.Screen"),
 * ).getOrThrow()
 * (detail as? TemplateDetail)?.captures.orEmpty().map { it.name }.distinct()
 * ```
 *
 * @see TemplateDetail
 * @see TemplateSummary
 */
@ExperimentalKatachiApi
@Serializable
public class TemplateCapturePreview internal constructor(
    /** The name, which is also its `--arg` name. */
    public val name: String,
    /** What kind of wildcard it names. */
    public val kind: TemplateCaptureKind,
    /**
     * Where it sits: the file pattern, with a `*` standing for it.
     */
    public val pattern: String,
    /**
     * Which `/`-separated level of [pattern][TemplateCapturePreview.pattern] it is, 0-based.
     */
    public val position: Int,
    /**
     * The pattern of the segment this capture sits in, such as `${fileName}Screen.kt`.
     *
     * `${name}` stands for each plain capture, and `<name.transform>` for each derived one.
     */
    public val segment: String,
    /** Which `*` of its level this occurrence is, 0-based, for a [TemplateCaptureKind.PathCapture]. */
    public val wildcardIndex: Int = 0,
    /**
     * The naming conversion of a derived occurrence, such as `"pascalCase"`; `null` for a plain one.
     *
     * A derived occurrence takes no `--arg` of its own: its value comes from the plain capture of the same [name][TemplateCapturePreview.name].
     */
    public val transform: String? = null,
) {
    /** `TemplateCapturePreview(<name> at <pattern>[<position>])`. */
    override fun toString(): String = "TemplateCapturePreview($name at $pattern[$position])"
}

/**
 * Which kind of named wildcard a [TemplateCapturePreview] is.
 *
 * `capture("...")` is the only kind for now. The enum stays so that the JSON keeps its `kind` field and a later kind can be added.
 *
 * ## Example 1: the captures that name a directory level
 * ```kt
 * detail.captures.filter { it.kind == TemplateCaptureKind.PathCapture }
 * ```
 */
@ExperimentalKatachiApi
@Serializable
public enum class TemplateCaptureKind {
    /** `capture("...")`: a directory level, which a run may create. */
    PathCapture,
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
@Serializable
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
@Serializable
public enum class PreviewValueSource {
    /** A String parameter, filled with `${name}` to show where its value goes. */
    Placeholder,

    /** A Boolean, Int or enum parameter, filled with its declared default. */
    Default,

    /** A Boolean, Int or enum parameter without a default: `true`, `0`, or the first entry. */
    StandIn,
}

/**
 * The one file of a [TemplateDetail]: where it would land, and what it would hold.
 *
 * ## Example 1: whether the layout decides this template's path
 * ```kt
 * detail.files.single().path != null
 * ```
 *
 * @see TemplateDetail
 */
@ExperimentalKatachiApi
@Serializable
public class TemplateFilePreview internal constructor(
    /** The declared pattern, `/` separated, with every capture written as `${name}`. */
    public val pattern: String,
    /** The file name, with the preview's values filled in: `${name}Repository.kt`. */
    public val fileName: String,
    /**
     * Where the file would land, relative to the project root.
     *
     * A directory `capture("...")` names is shown as `${name}`.
     */
    public val path: String,
    /** This template's captures that appear on [pattern][TemplateFilePreview.pattern], by name. */
    public val captures: List<String>,
    /** This template's parameters, by name. */
    public val parameters: List<String>,
    /** The contents, with the preview's values filled in. */
    public val content: String,
) {
    /** `TemplateFilePreview(<path>)`. */
    override fun toString(): String = "TemplateFilePreview($path)"
}

/**
 * Another value of one Boolean or enum parameter, and how it changes this template's content or
 * the parameters it declares.
 *
 * Found by changing that one parameter and keeping the preview's values for the rest. A value
 * that changes neither is not a branch.
 *
 * ## Example 1: what `--arg withImpl=false` adds or drops
 * ```kt
 * detail.branches.filter { it.parameterName == "withImpl" && it.value == "false" }.flatMap { it.addedParameters }
 * ```
 *
 * @see TemplateDetail
 */
@ExperimentalKatachiApi
@Serializable
public class TemplateBranch internal constructor(
    /** The parameter whose value was changed. */
    public val parameterName: String,
    /** The value it was changed to, as `--arg` spells it. */
    public val value: String,
    /**
     * File names produced with [value][TemplateBranch.value] and not with the preview's value.
     *
     * Usually empty, since a template produces one file.
     */
    public val addedFiles: List<String>,
    /**
     * The mirror image of [addedFiles][TemplateBranch.addedFiles]: file names produced with the
     * preview's value and not with [value][TemplateBranch.value].
     */
    public val removedFiles: List<String>,
    /**
     * Parameters declared with [value][TemplateBranch.value] and not with the preview's value.
     *
     * Such as one written inside `if (withImpl) { }`, previewed the way [TemplateDetail.parameters] are.
     */
    public val addedParameters: List<TemplateParameterPreview>,
    /**
     * Names of the parameters declared with the preview's value and not with
     * [value][TemplateBranch.value].
     */
    public val removedParameters: List<String>,
) {
    /** `TemplateBranch(<parameterName>=<value>)`. */
    override fun toString(): String = "TemplateBranch($parameterName=$value)"
}
