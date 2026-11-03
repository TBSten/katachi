package me.tbsten.katachi.template.internal

import me.tbsten.katachi.dsl.Summary
import me.tbsten.katachi.dsl.Title
import me.tbsten.katachi.dsl.internal.DeclaredTemplateParameter
import me.tbsten.katachi.dsl.internal.LayoutCaptures
import me.tbsten.katachi.dsl.internal.fillCapturedPath
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

// Replays stop at MAX_REPLAYS so a template whose parameters never settle cannot loop.
private const val MAX_REPLAYS: Int = 16

internal class PreviewValues(
    val parameters: List<DeclaredTemplateParameter>,
    val values: Map<String, String>,
)

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

internal fun titleOf(template: DeclaredTemplate): String =
    template.title ?: template.id ?: template.role[Title] ?: template.role.qualifiedName

internal fun previewValuesOf(
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

// Each Boolean or enum value is one branch and combinations multiply, so the walk stops at MAX_BRANCH_RUNS.
private const val MAX_BRANCH_RUNS: Int = 64

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

internal fun previewValueOf(parameter: DeclaredTemplateParameter): Pair<String, PreviewValueSource> {
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

internal fun parameterPreviewOf(parameter: DeclaredTemplateParameter): TemplateParameterPreview {
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

internal fun filePreviewOf(
    template: DeclaredTemplate,
    preview: PreviewValues,
    content: String,
    captureValues: Map<String, String> = emptyMap(),
): TemplateFilePreview {
    val entry = template.entries.first()
    // See TemplateGeneration.templateFileFor's identical read: `templateCaptures`, not
    // `.captureVariants.firstOrNull()`, is what is guaranteed to be this template's own variant
    // when this path folded two declarations into one entry.
    val variant = entry.templateCaptures ?: entry.captureVariants.firstOrNull() ?: LayoutCaptures.NONE
    val pattern = fillCapturedPath(entry.path, variant, variant.names.associateWith(::placeholderOf), ::placeholderOf)
    val path = when {
        variant.moduleCapture != null -> null
        captureValues.isEmpty() -> pattern
        // A plain occurrence reads the value a template set's `arg` gave it; a derived one is
        // still the placeholder, since katachi derives it from that value.
        else -> fillCapturedPath(entry.path, variant, emptyMap()) { capture ->
            if (capture.transform == null) captureValues[capture.name] ?: placeholderOf(capture) else placeholderOf(capture)
        }
    }
    return TemplateFilePreview(
        pattern = pattern,
        fileName = pattern.substringAfterLast('/'),
        path = path,
        captures = variant.names,
        parameters = preview.parameters.map { it.name },
        content = content,
    )
}

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
