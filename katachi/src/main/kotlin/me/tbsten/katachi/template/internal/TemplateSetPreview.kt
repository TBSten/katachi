package me.tbsten.katachi.template.internal

import me.tbsten.katachi.dsl.internal.DeclaredTemplateParameter
import me.tbsten.katachi.dsl.internal.ParsedArgValue
import me.tbsten.katachi.dsl.internal.TemplateParameterType
import me.tbsten.katachi.dsl.internal.TemplateSetDeclaration
import me.tbsten.katachi.dsl.internal.TemplateSetMemberReplay
import me.tbsten.katachi.dsl.internal.TemplateSetMode
import me.tbsten.katachi.dsl.internal.evaluateTemplate
import me.tbsten.katachi.dsl.internal.replayTemplateSet
import me.tbsten.katachi.internal.catching
import me.tbsten.katachi.template.KatachiInvalidTemplateSetArgException
import me.tbsten.katachi.template.KatachiInvalidTemplateSetArgException.Problem
import me.tbsten.katachi.template.TemplateSetArgKind
import me.tbsten.katachi.template.TemplateSetArgPreview
import me.tbsten.katachi.template.TemplateSetDetail
import me.tbsten.katachi.template.TemplateSetMemberPreview
import me.tbsten.katachi.template.TemplateSetSummary

/**
 * [set] as one line of the list.
 *
 * Never fails over one set: a set whose detail cannot be built is listed with
 * [TemplateSetSummary.conflict] `true`, so that one broken declaration does not cost the list --
 * or the IDE, which reads the same list -- every other set and template. [templateSetDetailOf] and
 * a run of the set say what is wrong.
 */
internal fun templateSetSummaryOf(set: TemplateSetDeclaration, table: List<DeclaredTemplate>): TemplateSetSummary =
    TemplateSetSummary(
        templateSet = set.specifier,
        name = set.name,
        title = set.title ?: set.name,
        summary = set.summary,
        groupName = groupNameOf(set),
        parameterNames = set.parameters.map { it.name },
        members = set.members.map { member ->
            catching { resolveTemplate(member.specifier, table).specifier }.getOrNull() ?: member.specifier
        },
        conflict = catching { templateSetDetailOf(set, table) }.isFailure,
    )

/**
 * [set] explained: its parameters, and each member previewed with the values the set passes it.
 *
 * The set's own parameters take the values a template's do (a String its placeholder, the others
 * their default or a stand-in). The block is replayed with them, which yields the `arg` table of
 * every member; each member is then previewed with exactly those values, and everything it was
 * not given is filled in as a template's own preview fills it. Nothing is read off disk.
 *
 * @throws me.tbsten.katachi.KatachiDeclarationException when the set cannot be previewed: a member
 *   given a name it does not take or none for a value it needs, or a member whose own preview fails.
 */
internal fun templateSetDetailOf(set: TemplateSetDeclaration, table: List<DeclaredTemplate>): TemplateSetDetail {
    val values = set.parameters.associate { it.name to previewValueOf(it).first }
    val replay = replayTemplateSet(set, TemplateSetMode.Preview(values))
    val passes = PassThroughProbe(set, values, replay.members)
    val members = replay.members.mapIndexed { index, member ->
        memberPreviewOf(set, member, table) { name, value -> passes.parameterOf(index, name, value) }
    }
    return TemplateSetDetail(
        templateSet = set.specifier,
        name = set.name,
        title = set.title ?: set.name,
        summary = set.summary,
        groupName = groupNameOf(set),
        parameters = set.parameters.map(::parameterPreviewOf),
        members = members,
        exampleCommand = exampleCommandOf(set, members),
    )
}

private fun groupNameOf(set: TemplateSetDeclaration): String? = set.groupPath.takeIf { it.isNotEmpty() }?.joinToString(".")

private fun memberPreviewOf(
    set: TemplateSetDeclaration,
    member: TemplateSetMemberReplay,
    table: List<DeclaredTemplate>,
    parameterOf: (name: String, value: String) -> String?,
): TemplateSetMemberPreview {
    val template = resolveTemplate(member.specifier, table)
    requireMemberArgNamesKnown(set, member, template)
    val captureNames = captureNamesOf(template.entries)
    val preview = previewValuesOf(template.template, template.role.qualifiedName, captureNames, fixed = member.args)
    fun evaluate(): String =
        asMemberArgProblem(set, member, template) {
            evaluateTemplate(template.template, template.role.qualifiedName, preview.values, captureNames, isPreview = true)
        }
    // A value that cannot be read is told first, as a run does: it may be what decides the branch
    // another missing value belongs to.
    if (preview.parameters.any { it.name in member.args && it.type.parse(member.args.getValue(it.name)) is ParsedArgValue.Invalid }) {
        evaluate()
    }
    requireMemberValuesGiven(set, member, template, captureNames, preview)
    requireNoConflicts(listOf(template), preview.values)
    val content = evaluate()
    return TemplateSetMemberPreview(
        template = template.specifier,
        title = titleOf(template),
        args = member.args.map { (name, value) ->
            TemplateSetArgPreview(
                name = name,
                kind = if (name in captureNames) TemplateSetArgKind.Capture else TemplateSetArgKind.Parameter,
                previewValue = value,
                parameter = parameterOf(name, value),
            )
        },
        files = listOf(filePreviewOf(template, preview, content, member.args.filterKeys { it in captureNames })),
    )
}

