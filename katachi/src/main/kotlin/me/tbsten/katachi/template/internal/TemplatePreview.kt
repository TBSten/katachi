package me.tbsten.katachi.template.internal

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.Role
import me.tbsten.katachi.dsl.Summary
import me.tbsten.katachi.dsl.Title
import me.tbsten.katachi.dsl.internal.DeclaredTemplateParameter
import me.tbsten.katachi.dsl.internal.TemplateDeclaration
import me.tbsten.katachi.dsl.internal.TemplateEvaluation
import me.tbsten.katachi.dsl.internal.TemplateParameterOrigin
import me.tbsten.katachi.dsl.internal.TemplateParameterType
import me.tbsten.katachi.dsl.internal.declaredTemplateParameters
import me.tbsten.katachi.dsl.internal.evaluateTemplate
import me.tbsten.katachi.dsl.internal.templateParameterNames
import me.tbsten.katachi.internal.catching
import me.tbsten.katachi.template.KatachiMissingTemplateCaptureException
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

/**
 * [role]'s template as one line of the list. [entries] are the declared layout, which the
 * captures are read from.
 *
 * Never fails over one template: a capture and a parameter sharing a name leave [TemplateSummary.fileCount]
 * `null`, as any other template that cannot be previewed does, so that one broken role does not
 * cost the list -- or the IDE, which reads the same list -- every other template.
 * [templateDetailOf] and a run of the template say what is wrong.
 */
internal fun templateSummaryOf(role: Role, entries: List<LayoutEntry>): TemplateSummary {
    val template = templateOf(role)
    val roleEntries = entries.filter { it.role === role }
    val captureNames = captureNamesOf(roleEntries)
    val preview = previewValuesOf(template, role.qualifiedName, captureNames)
    val fileCount = catching {
        requireNoCaptureConflicts(role, roleEntries, template, preview.values)
        evaluateTemplate(template, role.qualifiedName, preview.values, captureNames)
    }.getOrNull()?.files?.size
    return TemplateSummary(
        roleName = role.qualifiedName,
        title = role[Title],
        summary = role[Summary],
        parameterNames = preview.parameters.map { it.name },
        fileCount = fileCount,
        captures = capturePreviewsOf(roleEntries),
    )
}

/**
 * [role]'s template explained: its parameters, and its files rendered with stand-in values.
 *
 * Reads nothing off disk: the paths come from [entries], the declared layout.
 */
internal fun templateDetailOf(role: Role, entries: List<LayoutEntry>): TemplateDetail {
    val template = templateOf(role)
    val roleEntries = entries.filter { it.role === role }
    val captureNames = captureNamesOf(roleEntries)
    val preview = previewValuesOf(template, role.qualifiedName, captureNames)
    requireNoCaptureConflicts(role, roleEntries, template, preview.values)
    val evaluation = evaluateTemplate(template, role.qualifiedName, preview.values, captureNames)
    val stringNames = preview.parameters
        .filter { it.type == TemplateParameterType.StringType }
        .map { it.name } + captureNames

    return TemplateDetail(
        roleName = role.qualifiedName,
        title = role[Title],
        summary = role[Summary],
        parameters = preview.parameters.map(::parameterPreviewOf),
        files = evaluation.files.map { file ->
            filePreviewOf(role, roleEntries, stringNames, captureNames, file.fileName, file.content, file.declaredAt)
        },
        branches = branchesOf(template, role.qualifiedName, captureNames, preview, evaluation),
        exampleCommand = exampleCommandOf(role.qualifiedName, captureNames, preview.parameters),
        captures = capturePreviewsOf(roleEntries),
    )
}

/**
 * The values to preview [template] with, replayed until the parameters they declare settle.
 *
 * [fixed] wins over the stand-in of the parameter it names, which is how a branch is explored.
 * Every capture in [captureNames] reads as its placeholder, `${name}`, as a String parameter does.
 */
