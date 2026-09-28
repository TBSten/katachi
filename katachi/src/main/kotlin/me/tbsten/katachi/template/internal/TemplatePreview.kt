package me.tbsten.katachi.template.internal

import me.tbsten.katachi.dsl.Summary
import me.tbsten.katachi.dsl.Title
import me.tbsten.katachi.dsl.internal.DeclaredTemplateParameter
import me.tbsten.katachi.dsl.internal.LayoutCaptures
import me.tbsten.katachi.dsl.internal.LayoutTemplate
import me.tbsten.katachi.dsl.internal.TemplateParameterOrigin
import me.tbsten.katachi.dsl.internal.TemplateParameterType
import me.tbsten.katachi.dsl.internal.declaredTemplateParameters
import me.tbsten.katachi.dsl.internal.evaluateTemplate
import me.tbsten.katachi.dsl.internal.templateParameterNames
import me.tbsten.katachi.internal.catching
import me.tbsten.katachi.template.PreviewValueSource
import me.tbsten.katachi.template.TemplateBranch
import me.tbsten.katachi.template.TemplateDetail
import me.tbsten.katachi.template.TemplateFilePreview
import me.tbsten.katachi.template.TemplateParameterKind
import me.tbsten.katachi.template.TemplateParameterPreview
import me.tbsten.katachi.template.TemplateSummary

/**
 * How many times the parameters are replayed before the preview settles for what it has.
 *
 * A value can declare more parameters (`if (withImpl) { val implName by ... }`), and those need
 * values of their own, so the replay runs until the set stops changing. Real templates settle in
 * two; the bound only keeps a template that flips between branches from looping.
 */
private const val MAX_REPLAYS: Int = 16

/** The values a preview runs a template with, and the parameters those values declare. */
internal class PreviewValues(
    val parameters: List<DeclaredTemplateParameter>,
    val values: Map<String, String>,
)

/**
 * [template] as one line of the list. [DeclaredTemplate.entries] is where the captures are read
 * from.
 *
 * Never fails over one template: a capture and a parameter sharing a name leave
 * [TemplateSummary.conflict] `true`, as any other template that cannot be previewed does, so that
 * one broken declaration does not cost the list -- or the IDE, which reads the same list -- every
 * other template. [templateDetailOf] and a run of the template say what is wrong.
 */
internal fun templateSummaryOf(template: DeclaredTemplate): TemplateSummary {
    val captureNames = captureNamesOf(template.entries)
    val preview = previewValuesOf(template.template, template.role.qualifiedName, captureNames)
    val conflict = catching {
        requireNoConflicts(listOf(template), preview.values)
        evaluateTemplate(template.template, template.role.qualifiedName, preview.values, captureNames, isPreview = true)
    }.isFailure
    return TemplateSummary(
        template = template.specifier,
        id = template.id,
        title = titleOf(template),
        roleName = template.role.qualifiedName,
        summary = template.role[Summary],
        parameterNames = preview.parameters.map { it.name },
        captures = capturePreviewsOf(template.entries),
        conflict = conflict,
    )
}

/**
 * [template] explained: its parameters, and the file it produces rendered with stand-in values.
 *
 * Reads nothing off disk: the path comes from [DeclaredTemplate.entries], the declared layout.
 */
internal fun templateDetailOf(template: DeclaredTemplate): TemplateDetail {
    val captureNames = captureNamesOf(template.entries)
    val preview = previewValuesOf(template.template, template.role.qualifiedName, captureNames)
    requireNoConflicts(listOf(template), preview.values)
    val content = evaluateTemplate(template.template, template.role.qualifiedName, preview.values, captureNames, isPreview = true)
    return TemplateDetail(
        template = template.specifier,
        id = template.id,
        title = titleOf(template),
        roleName = template.role.qualifiedName,
        summary = template.role[Summary],
        parameters = preview.parameters.map(::parameterPreviewOf),
        files = listOf(filePreviewOf(template, preview, content)),
        branches = branchesOf(template, captureNames, preview, content),
        exampleCommand = exampleCommandOf(template, captureNames, preview.parameters),
        captures = capturePreviewsOf(template.entries),
    )
}

/** `.template { }`'s own `title`, falling back to [DeclaredTemplate.id], then to the role's `title`. */
private fun titleOf(template: DeclaredTemplate): String =
    template.title ?: template.id ?: template.role[Title] ?: template.role.qualifiedName

/**
 * The values to preview [template] with, replayed until the parameters they declare settle.
 *
 * [fixed] wins over the stand-in of the parameter it names, which is how a branch is explored.
 * Every capture in [captureNames] reads as its placeholder, `${name}`, as a String parameter does.
 */
