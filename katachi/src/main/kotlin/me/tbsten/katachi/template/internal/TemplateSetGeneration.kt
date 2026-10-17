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

/**
 * What running [set] with [values] would put in the project: path to contents, members in the
 * order they were written.
 *
 * [values] are the set's own parameters. Each member receives only what its `arg(...)` calls
 * passed, so the same template can be a member of two sets, or of a set and a run of its own,
 * without the values of one leaking into the other. All members are built before the caller
 * writes any of them, so a member that fails leaves nothing behind.
 *
 * [table] is every declared template, as [declaredTemplatesOf] lists them.
 */
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

/**
 * Refuses a run that left a parameter of [set] without a value or gave one it cannot read.
 *
 * A parameter with no default is required even when no member reads it, so the check is on the
 * declaration, not only on what the replay happened to read.
 */
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

/**
 * The names [template] accepts as `arg` when [member] gives it its values: its captures, and its
 * parameters on any branch. Sorted.
 */
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

/** Refuses an `arg` name that is neither a capture nor a parameter (on any branch) of [template]. */
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

/**
 * One member's file, with the member's own problems told apart from the rest: see
 * [asMemberArgProblem]. Anything else (a bad capture value, a file that exists) is the member's own
 * and is thrown as it is.
 */
private fun memberFile(
    context: ArchitectureProcessContext<*>,
    set: TemplateSetDeclaration,
    member: TemplateSetMemberReplay,
    template: DeclaredTemplate,
    modules: () -> ModuleIndex,
): Pair<String, String> =
    asMemberArgProblem(set, member, template) { templateFileFor(context, template, member.args, modules) }

/**
 * Runs [block], which evaluates [template] with the values [member] passes it, and turns the
 * failures that are the set's mistake into [KatachiInvalidTemplateSetArgException]: values the
 * member needed and did not get ([Problem.Missing]) and values its parameters could not read
 * ([Problem.InvalidValue]). The exception says where in the set to fix it, with the original as
 * its cause. Shared by a run and a preview, so that both tell the same story.
 */
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
