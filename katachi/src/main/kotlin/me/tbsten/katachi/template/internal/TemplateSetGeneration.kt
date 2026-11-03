package me.tbsten.katachi.template.internal

import me.tbsten.katachi.dsl.KatachiInvalidTemplateParameterValueException
import me.tbsten.katachi.dsl.KatachiMissingTemplateParameterException
import me.tbsten.katachi.dsl.internal.InvalidTemplateValue
import me.tbsten.katachi.dsl.internal.ModuleIndex
import me.tbsten.katachi.dsl.internal.TemplateSetDeclaration
import me.tbsten.katachi.dsl.internal.TemplateSetMemberReplay
import me.tbsten.katachi.dsl.internal.TemplateSetMode
import me.tbsten.katachi.dsl.internal.replayTemplateSet
import me.tbsten.katachi.processor.ArchitectureProcessContext
import me.tbsten.katachi.template.KatachiDuplicateTemplateOutputException
import me.tbsten.katachi.template.KatachiInvalidTemplateSetArgException
import me.tbsten.katachi.template.KatachiInvalidTemplateSetArgException.Problem
import me.tbsten.katachi.template.KatachiMissingTemplateCaptureException
import me.tbsten.katachi.template.KatachiTemplateSetParameterValueException

internal fun templateSetFilesFor(
    context: ArchitectureProcessContext<*>,
    set: TemplateSetDeclaration,
    table: List<DeclaredTemplate>,
    values: Map<String, String>,
    modules: () -> ModuleIndex,
): Map<String, String> {
    val replay = replayTemplateSet(set, TemplateSetMode.Run(values))
    requireSetParametersUsable(set, values, replay.missing, replay.invalid.values.associate { it.name to it })

    val files = linkedMapOf<String, String>()
    val ownerOf = linkedMapOf<String, String>()
    for (member in replay.members) {
        val template = resolveTemplate(member.specifier, table)
        val memberValues = member.args
        requireMemberArgNamesKnown(set, member, template)
        requireNoConflicts(listOf(template), memberValues)
        val (path, content) = memberFile(context, set, member, template, modules)
        ownerOf[path]?.let { owner ->
            throw KatachiDuplicateTemplateOutputException(
                path = path,
                templates = listOf(owner, template.specifier).sorted(),
            )
        }
        files[path] = content
        ownerOf[path] = template.specifier
    }
    return files
}

private fun requireSetParametersUsable(
    set: TemplateSetDeclaration,
    values: Map<String, String>,
    read: List<String>,
    invalid: Map<String, InvalidTemplateValue>,
) {
    val required = set.parameters.filter { it.default == null && it.name !in values }.map { it.name }
    val missing = (required + read).distinct().filter { it !in invalid }.sorted()
    if (missing.isEmpty() && invalid.isEmpty()) return
    val accepted = (missing + invalid.keys).mapNotNull { name ->
        set.parameters.firstOrNull { it.name == name }?.type?.acceptedDescription?.let { name to it }
    }.toMap()
    throw KatachiTemplateSetParameterValueException(
        templateSet = set.specifier,
        missing = missing,
        invalid = invalid.mapValues { it.value.raw },
        accepted = accepted,
        declaredAt = set.declaredAt,
    )
}

internal fun acceptedArgNamesOf(template: DeclaredTemplate, member: TemplateSetMemberReplay): List<String> {
    val captureNames = captureNamesOf(template.entries)
    val parameterNames = parameterOriginsOnEveryBranch(
        template.template,
        template.role.qualifiedName,
        captureNames,
        member.args,
    ).keys
    return (captureNames + parameterNames).sorted()
}

internal fun requireMemberArgNamesKnown(
    set: TemplateSetDeclaration,
    member: TemplateSetMemberReplay,
    template: DeclaredTemplate,
) {
    val accepted = acceptedArgNamesOf(template, member)
    val unknown = member.args.keys.filter { it !in accepted }.sorted()
    if (unknown.isEmpty()) return
    throw KatachiInvalidTemplateSetArgException(
        templateSet = set.specifier,
        member = member.specifier,
        problem = Problem.UnknownName,
        names = unknown,
        accepted = accepted,
        declaredAt = member.declaredAt,
        templateSetDeclaredAt = set.declaredAt,
    )
}

private fun memberFile(
    context: ArchitectureProcessContext<*>,
    set: TemplateSetDeclaration,
    member: TemplateSetMemberReplay,
    template: DeclaredTemplate,
    modules: () -> ModuleIndex,
): Pair<String, String> =
    asMemberArgProblem(set, member, template) { templateFileFor(context, template, member.args, modules) }

internal fun <T> asMemberArgProblem(
    set: TemplateSetDeclaration,
    member: TemplateSetMemberReplay,
    template: DeclaredTemplate,
    block: () -> T,
): T {
    fun argProblem(
        problem: Problem,
        names: List<String>,
        cause: Throwable,
        values: Map<String, String> = emptyMap(),
        acceptedValues: Map<String, String> = emptyMap(),
        alsoMissing: List<String> = emptyList(),
    ): KatachiInvalidTemplateSetArgException {
        val accepted = acceptedArgNamesOf(template, member)
        return KatachiInvalidTemplateSetArgException(
            templateSet = set.specifier,
            member = member.specifier,
            problem = problem,
            names = names,
            accepted = accepted,
            declaredAt = member.declaredAt,
            templateSetDeclaredAt = set.declaredAt,
            values = values,
            acceptedValues = acceptedValues,
            alsoMissing = alsoMissing,
            cause = cause,
        )
    }
    return try {
        block()
    } catch (e: KatachiMissingTemplateParameterException) {
        throw argProblem(Problem.Missing, (e.names + e.missingCaptures).distinct().sorted(), e)
    } catch (e: KatachiInvalidTemplateParameterValueException) {
        throw argProblem(
            Problem.InvalidValue,
            e.names,
            e,
            values = e.problems.associate { it.name to it.raw },
            acceptedValues = e.problems.mapNotNull { p -> p.type.acceptedDescription?.let { p.name to it } }.toMap(),
            alsoMissing = e.missing,
        )
    } catch (e: KatachiMissingTemplateCaptureException) {
        throw argProblem(Problem.Missing, e.names, e)
    }
}