private fun previewValuesOf(
    template: LayoutTemplate,
    roleName: String,
    captureNames: Set<String>,
    fixed: Map<String, String> = emptyMap(),
): PreviewValues {
    val base = captureNames.associateWith(::placeholderOf) + fixed
    var values = base
    var parameters = declaredTemplateParameters(template, roleName, values, captureNames, isPreview = true)
    repeat(MAX_REPLAYS) {
        val next = LinkedHashMap(base)
        for (parameter in parameters) {
            if (parameter.name !in next) next[parameter.name] = previewValueOf(parameter).first
        }
        if (next == values) return PreviewValues(parameters, values)
        values = next
        parameters = declaredTemplateParameters(template, roleName, values, captureNames, isPreview = true)
    }
    return PreviewValues(parameters, values)
}

/**
 * How many runs [parameterOriginsOnEveryBranch] spends looking for branches. Each Boolean or
 * enum value is one branch and combinations multiply, so the walk stops here; real templates
 * have a handful of switches and are covered long before.
 */
private const val MAX_BRANCH_RUNS: Int = 64

/**
 * Every parameter [template] declares on the branches a preview can reach, with where each was
 * declared, together with those [values] declare.
 *
 * Breadth first from the preview's own values: every other value of each Boolean or enum
 * parameter, then every other value of the parameters *those* runs declare, and so on, until
 * nothing is left or [MAX_BRANCH_RUNS] runs were spent. A branch taken on an Int or String value
 * cannot be enumerated, which is why [values] -- the run's own -- are replayed as well. Like the
 * replays it is made of, this never throws.
 */
internal fun parameterOriginsOnEveryBranch(
    template: LayoutTemplate,
    roleName: String,
    captureNames: Set<String>,
    values: Map<String, String>,
): Map<String, TemplateParameterOrigin> {
    val origins = LinkedHashMap<String, TemplateParameterOrigin>()
    fun collect(replayed: Map<String, String>) {
        val names = templateParameterNames(template, roleName, replayed, captureNames, isPreview = true)
        for ((name, origin) in names.origins) {
            origins.putIfAbsent(name, origin)
        }
    }
    collect(values)
    val seen = HashSet<Map<String, String>>()
    val pending = ArrayDeque<Map<String, String>>().apply { add(emptyMap()) }
    var runs = 0
    while (pending.isNotEmpty() && runs < MAX_BRANCH_RUNS) {
        val fixed = pending.removeFirst()
        if (!seen.add(fixed)) continue
        runs++
        val preview = previewValuesOf(template, roleName, captureNames, fixed)
        collect(preview.values)
        for (parameter in preview.parameters) {
            if (parameter.name in fixed || parameter.name in captureNames) continue
            for (value in parameter.type.acceptedValues) {
                if (value != preview.values[parameter.name]) pending += fixed + (parameter.name to value)
            }
        }
    }
    return origins
}

/** The value a preview fills [parameter] with, spelt as `--arg` would, and why. */
private fun previewValueOf(parameter: DeclaredTemplateParameter): Pair<String, PreviewValueSource> {
    val type = parameter.type
    if (type == TemplateParameterType.StringType) {
        return placeholderOf(parameter.name) to PreviewValueSource.Placeholder
    }
    parameter.default?.let { return argSpellingOf(it) to PreviewValueSource.Default }
    val standIn = when (type) {
        TemplateParameterType.BooleanType -> "true"
        TemplateParameterType.IntType -> "0"
        is TemplateParameterType.EnumType<*> -> type.acceptedValues.first()
        TemplateParameterType.StringType -> placeholderOf(parameter.name)
    }
    return standIn to PreviewValueSource.StandIn
}

private fun parameterPreviewOf(parameter: DeclaredTemplateParameter): TemplateParameterPreview {
    val (value, source) = previewValueOf(parameter)
    return TemplateParameterPreview(
        name = parameter.name,
        kind = kindOf(parameter.type),
        typeName = parameter.type.label,
        default = parameter.default?.let(::argSpellingOf),
        acceptedValues = parameter.type.acceptedValues,
        isRequired = parameter.default == null,
        previewValue = value,
        previewValueSource = source,
    )
}

private fun kindOf(type: TemplateParameterType<*>): TemplateParameterKind = when (type) {
    TemplateParameterType.StringType -> TemplateParameterKind.StringParameter
    TemplateParameterType.BooleanType -> TemplateParameterKind.BooleanParameter
    TemplateParameterType.IntType -> TemplateParameterKind.IntParameter
    is TemplateParameterType.EnumType<*> -> TemplateParameterKind.EnumParameter
}

