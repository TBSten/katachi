package me.tbsten.katachi.template

import kotlinx.serialization.Serializable
import me.tbsten.katachi.ExperimentalKatachiApi

/**
 * One line of a [TemplateList] for a template set: which set, what it gathers, and what it takes.
 *
 * ## Example 1: the sets whose preview failed
 * ```kt
 * import me.tbsten.katachi.processor.process
 * import me.tbsten.katachi.template.DescribeTemplates
 * import me.tbsten.katachi.template.TemplateList
 *
 * val list = projectArchitecture.process(DescribeTemplates, DescribeTemplates.Args()).getOrThrow()
 * (list as? TemplateList)?.templateSets.orEmpty().filter { it.conflict }.map { it.templateSet }
 * ```
 *
 * @see TemplateSetDetail
 */
@ExperimentalKatachiApi
@Serializable
public class TemplateSetSummary internal constructor(
    /** The complete specifier `--arg template=` accepts for this set: the group's path, then [name]. */
    public val templateSet: String,
    /** The name the set was declared with. */
    public val name: String,
    /** The set's `title`, falling back to [name]. */
    public val title: String,
    /** The set's `summary`, or `null` when it declares none. */
    public val summary: String?,
    /** The qualified name of the group the set was declared in, or `null` for one declared at the root. */
    public val groupName: String?,
    /** The set's own parameters, in declaration order. */
    public val parameterNames: List<String>,
    /** The complete specifier of every member, as it resolved, in the order they were written. */
    public val members: List<String>,
    /**
     * Whether the set's preview could not be built, so that it has no [TemplateSetDetail].
     *
     * The set is still listed; describing it alone with `--arg template=<set>`, or a run, says what is
     * wrong.
     */
    public val conflict: Boolean,
) {
    /** `TemplateSetSummary(<templateSet>)`. */
    override fun toString(): String = "TemplateSetSummary($templateSet)"
}

/**
 * One template set explained: its parameters, and for each member the values it is given and the
 * file it would produce.
 *
 * The set's parameters are previewed as a template's are (see [TemplateDetail]); each member is
 * previewed with the values its `arg(...)` calls pass, the rest filled in as a template's own
 * preview does.
 *
 * ## Example 1: the files a set would write
 * ```kt
 * import me.tbsten.katachi.processor.process
 * import me.tbsten.katachi.template.DescribeTemplates
 * import me.tbsten.katachi.template.TemplateSetDetail
 *
 * val detail = projectArchitecture.process(
 *     DescribeTemplates,
 *     DescribeTemplates.Args(template = "feature.NewFeature"),
 * ).getOrThrow()
 * (detail as? TemplateSetDetail)?.members.orEmpty().flatMap { it.files }.map { it.path ?: it.fileName }
 * ```
 *
 * @see TemplateSetMemberPreview
 */
@ExperimentalKatachiApi
@Serializable
public class TemplateSetDetail internal constructor(
    /** The complete specifier `--arg template=` accepts for this set. */
    public val templateSet: String,
    /** The name the set was declared with. */
    public val name: String,
    /** The set's `title`, falling back to [name]. */
    public val title: String,
    /** The set's `summary`, or `null` when it declares none. */
    public val summary: String?,
    /** The qualified name of the group the set was declared in, or `null` for one declared at the root. */
    public val groupName: String?,
    /** The set's own parameters, previewed as a template's are. */
    public val parameters: List<TemplateParameterPreview>,
    /** The members, in the order they were written. */
    public val members: List<TemplateSetMemberPreview>,
    /** A `./gradlew katachiTemplate ...` command that generates this set, ready to paste. */
    public val exampleCommand: String,
) : TemplateDescription {
    /** `Template set <templateSet>: <n> members`. */
    override fun toString(): String = "Template set $templateSet: ${members.size} members"
}

/**
 * One member of a [TemplateSetDetail]: the template, what the set passes it, and the file that
 * produces.
 *
 * ## Example 1: the names the set passes a member
 * ```kt
 * detail.members.flatMap { member -> member.args.map { it.name } }
 * ```
 *
 * @see TemplateSetArgPreview
 */
@ExperimentalKatachiApi
@Serializable
public class TemplateSetMemberPreview internal constructor(
    /** The complete specifier of the member's template. */
    public val template: String,
    /** The member template's title, as [TemplateDetail.title] reads it. */
    public val title: String,
    /** What the member's `arg(...)` calls pass, in the order they were written. */
    public val args: List<TemplateSetArgPreview>,
    /** The file the member would produce; always one entry. */
    public val files: List<TemplateFilePreview>,
) {
    /** `TemplateSetMemberPreview(<template>)`. */
    override fun toString(): String = "TemplateSetMemberPreview($template)"
}

/**
 * One `arg(...)` of a member, previewed.
 *
 * ## Example 1: the members that read a parameter of the set
 * ```kt
 * detail.members.filter { member -> member.args.any { it.parameter == "name" } }
 * ```
 *
 * @see TemplateSetMemberPreview
 */
@ExperimentalKatachiApi
@Serializable
public class TemplateSetArgPreview internal constructor(
    /** The name on the member's side. */
    public val name: String,
    /** Whether [name] is a capture or a parameter of the member's template. */
    public val kind: TemplateSetArgKind,
    /** The value the preview gave it: `${p}` for a String parameter `p` of the set, passed as it is. */
    public val previewValue: String,
    /**
     * The name of the set's parameter this value is, when the value is exactly that one parameter
     * read as it is; `null` for a constant or an expression.
     */
    public val parameter: String?,
) {
    /** `TemplateSetArgPreview(<name> = <previewValue>)`. */
    override fun toString(): String = "TemplateSetArgPreview($name = $previewValue)"
}

/**
 * Whether a [TemplateSetArgPreview] names a capture or a parameter of the member.
 *
 * ## Example 1: the args that choose a directory
 * ```kt
 * member.args.filter { it.kind == TemplateSetArgKind.Capture }
 * ```
 */
@ExperimentalKatachiApi
@Serializable
public enum class TemplateSetArgKind {
    /** A `capture("...")` of the member's layout. */
    Capture,

    /** A parameter the member's template declares. */
    Parameter,
}
