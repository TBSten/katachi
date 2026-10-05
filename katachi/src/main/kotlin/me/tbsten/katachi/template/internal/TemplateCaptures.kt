package me.tbsten.katachi.template.internal

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.KatachiTemplateParameterConflictException
import me.tbsten.katachi.dsl.KatachiTemplateParameterTypeConflictException
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.LayoutEntryKind
import me.tbsten.katachi.dsl.Role
import me.tbsten.katachi.dsl.internal.LayoutCaptures
import me.tbsten.katachi.dsl.internal.ModulePattern
import me.tbsten.katachi.dsl.internal.PathCapture
import me.tbsten.katachi.dsl.internal.TemplateParameterOrigin
import me.tbsten.katachi.dsl.internal.fillCapturedSegments
import me.tbsten.katachi.dsl.internal.placeholder
import me.tbsten.katachi.template.KatachiInvalidTemplateCaptureValueException
import me.tbsten.katachi.template.TemplateCaptureKind
import me.tbsten.katachi.template.TemplateCapturePreview

/**
 * Every capture name [entries] declare, each with the first place that declares it, in
 * declaration order. [entries] are one template's own -- [DeclaredTemplate.entries], usually one.
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
 * Refuses a run whose values would give a capture the same name as another input of the chosen
 * templates: a parameter any of them declares on any branch, or an argument of the processor
 * itself ([TEMPLATE_ARG] / [ON_EXISTING_ARG]).
 *
 * Checked across every template of the run at once, not one at a time: two different templates
 * sharing a capture name is fine and by design -- `--arg name=User` fills both -- but a template
 * that declares a *parameter* under a name another of the run's templates already uses as a
 * capture, or as a parameter of a different kind, is not: both would read `--arg name=...`, and
 * nothing would say which reading was meant. Any branch of a template's parameters is walked, not
 * only the one [values] take -- see [parameterOriginsOnEveryBranch].
 */