/**
 * [template]'s one file, previewed: its declared pattern, its path with [preview]'s values filled
 * in, and its [content].
 *
 * A capture reads as `${name}` in the path exactly as it does in the content -- there is no real
 * value to fill a preview's path with, only the same placeholder a String parameter gets. A
 * module capture is the one exception: which module it would pick is a question only the modules
 * that exist could answer, so [TemplateFilePreview.path] is `null` whenever one is present, as
 * [TemplateFilePreview.pattern] (the declared shape, captures included) still is not.
 */
private fun filePreviewOf(template: DeclaredTemplate, preview: PreviewValues, content: String): TemplateFilePreview {
    val entry = template.entries.first()
    // See TemplateGeneration.templateFileFor's identical read: `templateCaptures`, not
    // `.captureVariants.firstOrNull()`, is what is guaranteed to be this template's own variant
    // when this path folded two declarations into one entry.
    val variant = entry.templateCaptures ?: entry.captureVariants.firstOrNull() ?: LayoutCaptures.NONE
    val placeholders = variant.names.associateWith(::placeholderOf)
    val pattern = fillCapturedPath(entry.path, variant, placeholders)
    val path = if (variant.moduleCapture != null) null else pattern
    return TemplateFilePreview(
        pattern = pattern,
        fileName = pattern.substringAfterLast('/'),
        path = path,
        captures = variant.names,
        parameters = preview.parameters.map { it.name },
        content = content,
    )
}

/**
 * Every other value of a Boolean or enum parameter that changes this template's content or the
 * parameters it declares.
 *
 * One parameter at a time, the rest kept at the preview's values: the combinations of several
 * would grow with every parameter, and one at a time already answers "what does this switch do".
 * A value the template cannot render with is left out rather than reported -- the detail is about
 * what the template does, and `katachiTemplate` reports a failure with that value itself.
 *
 * [TemplateBranch.addedFiles] / [TemplateBranch.removedFiles] stay empty: a template describes
 * exactly one file, so no value changes *which* file it is -- only whether it can be rendered at
 * all (in which case the branch is skipped, as it always was) or what it and its parameters are.
 */
private fun branchesOf(
    template: DeclaredTemplate,
    captureNames: Set<String>,
    preview: PreviewValues,
    content: String,
): List<TemplateBranch> {
    val baseParameters = preview.parameters.map { it.name }
    val branches = mutableListOf<TemplateBranch>()
    for (parameter in preview.parameters) {
        val used = preview.values[parameter.name]
        for (value in parameter.type.acceptedValues) {
            if (value == used) continue
            val variant = previewValuesOf(template.template, template.role.qualifiedName, captureNames, fixed = mapOf(parameter.name to value))
            val variantContent = catching {
                evaluateTemplate(template.template, template.role.qualifiedName, variant.values, captureNames, isPreview = true)
            }.getOrNull() ?: continue
            val addedParameters = variant.parameters.filter { it.name !in baseParameters }
            val removedParameters = baseParameters - variant.parameters.map { it.name }.toSet()
            if (variantContent == content && addedParameters.isEmpty() && removedParameters.isEmpty()) {
                continue
            }
            branches += TemplateBranch(
                parameterName = parameter.name,
                value = value,
                addedFiles = emptyList(),
                removedFiles = emptyList(),
                addedParameters = addedParameters.map(::parameterPreviewOf),
                removedParameters = removedParameters,
            )
        }
    }
    return branches
}

/**
 * A `katachiTemplate` command for [template] with every capture and every required parameter
 * given a value -- a String parameter its own name with the first letter upper-cased, and a
 * capture `<name>`.
 *
 * A capture is left as a slot on purpose. Its value is a directory or a module of the user's own
 * project, and no word the preview could make up is one: a command that ran as pasted would
 * generate into a directory nobody asked for. A shell refuses the `<`, so the slot has to be
 * filled before the command runs at all.
 */
private fun exampleCommandOf(
    template: DeclaredTemplate,
    captureNames: Set<String>,
    parameters: List<DeclaredTemplateParameter>,
): String =
    buildString {
        append("./gradlew katachiTemplate --arg template=").append(template.specifier)
        for (name in captureNames) append(" --arg ").append(name).append("=<").append(name).append('>')
        for (parameter in parameters) {
            if (parameter.default != null) continue
            val value = when (parameter.type) {
                TemplateParameterType.StringType -> parameter.name.replaceFirstChar(Char::uppercaseChar)
                else -> previewValueOf(parameter).first
            }
            append(" --arg ").append(parameter.name).append('=').append(value)
        }
    }