/**
 * Refuses a member the set gives no value for a capture, or for a parameter that has no default.
 *
 * A run would stop on the same name, so the preview says it too rather than showing a placeholder
 * for a value nobody passes.
 */
private fun requireMemberValuesGiven(
    set: TemplateSetDeclaration,
    member: TemplateSetMemberReplay,
    template: DeclaredTemplate,
    captureNames: Set<String>,
    preview: PreviewValues,
) {
    val required = captureNames + preview.parameters.filter { it.default == null }.map { it.name }
    val missing = required.filter { it !in member.args }.sorted()
    if (missing.isEmpty()) return
    throw KatachiInvalidTemplateSetArgException(
        templateSet = set.specifier,
        member = member.specifier,
        problem = Problem.Missing,
        names = missing,
        accepted = acceptedArgNamesOf(template, member),
        declaredAt = member.declaredAt,
        templateSetDeclaredAt = set.declaredAt,
    )
}

/**
 * Tells which `arg` values are exactly one parameter of the set read as it is.
 *
 * A String parameter previews as `${p}`, so an `arg` holding exactly that is `p`. The other types
 * have no placeholder, so each is told by a second replay with that one parameter's value changed:
 * an `arg` that held `p`'s value before and holds the changed one now is `p`. A constant stays
 * the same, and `!p` or `p + 1` ends up somewhere else. A block that reads `p` twice to build one
 * value can still be mistaken for `p`; the answer is a hint for the IDE, not a guarantee.
 */
private class PassThroughProbe(
    private val set: TemplateSetDeclaration,
    private val values: Map<String, String>,
    private val base: List<TemplateSetMemberReplay>,
) {
    private class Changed(val parameter: DeclaredTemplateParameter, val value: String, val members: List<TemplateSetMemberReplay>)

    private val changed: List<Changed> = set.parameters.mapNotNull { parameter ->
        val before = values.getValue(parameter.name)
        val after = alternateOf(parameter, before) ?: return@mapNotNull null
        val members = catching { replayTemplateSet(set, TemplateSetMode.Preview(values + (parameter.name to after))).members }
            .getOrNull() ?: return@mapNotNull null
        Changed(parameter, after, members)
    }

    fun parameterOf(memberIndex: Int, name: String, value: String): String? {
        set.parameters.firstOrNull { it.type == TemplateParameterType.StringType && placeholderOf(it.name) == value }
            ?.let { return it.name }
        return changed.firstOrNull { probe ->
            values[probe.parameter.name] == value &&
                base.getOrNull(memberIndex)?.args?.get(name) == value &&
                probe.members.getOrNull(memberIndex)?.args?.get(name) == probe.value
        }?.parameter?.name
    }
}

/** Another value [parameter] accepts, spelt as `--arg` would, or `null` when it has no second one to try. */
private fun alternateOf(parameter: DeclaredTemplateParameter, current: String): String? = when (val type = parameter.type) {
    TemplateParameterType.StringType -> null
    TemplateParameterType.BooleanType -> (!current.toBoolean()).toString()
    TemplateParameterType.IntType -> current.toIntOrNull()?.plus(1)?.toString()
    is TemplateParameterType.EnumType<*> -> {
        val entries = type.acceptedValues
        entries.getOrNull((entries.indexOf(current) + 1) % entries.size)?.takeIf { it != current }
    }
}

/**
 * A `katachiTemplate` command for [set] with every required parameter given a value.
 *
 * A parameter that reaches a capture of some member as it is gets the `<name>` slot, as a
 * template's own capture does: its value is a directory of the user's own project, which no word
 * the preview could make up would be. A String parameter otherwise gets its name with the first
 * letter upper-cased, and any other type its preview value.
 */
private fun exampleCommandOf(set: TemplateSetDeclaration, members: List<TemplateSetMemberPreview>): String {
    val slots = members.flatMap { it.args }.filter { it.kind == TemplateSetArgKind.Capture }.mapNotNull { it.parameter }.toSet()
    return buildString {
        append("./gradlew katachiTemplate --arg template=").append(set.specifier)
        for (parameter in set.parameters) {
            if (parameter.default != null) continue
            val value = when {
                parameter.name in slots -> "<${parameter.name}>"
                parameter.type == TemplateParameterType.StringType -> parameter.name.replaceFirstChar(Char::uppercaseChar)
                else -> previewValueOf(parameter).first
            }
            append(" --arg ").append(parameter.name).append('=').append(value)
        }
    }
}
