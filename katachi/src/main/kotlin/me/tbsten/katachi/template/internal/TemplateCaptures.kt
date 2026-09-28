package me.tbsten.katachi.template.internal

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.KatachiTemplateParameterConflictException
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.LayoutEntryKind
import me.tbsten.katachi.dsl.Role
import me.tbsten.katachi.dsl.internal.LayoutCaptures
import me.tbsten.katachi.dsl.internal.ModulePattern
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

/**
 * Refuses a value [values] gives a capture of [entries] that cannot be one directory level -- or,
 * for a module capture, one level of the module path.
 */
internal fun requireValidCaptureValues(role: Role, entries: List<LayoutEntry>, values: Map<String, String>) {
    val moduleCaptureNames = moduleCaptureNamesOf(entries)
    for ((name, site) in captureSitesOf(entries)) {
        val value = values[name] ?: continue
        val problem = captureValueProblemOf(value) ?: continue
        throw KatachiInvalidTemplateCaptureValueException(
            role = role.qualifiedName,
            name = name,
            value = value,
            problem = problem,
            captureDeclaredAt = site,
            isModuleCapture = name in moduleCaptureNames,
        )
    }
}

/** The names [entries] give the `*`s of a module key, as opposed to a `capture("...")` level. */
internal fun moduleCaptureNamesOf(entries: List<LayoutEntry>): Set<String> =
    entries.flatMapTo(LinkedHashSet()) { entry -> entry.captureVariants.flatMap { it.moduleCapture?.names.orEmpty() } }

/**
 * The device names Windows reserves in every directory, whatever the extension: `CON` and
 * `con.txt` alike open the console rather than a file.
 */
private val WINDOWS_RESERVED_NAMES: Set<String> =
    setOf("CON", "PRN", "AUX", "NUL") + (1..9).flatMap { listOf("COM$it", "LPT$it") }

private fun captureValueProblemOf(value: String): KatachiInvalidTemplateCaptureValueException.Problem? = when {
    value.isEmpty() -> KatachiInvalidTemplateCaptureValueException.Problem.Empty
    value.isBlank() -> KatachiInvalidTemplateCaptureValueException.Problem.Blank
    value == "." || value == ".." -> KatachiInvalidTemplateCaptureValueException.Problem.DotSegment
    value.any { it == '/' || it == '\\' } -> KatachiInvalidTemplateCaptureValueException.Problem.Separator
    value.any(::isUncreatableInName) -> KatachiInvalidTemplateCaptureValueException.Problem.UncreatableCharacter
    value.first().isWhitespace() || value.last().isWhitespace() ->
        KatachiInvalidTemplateCaptureValueException.Problem.SurroundingWhitespace
    value.last() == '.' -> KatachiInvalidTemplateCaptureValueException.Problem.TrailingDot
    value.substringBefore('.').uppercase() in WINDOWS_RESERVED_NAMES ->
        KatachiInvalidTemplateCaptureValueException.Problem.ReservedName

    else -> null
}

/**
 * Whether [character] may not be part of a directory name katachi creates: one of
 * [UNCREATABLE_CHARACTERS], or a character that is not printed as itself on one line -- a control
 * character (DEL and the C1 range included) or a line or paragraph separator such as U+2028.
 */
internal fun isUncreatableInName(character: Char): Boolean =
    character in UNCREATABLE_CHARACTERS ||
        Character.isISOControl(character) ||
        Character.getType(character).let {
            it == Character.LINE_SEPARATOR.toInt() || it == Character.PARAGRAPH_SEPARATOR.toInt()
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
 * [path] with every capture of [variant] that [values] gives a value filled in: its
 * `capture("...")` levels, and the module key's `*`s when [path] still starts with the key itself.
 *
 * The second only happens to an entry flattened without the modules -- the declarations alone, as
 * a preview or a message reads them. There the key is kept as its conventional directory -- a
 * `feature` directory with a `*` level below it, for `":feature:*"` -- and filling it in the same
 * way gives the directory the value would pick. An entry flattened against a module the values
 * picked already starts with that module's directory and is left as it is.
 */
internal fun fillCapturedPath(path: String, variant: LayoutCaptures, values: Map<String, String>): String {
    val filled = fillCaptures(path, variant.pathCaptures, values)
    val module = variant.moduleCapture ?: return filled
    val moduleValues = module.names.map { values[it] ?: return filled }
    val prefix = conventionalDirectoryOf(module.modulePattern)
    if (filled != prefix && !filled.startsWith("$prefix/")) return filled
    val pattern = ModulePattern.compile(module.modulePattern)
    return conventionalDirectoryOf(pattern.filledIn(moduleValues)) + filled.removePrefix(prefix)
}

/** [modulePattern] as [ModulePattern.conventionalDirectory] spells it, without compiling it again. */
private fun conventionalDirectoryOf(modulePattern: String): String =
    modulePattern.removePrefix(":").replace(':', '/')

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
