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

private fun alternateOf(parameter: DeclaredTemplateParameter, current: String): String? = when (val type = parameter.type) {
    TemplateParameterType.StringType -> null
    TemplateParameterType.BooleanType -> (!current.toBoolean()).toString()
    TemplateParameterType.IntType -> current.toIntOrNull()?.plus(1)?.toString()
    is TemplateParameterType.EnumType<*> -> {
        val entries = type.acceptedValues
        entries.getOrNull((entries.indexOf(current) + 1) % entries.size)?.takeIf { it != current }
    }
}

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