private fun previewValuesOf(
    template: TemplateDeclaration,
    roleName: String,
    captureNames: Set<String>,
    fixed: Map<String, String> = emptyMap(),
): PreviewValues {
    val base = captureNames.associateWith(::placeholderOf) + fixed
    var values = base
    var parameters = declaredTemplateParameters(template, roleName, values, captureNames)
    repeat(MAX_REPLAYS) {
        val next = LinkedHashMap(base)
        for (parameter in parameters) {
            if (parameter.name !in next) next[parameter.name] = previewValueOf(parameter).first
        }
        if (next == values) return PreviewValues(parameters, values)
        values = next
        parameters = declaredTemplateParameters(template, roleName, values, captureNames)
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
    template: TemplateDeclaration,
    roleName: String,
    captureNames: Set<String>,
    values: Map<String, String>,
): Map<String, TemplateParameterOrigin> {
    val origins = LinkedHashMap<String, TemplateParameterOrigin>()
    fun collect(replayed: Map<String, String>) {
        for ((name, origin) in templateParameterNames(template, roleName, replayed, captureNames).origins) {
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
 * directory it answers is put in front of the name as the preview spells it. A directory
 * capture is filled in the same way and spelt back as `${name}`; a module capture is left
 * unfilled, since only the modules that exist could say which one a value picks.
 */
private fun filePreviewOf(
    role: Role,
    entries: List<LayoutEntry>,
    stringNames: List<String>,
    captureNames: Set<String>,
    fileName: String,
    content: String,
    declaredAt: DeclarationSite,
): TemplateFilePreview {
    var plainName = fileName
    for (name in stringNames) {
        plainName = plainName.replace(placeholderOf(name), name.replaceFirstChar(Char::uppercaseChar))
    }
    val moduleCaptureNames = entries.flatMap { entry -> entry.captureVariants.flatMap { it.moduleCapture?.names.orEmpty() } }.toSet()
    val tokens = (captureNames - moduleCaptureNames).withIndex().associate { (index, name) -> name to "$CAPTURE_TOKEN$index" }
    return try {
        var placed = placeTemplateFile(role, PlacementLayout(entries), plainName, declaredAt, tokens)
        for ((name, token) in tokens) placed = placed.replace(token, placeholderOf(name))
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
    } catch (unfilled: KatachiMissingTemplateCaptureException) {
        TemplateFilePreview(
            fileName = fileName,
            path = null,
            unresolvedPatterns = unfilled.missing.keys.sorted(),
            content = content,
        )
    }
}

/**
 * What a directory capture is spelt as while a preview asks the layout about it: a plain word no
 * glob reads as anything, numbered per capture, and swapped back for `${name}` afterwards.
 */
private const val CAPTURE_TOKEN: String = "katachiCapturePreview"

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
    captureNames: Set<String>,
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
            val variant = previewValuesOf(template, roleName, captureNames, fixed = mapOf(parameter.name to value))
            val files = catching { evaluateTemplate(template, roleName, variant.values, captureNames) }
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
 * A `katachiTemplate` command for [roleName] that runs as pasted: every capture and every required
 * parameter is given a value -- a capture its own name, as a directory is usually spelt, and a
 * String parameter its own name with the first letter upper-cased.
 *
 * Not `<name>`: a shell reads `<` as a redirection, and the command is meant to be pasted.
 */
private fun exampleCommandOf(
    roleName: String,
    captureNames: Set<String>,
    parameters: List<DeclaredTemplateParameter>,
): String =
    buildString {
        append("./gradlew katachiTemplate --arg roleName=").append(roleName)
        for (name in captureNames) append(" --arg ").append(name).append('=').append(name)
        for (parameter in parameters) {
            if (parameter.default != null) continue
            val value = when (parameter.type) {
                TemplateParameterType.StringType -> parameter.name.replaceFirstChar(Char::uppercaseChar)
                else -> previewValueOf(parameter).first
            }
            append(" --arg ").append(parameter.name).append('=').append(value)
        }
    }
