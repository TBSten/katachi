package me.tbsten.katachi.template.internal

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.Role
import me.tbsten.katachi.dsl.Summary
import me.tbsten.katachi.dsl.Title
import me.tbsten.katachi.dsl.internal.DeclaredTemplateParameter
import me.tbsten.katachi.dsl.internal.TemplateDeclaration
import me.tbsten.katachi.dsl.internal.TemplateEvaluation
import me.tbsten.katachi.dsl.internal.TemplateParameterType
import me.tbsten.katachi.dsl.internal.declaredTemplateParameters
import me.tbsten.katachi.dsl.internal.evaluateTemplate
import me.tbsten.katachi.internal.catching
import me.tbsten.katachi.template.KatachiWildcardTemplatePlacementException
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
private class PreviewValues(
    val parameters: List<DeclaredTemplateParameter>,
    val values: Map<String, String>,
)

/** [role]'s template as one line of the list. */
internal fun templateSummaryOf(role: Role): TemplateSummary {
    val template = templateOf(role)
    val preview = previewValuesOf(template, role.qualifiedName)
    val fileCount = catching { evaluateTemplate(template, role.qualifiedName, preview.values) }
        .getOrNull()?.files?.size
    return TemplateSummary(
        roleName = role.qualifiedName,
        title = role[Title],
        summary = role[Summary],
        parameterNames = preview.parameters.map { it.name },
        fileCount = fileCount,
    )
}

/**
 * [role]'s template explained: its parameters, and its files rendered with stand-in values.
 *
 * Reads nothing off disk: the paths come from [entries], the declared layout.
 */
internal fun templateDetailOf(role: Role, entries: List<LayoutEntry>): TemplateDetail {
    val template = templateOf(role)
    val preview = previewValuesOf(template, role.qualifiedName)
    val evaluation = evaluateTemplate(template, role.qualifiedName, preview.values)
    val roleEntries = entries.filter { it.role === role }
    val stringNames = preview.parameters
        .filter { it.type == TemplateParameterType.StringType }
        .map { it.name }

    return TemplateDetail(
        roleName = role.qualifiedName,
        title = role[Title],
        summary = role[Summary],
        parameters = preview.parameters.map(::parameterPreviewOf),
        files = evaluation.files.map { file ->
            filePreviewOf(role, roleEntries, stringNames, file.fileName, file.content, file.declaredAt)
        },
        branches = branchesOf(template, role.qualifiedName, preview, evaluation),
        exampleCommand = exampleCommandOf(role.qualifiedName, preview.parameters),
    )
}

/**
 * The values to preview [template] with, replayed until the parameters they declare settle.
 *
 * [fixed] wins over the stand-in of the parameter it names, which is how a branch is explored.
 */
private fun previewValuesOf(
    template: TemplateDeclaration,
    roleName: String,
    fixed: Map<String, String> = emptyMap(),
): PreviewValues {
    var values = fixed
    var parameters = declaredTemplateParameters(template, roleName, values)
    repeat(MAX_REPLAYS) {
        val next = LinkedHashMap(fixed)
        for (parameter in parameters) {
            if (parameter.name !in next) next[parameter.name] = previewValueOf(parameter).first
        }
        if (next == values) return PreviewValues(parameters, values)
        values = next
        parameters = declaredTemplateParameters(template, roleName, values)
    }
    return PreviewValues(parameters, values)
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

/** `${name}`: what a String parameter reads as in a preview. */
private fun placeholderOf(name: String): String = "\$" + "{" + name + "}"

/** [value] as `--arg` spells it: an enum entry by its name, anything else by `toString`. */
private fun argSpellingOf(value: Any): String = when (value) {
    is Enum<*> -> value.name
    else -> value.toString()
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
 * Where [fileName] would land, decided by the same [placeTemplateFile] generation uses.
 *
 * The name still holds `${name}`, whose `$`, `{` and `}` generation would refuse as a file name.
 * So the layout is asked about the name with each placeholder spelt as a plain word, and the
 * directory it answers is put in front of the name as the preview spells it.
 */
private fun filePreviewOf(
    role: Role,
    entries: List<LayoutEntry>,
    stringNames: List<String>,
    fileName: String,
    content: String,
    declaredAt: DeclarationSite,
): TemplateFilePreview {
    var plainName = fileName
    for (name in stringNames) {
        plainName = plainName.replace(placeholderOf(name), name.replaceFirstChar(Char::uppercaseChar))
    }
    return try {
        val placed = placeTemplateFile(role, entries, plainName, declaredAt)
        val directory = placed.substringBeforeLast('/', missingDelimiterValue = "")
        TemplateFilePreview(
            fileName = fileName,
            path = if (directory.isEmpty()) fileName else "$directory/$fileName",
            unresolvedPatterns = emptyList(),
            content = content,
        )
    } catch (wildcard: KatachiWildcardTemplatePlacementException) {
        TemplateFilePreview(
            fileName = fileName,
            path = null,
            unresolvedPatterns = wildcard.patterns,
            content = content,
        )
    }
}

/**
 * Every other value of a Boolean or enum parameter that changes which files are produced, or
 * which parameters are declared -- a parameter inside `if (withImpl) { }` comes and goes with it.
 *
 * One parameter at a time, the rest kept at the preview's values: the combinations of several
 * would grow with every parameter, and one at a time already answers "what does this switch do".
 * A value the template cannot render with is left out rather than reported -- the detail is about
 * what the template does, and `katachiTemplate` reports a failure with that value itself.
 */
private fun branchesOf(
    template: TemplateDeclaration,
    roleName: String,
    preview: PreviewValues,
    evaluation: TemplateEvaluation,
): List<TemplateBranch> {
    val baseFiles = evaluation.files.map { it.fileName }
    val baseParameters = preview.parameters.map { it.name }
    val branches = mutableListOf<TemplateBranch>()
    for (parameter in preview.parameters) {
        val used = preview.values[parameter.name]
        for (value in parameter.type.acceptedValues) {
            if (value == used) continue
            val variant = previewValuesOf(template, roleName, fixed = mapOf(parameter.name to value))
            val files = catching { evaluateTemplate(template, roleName, variant.values) }
                .getOrNull()?.files?.map { it.fileName } ?: continue
            val added = files - baseFiles.toSet()
            val removed = baseFiles - files.toSet()
            val addedParameters = variant.parameters.filter { it.name !in baseParameters }
            val removedParameters = baseParameters - variant.parameters.map { it.name }.toSet()
            if (added.isEmpty() && removed.isEmpty() && addedParameters.isEmpty() && removedParameters.isEmpty()) {
                continue
            }
            branches += TemplateBranch(
                parameterName = parameter.name,
                value = value,
                addedFiles = added,
                removedFiles = removed,
                addedParameters = addedParameters.map(::parameterPreviewOf),
                removedParameters = removedParameters,
            )
        }
    }
    return branches
}

/**
 * A `katachiTemplate` command for [roleName] that runs as pasted: every required parameter is
 * given a value, a String one its own name with the first letter upper-cased.
 *
 * Not `<name>`: a shell reads `<` as a redirection, and the command is meant to be pasted.
 */
private fun exampleCommandOf(roleName: String, parameters: List<DeclaredTemplateParameter>): String =
    buildString {
        append("./gradlew katachiTemplate --arg roleName=").append(roleName)
        for (parameter in parameters) {
            if (parameter.default != null) continue
            val value = when (parameter.type) {
                TemplateParameterType.StringType -> parameter.name.replaceFirstChar(Char::uppercaseChar)
                else -> previewValueOf(parameter).first
            }
            append(" --arg ").append(parameter.name).append('=').append(value)
        }
    }