internal fun requireNoConflicts(templates: List<DeclaredTemplate>, values: Map<String, String>) {
    val captureSites = LinkedHashMap<String, DeclarationSite>()
    val ownerOf = LinkedHashMap<String, DeclaredTemplate>()
    for (template in templates) {
        for ((name, site) in captureSitesOf(template.entries)) {
            captureSites.putIfAbsent(name, site)
            ownerOf.putIfAbsent(name, template)
        }
    }

    val paramOrigins = LinkedHashMap<String, Pair<DeclaredTemplate, TemplateParameterOrigin>>()
    for (template in templates) {
        val names = captureSitesOf(template.entries).keys
        val origins = parameterOriginsOnEveryBranch(template.template, template.role.qualifiedName, names, values)
        for ((name, origin) in origins) {
            val existing = paramOrigins[name]
            // Type (label -- declaredWith alone reads "enumParameter()" for every enum, so two
            // different enum types would slip past it) or default differs: design draft section
            // 2, "複数指定のとき" -- "型か既定値が食い違ったら落とす".
            if (existing != null && (existing.second.label != origin.label || existing.second.default != origin.default)) {
                val (firstTemplate, firstOrigin) = existing
                throw KatachiTemplateParameterTypeConflictException(
                    name = name,
                    template = template.specifier,
                    declaredAt = origin.declaredAt,
                    label = origin.label,
                    default = origin.default?.let(::argSpellingOf),
                    conflictsWithTemplate = firstTemplate.specifier,
                    conflictsWithDeclaredAt = firstOrigin.declaredAt,
                    conflictsWithLabel = firstOrigin.label,
                    conflictsWithDefault = firstOrigin.default?.let(::argSpellingOf),
                )
            }
            paramOrigins.putIfAbsent(name, template to origin)
        }
    }

    for ((name, site) in captureSites) {
        val owner = ownerOf.getValue(name)
        val (conflictsWith, parameterSite) = when {
            name == TEMPLATE_ARG -> "GenerateCodeFromTemplate.Args.template" to null
            name == ON_EXISTING_ARG -> "GenerateCodeFromTemplate.Args.onExisting" to null
            else -> paramOrigins[name]?.let { (paramTemplate, origin) ->
                val where = if (paramTemplate === owner) "" else " in template \"${paramTemplate.specifier}\""
                "the template parameter declared with ${origin.declaredWith}$where" to origin.declaredAt
            } ?: continue
        }
        throw KatachiTemplateParameterConflictException(
            role = owner.role.qualifiedName,
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
 *
 * Checks the value alone, exactly as it was passed. A capture that only fills part of a segment
 * (`"${capture("x")}."`) can still turn a fine value into a segment the file system refuses --
 * see [requireValidFilledSegments], which checks the other half.
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

/**
 * Refuses a run whose values, once filled into [entry]'s path, would land a directory-level
 * capture ([LayoutCaptures.pathCaptures]) in a segment the file system refuses -- checked on the
 * *whole* segment, not the value alone, since a partial match (`"${capture("x")}."`) can combine a
 * fine value with the pattern's own literal text into one that is not. A module capture is not
 * checked here: its value picks an existing module, whose directory is not built out of it.
 *
 * Called once every capture [entry] needs has a value, so every segment it names can be filled.
 */
internal fun requireValidFilledSegments(
    role: Role,
    entry: LayoutEntry,
    variant: LayoutCaptures,
    values: Map<String, String>,
) {
    if (variant.pathCaptures.isEmpty()) return
    val segments = fillCapturedSegments(entry.path, variant.pathCaptures) { filledValueOf(it, values) }
    val sites = captureSitesOf(listOf(entry))
    for ((segmentIndex, captures) in variant.pathCaptures.groupBy { it.segmentIndex }) {
        val segment = segments.getOrNull(segmentIndex) ?: continue
        val problem = filledSegmentProblemOf(segment) ?: continue
        val capture = captures.last()
        throw KatachiInvalidTemplateCaptureValueException(
            role = role.qualifiedName,
            name = capture.name,
            value = filledValueOf(capture, values) ?: values.getValue(capture.name),
            problem = problem,
            segment = segment,
            captureDeclaredAt = sites.getValue(capture.name),
            isModuleCapture = false,
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
 * The same problems [captureValueProblemOf] finds in a value on its own, checked instead on a
 * whole filled-in segment: not [KatachiInvalidTemplateCaptureValueException.Problem.Empty] (an
 * empty value is already refused on its own) and not
 * [KatachiInvalidTemplateCaptureValueException.Problem.Separator] or
 * [KatachiInvalidTemplateCaptureValueException.Problem.UncreatableCharacter] (the layout's own
 * literal text is trusted, and a value cannot introduce either -- see [captureValueProblemOf]).
 */
private fun filledSegmentProblemOf(segment: String): KatachiInvalidTemplateCaptureValueException.Problem? = when {
    segment.isBlank() -> KatachiInvalidTemplateCaptureValueException.Problem.Blank
    segment == "." || segment == ".." -> KatachiInvalidTemplateCaptureValueException.Problem.DotSegment
    segment.first().isWhitespace() || segment.last().isWhitespace() ->
        KatachiInvalidTemplateCaptureValueException.Problem.SurroundingWhitespace
    segment.last() == '.' -> KatachiInvalidTemplateCaptureValueException.Problem.TrailingDot
    segment.substringBefore('.').uppercase() in WINDOWS_RESERVED_NAMES ->
        KatachiInvalidTemplateCaptureValueException.Problem.ReservedName

    else -> null
}

/**
 * Characters a generated file or directory name may not hold.
 *
 * `*`, `?`, `[`, `]`, `{` and `}` are katachi's glob metacharacters or the ones it rejects, so a
 * name holding them would be compared against the layout as a literal and then created on disk as
 * a file nothing can name back. The rest are what Windows refuses outright; katachi's own check
 * has to give the same answer on every platform, so they are refused everywhere.
 */
internal const val UNCREATABLE_CHARACTERS: String = "*?[]{}:\"<>|"

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

/**
 * What [capture] reads as in a generated path, out of a run's [values]: the value of its name,
 * converted for a derived occurrence. `null` when the run gave the name no value.
 */
internal fun filledValueOf(capture: PathCapture, values: Map<String, String>): String? =
    values[capture.name]?.let { value -> capture.transform?.apply?.invoke(value) ?: value }

/** [PathCapture.placeholder], as a function reference for the fill calls here. */
internal fun placeholderOf(capture: PathCapture): String = capture.placeholder()

/**
 * The captures of one template as `DescribeTemplates` lists them: one per module wildcard and one
 * per file pattern a directory capture sits in -- the places this template can generate into --
 * in declaration order. [entries] is one template's own ([DeclaredTemplate.entries]).
 */
internal fun capturePreviewsOf(entries: List<LayoutEntry>): List<TemplateCapturePreview> {
    // Keyed by what a preview says rather than by the preview: the public class declares no `equals`.
    val previews = LinkedHashMap<List<Any?>, TemplateCapturePreview>()
    fun add(preview: TemplateCapturePreview) {
        val key = listOf(preview.name, preview.kind, preview.pattern, preview.position, preview.wildcardIndex, preview.transform)
        previews.putIfAbsent(key, preview)
    }
    for (entry in entries) {
        if (entry.kind != LayoutEntryKind.File || entry.synthetic) continue
        for (variant in entry.captureVariants) {
            variant.moduleCapture?.let { module ->
                val segment = ModulePattern.compile(module.modulePattern, DeclarationSite.Unknown)
                    .filledIn(module.names.map(::placeholderOf))
                module.names.forEachIndexed { index, name ->
                    add(TemplateCapturePreview(name, TemplateCaptureKind.ModuleCapture, module.modulePattern, index, segment))
                }
            }
            for (capture in variant.pathCaptures) {
                add(
                    TemplateCapturePreview(
                        name = capture.name,
                        kind = TemplateCaptureKind.PathCapture,
                        pattern = entry.path,
                        position = capture.segmentIndex,
                        segment = segmentPatternOf(entry, variant, capture.segmentIndex),
                        wildcardIndex = capture.wildcardIndex,
                        transform = capture.transform?.spelling,
                    ),
                )
            }
        }
    }
    return previews.values.toList()
}

/**
 * The pattern of one segment of [entry]'s path, every capture shown as its placeholder:
 * `${fileName}Screen.kt` for the file-name level of a partial match, a whole `${name}` for a
 * level one capture fills entirely, `<feature.pascalCase>Screen.kt` for a derived one.
 */
private fun segmentPatternOf(entry: LayoutEntry, variant: LayoutCaptures, segmentIndex: Int): String {
    val onSegment = variant.pathCaptures.filter { it.segmentIndex == segmentIndex }
    return fillCapturedSegments(entry.path, onSegment) { placeholderOf(it) }.getOrNull(segmentIndex)
        ?: entry.path.split('/').getOrNull(segmentIndex).orEmpty()
}

/** `${name}`: what a capture or a String parameter reads as in a preview. */
internal fun placeholderOf(name: String): String = "\$" + "{" + name + "}"

/** [value] as `--arg` spells it: an enum entry by its name, anything else by `toString`. */
internal fun argSpellingOf(value: Any): String = when (value) {
    is Enum<*> -> value.name
    else -> value.toString()
}
