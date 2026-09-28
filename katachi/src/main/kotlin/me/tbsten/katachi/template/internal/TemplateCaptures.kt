package me.tbsten.katachi.template.internal

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.KatachiTemplateParameterConflictException
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.LayoutEntryKind
import me.tbsten.katachi.dsl.Role
import me.tbsten.katachi.dsl.internal.PathCapture
import me.tbsten.katachi.dsl.internal.TemplateDeclaration
import me.tbsten.katachi.template.KatachiInvalidTemplateCaptureValueException
import me.tbsten.katachi.template.TemplateCaptureKind
import me.tbsten.katachi.template.TemplateCapturePreview

/**
 * The `--arg` name that says which role to run. Spelled once, because
 * [me.tbsten.katachi.template.GenerateCodeFromTemplate.undeclaredArgNames] has to read it out of
 * the raw arguments before anything has decoded the processor's `Args`, and a capture of that name
 * is refused.
 */
internal const val ROLE_NAME_ARG: String = "roleName"

/** The `--arg` name of `GenerateCodeFromTemplate.Args.onExisting`, which a capture may not take. */
internal const val ON_EXISTING_ARG: String = "onExisting"

/**
 * Every capture name [entries] declare, each with the first place that declares it, in
 * declaration order. [entries] are one role's, as the declarations alone flatten them.
 */
internal fun captureSitesOf(entries: List<LayoutEntry>): Map<String, DeclarationSite> {
    val sites = LinkedHashMap<String, DeclarationSite>()
    for (entry in entries) {
        for (variant in entry.captureVariants) {
            for (name in variant.names) sites.putIfAbsent(name, entry.declaredAt)
        }
    }
    return sites
}

/** The capture names of [entries], in declaration order. See [captureSitesOf]. */
internal fun captureNamesOf(entries: List<LayoutEntry>): Set<String> = captureSitesOf(entries).keys

/**
 * Refuses a capture of [role] whose name another input of its template also answers to: a
 * parameter [template] declares on any branch, or an argument of the processor.
 *
 * Any branch, not only the one [values] take: a parameter declared inside `if (withImpl) { }`
 * would otherwise be a conflict only on the runs that set `withImpl`, and a template that is fine
 * today would fail the day somebody flips the switch. [values] are replayed too, so a branch
 * reached through an Int or String value -- which no preview can enumerate -- is still seen on
 * the run that takes it. See [parameterOriginsOnEveryBranch].
 */
internal fun requireNoCaptureConflicts(
    role: Role,
    entries: List<LayoutEntry>,
    template: TemplateDeclaration,
    values: Map<String, String>,
) {
    val sites = captureSitesOf(entries)
    if (sites.isEmpty()) return
    val origins = parameterOriginsOnEveryBranch(template, role.qualifiedName, sites.keys, values)
    for ((name, site) in sites) {
        val (conflictsWith, parameterSite) = when {
            name == ROLE_NAME_ARG -> "GenerateCodeFromTemplate.Args.roleName" to null
            name == ON_EXISTING_ARG -> "GenerateCodeFromTemplate.Args.onExisting" to null
            else -> origins[name]?.let { "the template parameter declared with ${it.declaredWith}" to it.declaredAt }
                ?: continue
        }
        throw KatachiTemplateParameterConflictException(
            role = role.qualifiedName,
            name = name,
            conflictsWith = conflictsWith,
            captureDeclaredAt = site,
            parameterDeclaredAt = parameterSite,
        )
    }
}

/** Refuses a value [values] gives a capture of [entries] that cannot be one directory level. */
internal fun requireValidCaptureValues(role: Role, entries: List<LayoutEntry>, values: Map<String, String>) {
    for ((name, site) in captureSitesOf(entries)) {
        val value = values[name] ?: continue
        val problem = captureValueProblemOf(value) ?: continue
        throw KatachiInvalidTemplateCaptureValueException(
            role = role.qualifiedName,
            name = name,
            value = value,
            problem = problem,
            captureDeclaredAt = site,
        )
    }
}

private fun captureValueProblemOf(value: String): KatachiInvalidTemplateCaptureValueException.Problem? = when {
    value.isEmpty() -> KatachiInvalidTemplateCaptureValueException.Problem.Empty
    value == "." || value == ".." -> KatachiInvalidTemplateCaptureValueException.Problem.DotSegment
    value.any { it == '/' || it == '\\' } -> KatachiInvalidTemplateCaptureValueException.Problem.Separator
    value.any { it in UNCREATABLE_CHARACTERS || it < ' ' } ->
        KatachiInvalidTemplateCaptureValueException.Problem.UncreatableCharacter

    else -> null
}

/** [path] with each of [captures] replaced by its value in [values]; the caller made sure every value is there. */
internal fun fillCaptures(path: String, captures: List<PathCapture>, values: Map<String, String>): String {
    if (captures.isEmpty()) return path
    val segments = path.split('/').toMutableList()
    for (capture in captures) {
        val value = values[capture.name] ?: continue
        if (capture.segmentIndex in segments.indices) segments[capture.segmentIndex] = value
    }
    return segments.joinToString("/")
}

/**
 * The captures of one role as `DescribeTemplates` lists them: one per module wildcard and one
 * per file pattern a directory capture sits in -- the places a template can generate into -- in
 * declaration order.
 */
internal fun capturePreviewsOf(entries: List<LayoutEntry>): List<TemplateCapturePreview> {
    // Keyed by what a preview says rather than by the preview: the public class declares no `equals`.
    val previews = LinkedHashMap<List<Any>, TemplateCapturePreview>()
    fun add(preview: TemplateCapturePreview) {
        previews.putIfAbsent(listOf(preview.name, preview.kind, preview.pattern, preview.position), preview)
    }
    for (entry in entries) {
        if (entry.kind != LayoutEntryKind.File || entry.synthetic) continue
        for (variant in entry.captureVariants) {
            variant.moduleCapture?.let { module ->
                module.names.forEachIndexed { index, name ->
                    add(TemplateCapturePreview(name, TemplateCaptureKind.ModuleCapture, module.modulePattern, index))
                }
            }
            for (capture in variant.pathCaptures) {
                add(TemplateCapturePreview(capture.name, TemplateCaptureKind.PathCapture, entry.path, capture.segmentIndex))
            }
        }
    }
    return previews.values.toList()
}
